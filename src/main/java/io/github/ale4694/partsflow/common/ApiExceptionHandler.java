package io.github.ale4694.partsflow.common;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@link ProblemDetail} response. The "detail" text is shown to the user by the
 * (Italian) web interface, so it is written in Italian; logs and code stay in English.
 * The base class already covers Spring MVC errors (bad JSON, validation, unsupported media type...); their
 * English texts are replaced in {@link #handleExceptionInternal}.
 * Feature-specific exceptions get their own handlers as the features are added.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private static final String SORT_MESSAGE = "Proprietà di ordinamento sconosciuta: ";

	// Spring Data message: Sort expression '["string"]: ASC' must only contain property references...
	private static final Pattern BAD_SORT_MESSAGE = Pattern.compile("^Sort expression '(.*): (?:ASC|DESC)'");

	/** Names of the request fields, as the user knows them (used in the "invalid data" message). */
	private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
			Map.entry("code", "codice"),
			Map.entry("description", "descrizione"),
			Map.entry("unit", "unità di misura"),
			Map.entry("reorderThreshold", "soglia di riordino"),
			Map.entry("name", "ragione sociale"),
			Map.entry("vatNumber", "partita IVA"),
			Map.entry("supplierCode", "codice del fornitore"),
			Map.entry("itemId", "articolo"),
			Map.entry("type", "tipo"),
			Map.entry("quantity", "quantità"),
			Map.entry("reason", "motivo"),
			Map.entry("sourceDocument", "documento di riferimento"),
			Map.entry("question", "domanda"));

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	/** Someone else changed the same record first (operations without an automatic retry). */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Il record è stato modificato da qualcun altro: ricaricalo e riprova");
	}

	/** e.g. ?sort=doesNotExist */
	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, SORT_MESSAGE + ex.getPropertyName());
	}

	/**
	 * A sort value that is not even a property path, e.g. the ["string"] that Swagger UI sends by default.
	 * Spring Data reports it as InvalidDataAccessApiUsageException instead of PropertyReferenceException.
	 * Any other InvalidDataAccessApiUsageException is a bug of ours and stays a 500.
	 */
	@ExceptionHandler(InvalidDataAccessApiUsageException.class)
	ProblemDetail handleInvalidDataAccessUsage(InvalidDataAccessApiUsageException ex) {
		Matcher badSort = BAD_SORT_MESSAGE.matcher(String.valueOf(ex.getMessage()));
		if (badSort.find()) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, SORT_MESSAGE + badSort.group(1));
		}
		return handleUnexpected(ex);
	}

	/** Safety net for unique/foreign key violations that slip past the explicit checks (e.g. concurrent requests). */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"L'operazione è in conflitto con dati esistenti (valore duplicato o record ancora in uso)");
	}

	/** Last resort: log the details, but never leak them to the client. */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Errore imprevisto del server");
	}

	/**
	 * Every error that Spring MVC itself turns into a ProblemDetail (invalid JSON, validation, missing file,
	 * unsupported media type...) passes through here: replace its English text with an Italian one.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			problem.setDetail(italianDetail(ex, statusCode, problem.getDetail()));
		}
		return response;
	}

	private String italianDetail(Exception ex, HttpStatusCode status, String original) {
		if (ex instanceof MethodArgumentNotValidException invalid) {
			String fields = invalid.getBindingResult().getFieldErrors().stream()
					.map(error -> FIELD_LABELS.getOrDefault(error.getField(), error.getField()))
					.distinct()
					.collect(Collectors.joining(", "));
			return fields.isEmpty() ? "Dati non validi" : "Dati non validi o mancanti: " + fields;
		}
		return switch (status.value()) {
			case 400 -> "Richiesta non valida";
			case 404 -> "Risorsa non trovata";
			case 405 -> "Metodo non consentito";
			case 406 -> "Formato di risposta non disponibile";
			case 415 -> "Tipo di contenuto non supportato";
			default -> original;
		};
	}
}

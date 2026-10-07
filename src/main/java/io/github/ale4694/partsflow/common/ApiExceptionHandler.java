package io.github.ale4694.partsflow.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@link ProblemDetail} response.
 * The base class already covers Spring MVC errors (bad JSON, validation, unsupported media type...).
 * Feature-specific exceptions get their own handlers as the features are added.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
				"The record was modified by someone else, reload it and try again");
	}

	/** e.g. ?sort=doesNotExist */
	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Unknown sort property: " + ex.getPropertyName());
	}

	/** Safety net for unique/foreign key violations that slip past the explicit checks (e.g. concurrent requests). */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The operation conflicts with existing data (duplicate value or the record is still referenced)");
	}

	/** Last resort: log the details, but never leak them to the client. */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
	}
}

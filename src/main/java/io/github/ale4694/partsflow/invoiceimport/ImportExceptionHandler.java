package io.github.ale4694.partsflow.invoiceimport;

import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.MalformedDocumentException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error mapping owned by this feature. It runs before the shared handler in {@code common}
 * (which has a catch-all for unexpected errors), so it needs the higher precedence.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ImportExceptionHandler {

	/** The file cannot be read as a document: the client sent something wrong. */
	@ExceptionHandler(MalformedDocumentException.class)
	ProblemDetail handleMalformed(MalformedDocumentException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/** The file is readable but a business rule rejects it. */
	@ExceptionHandler(InvalidDocumentException.class)
	ProblemDetail handleInvalid(InvalidDocumentException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}
}

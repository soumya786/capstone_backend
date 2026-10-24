package com.capstone.Capstone.project.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.capstone.Capstone.project.model.ApiError;

import jakarta.validation.ConstraintViolationException;

/**
 * Maps every failure to the spec's {@code Error} schema. Clients only ever see a generic
 * message; details are logged server-side.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiError> notFound(NotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ApiError> conflict(ConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", ex.getMessage()));
	}

	@ExceptionHandler(BadRequestException.class)
	ResponseEntity<ApiError> badRequest(BadRequestException ex) {
		return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", ex.getMessage()));
	}

	@ExceptionHandler({ ConstraintViolationException.class, IllegalArgumentException.class })
	ResponseEntity<ApiError> invalid(Exception ex) {
		log.debug("Rejected request", ex);
		return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "Invalid request"));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(Exception ex) {
		log.error("Unhandled error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiError("INTERNAL_ERROR", "Something went wrong"));
	}

	/** Covers Spring MVC's own exceptions (validation, missing header, bad JSON, 404, 405 ...). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("Request failed", ex);
		} else {
			log.debug("Rejected request", ex);
		}
		HttpStatus resolved = HttpStatus.resolve(status.value());
		String code = resolved == null ? "ERROR" : resolved.name();
		String message = status.is4xxClientError() ? (status.value() == 404 ? "Resource not found" : "Invalid request")
				: "Something went wrong";
		return ResponseEntity.status(status).headers(headers).body(new ApiError(code, message));
	}
}

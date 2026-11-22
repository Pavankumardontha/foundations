package com.zenith.events;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onValidationFailure(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError body = new ApiError(
                UUID.randomUUID().toString(),
                400,
                "validation_failed",
                "Request validation failed.",
                fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> onDomainException(DomainException ex) {
        // 409: the request clashes with the current state (someone got there first).
        // 422: the request is understood but breaks a business rule.
        HttpStatus status = (ex instanceof SeatAlreadyReservedException)
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;
        ApiError body = new ApiError(
                UUID.randomUUID().toString(),
                status.value(),
                ex.code(),
                ex.getMessage(),
                List.of());
        return ResponseEntity.status(status).body(body);
    }

    // The 400-versus-422 rule, applied: a request defective AS A DOCUMENT is 400.
    // Unreadable body — broken JSON, wrong types inside the JSON.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> onUnreadableBody(HttpMessageNotReadableException ex) {
        ApiError body = new ApiError(
                UUID.randomUUID().toString(),
                400,
                "malformed_request",
                "Request body is not readable JSON.",
                List.of());
        return ResponseEntity.badRequest().body(body);
    }

    // A path variable or query parameter whose text cannot become the declared type.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ApiError body = new ApiError(
                UUID.randomUUID().toString(),
                400,
                "invalid_parameter",
                "A parameter has an invalid value.",
                List.of(new ApiError.FieldError(ex.getName(), "invalid value")));
        return ResponseEntity.badRequest().body(body);
    }

    // The body's label (Content-Type) names a format we do not accept.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> onUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        ApiError body = new ApiError(
                UUID.randomUUID().toString(),
                415,
                "unsupported_media_type",
                "Content-Type must be application/json.",
                List.of());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    // Boot 3.2+ turns "no such path" into an exception. Without this mapping, the
    // catch-all would answer 404s with a fake 500 and page the on-call for typos.
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> onNoResource(NoResourceFoundException ex) {
        ApiError body = new ApiError(
                UUID.randomUUID().toString(),
                404,
                "not_found",
                "No resource at this path.",
                List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> onUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        // The stack trace is for us: full detail, ERROR level, tagged with the errorId.
        log.error("Unhandled exception, errorId={}", errorId, ex);
        // The identifier is for the caller: nothing else leaves the building.
        ApiError body = new ApiError(
                errorId,
                500,
                "internal_error",
                "Something went wrong on our side. Quote the errorId when contacting support.",
                List.of());
        return ResponseEntity.internalServerError().body(body);
    }
}

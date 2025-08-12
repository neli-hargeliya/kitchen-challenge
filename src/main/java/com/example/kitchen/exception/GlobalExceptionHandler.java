package com.example.kitchen.exception;

import com.example.kitchen.model.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationWebFlux(WebExchangeBindException ex, ServerWebExchange exchange) {
        String msg = ex.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        log.debug("Validation error (WebFlux): {}", msg, ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, msg, exchange.getRequest().getPath().value());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationMvc(MethodArgumentNotValidException ex, ServletWebRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        if (msg.isBlank()) {
            msg = ex.getBindingResult().getAllErrors().stream()
                    .findFirst()
                    .map(err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "Validation failed")
                    .orElse("Validation failed");
        }

        log.debug("Validation error (MVC): {}", msg, ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, msg, request.getRequest().getRequestURI());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, ServletWebRequest request) {
        String message = safeMessage(ex);
        log.debug("Bad request: {}", message, ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequest().getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalError(Exception ex, ServletWebRequest request) {
        String message = safeMessage(ex);
        log.error("Unhandled error: {}", message, ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, request.getRequest().getRequestURI());
    }

    private String formatFieldError(FieldError e) {
        String reason = (e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid");
        return e.getField() + ": " + reason;
    }

    private String safeMessage(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, String path) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message != null ? message : "",
                path
        );
        return ResponseEntity.status(status).body(body);
    }
}

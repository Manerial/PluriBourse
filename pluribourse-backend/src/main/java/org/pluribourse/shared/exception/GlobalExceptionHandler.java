package org.pluribourse.shared.exception;

import jakarta.servlet.http.*;
import jakarta.validation.*;
import org.jspecify.annotations.*;
import org.springframework.http.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.*;

import java.net.*;

/**
 * Global exception handler returning RFC 7807 Problem Details for all errors.
 * Extends {@link ResponseEntityExceptionHandler} to inherit Spring MVC default handlers.
 */
@NullMarked
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setType(URI.create("https://pluribourse/errors/" + ex.getErrorCode()));
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(ex.getStatus()).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create("https://pluribourse/errors/validation-failed"));
        String uri = request instanceof ServletWebRequest swr
                ? swr.getRequest().getRequestURI()
                : "unknown";
        pd.setInstance(URI.create(uri));
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        pd.setType(URI.create("https://pluribourse/errors/internal-error"));
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.internalServerError().body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ex.getMessage() != null ? ex.getMessage() : "Constraint violation");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, detail);
        pd.setType(URI.create("https://pluribourse/errors/validation-failed"));
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.unprocessableContent().body(pd);
    }
}

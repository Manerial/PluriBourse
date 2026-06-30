package org.pluribourse.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Runtime exception for business rule violations.
 * Carries an HTTP status and a short error code used in the RFC 7807 {@code type} URI.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}

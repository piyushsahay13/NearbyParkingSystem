package com.wego.parkingsystem.exception;

import lombok.Getter;

/**
 * Abstract base exception for all application-specific exceptions.
 * Carries an {@link ErrorCode} that maps to an HTTP status and a
 * structured error payload for consistent RFC 7807-style responses.
 */
@Getter
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String userMessage;

    protected ApplicationException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.userMessage = errorCode.getDefaultMessage();
    }

    protected ApplicationException(ErrorCode errorCode, String userMessage) {
        super(userMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    protected ApplicationException(ErrorCode errorCode, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}

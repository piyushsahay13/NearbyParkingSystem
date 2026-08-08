package com.wego.parkingsystem.exception;

/** HTTP 400 — Request parameter or body validation failed. */
public class ValidationException extends ApplicationException {

    public ValidationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ValidationException(ErrorCode errorCode, String userMessage) {
        super(errorCode, userMessage);
    }
}

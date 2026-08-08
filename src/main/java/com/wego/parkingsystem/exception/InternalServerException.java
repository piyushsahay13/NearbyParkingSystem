package com.wego.parkingsystem.exception;

/** HTTP 500 — Catch-all for unhandled or unexpected system-level errors. */
public class InternalServerException extends ApplicationException {

    public InternalServerException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InternalServerException(ErrorCode errorCode, String userMessage) {
        super(errorCode, userMessage);
    }

    public InternalServerException(ErrorCode errorCode, String userMessage, Throwable cause) {
        super(errorCode, userMessage, cause);
    }
}

package com.wego.parkingsystem.exception;

/** HTTP 500 — Unrecoverable database error (connection, timeout, constraint). */
public class DatabaseException extends ApplicationException {

    public DatabaseException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DatabaseException(ErrorCode errorCode, String userMessage) {
        super(errorCode, userMessage);
    }

    public DatabaseException(ErrorCode errorCode, String userMessage, Throwable cause) {
        super(errorCode, userMessage, cause);
    }
}

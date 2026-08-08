package com.wego.parkingsystem.exception;

/** HTTP 500 — Static dataset parsing, loading, or coordinate conversion failure. */
public class DatasetException extends ApplicationException {

    public DatasetException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DatasetException(ErrorCode errorCode, String userMessage) {
        super(errorCode, userMessage);
    }

    public DatasetException(ErrorCode errorCode, String userMessage, Throwable cause) {
        super(errorCode, userMessage, cause);
    }
}

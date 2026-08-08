package com.wego.parkingsystem.exception;

/** HTTP 503 — Live availability synchronization failure (stale data, sync conflict). */
public class SynchronizationException extends ApplicationException {

    public SynchronizationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SynchronizationException(ErrorCode errorCode, String userMessage) {
        super(errorCode, userMessage);
    }

    public SynchronizationException(ErrorCode errorCode, String userMessage, Throwable cause) {
        super(errorCode, userMessage, cause);
    }
}

package com.wego.parkingsystem.exception;

/** HTTP 404/409/422 — Business rule or domain logic violation. */
public class BusinessException extends ApplicationException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(ErrorCode errorCode, String userMessage) {
        super(errorCode, userMessage);
    }
}

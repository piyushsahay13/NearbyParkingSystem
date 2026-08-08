package com.wego.parkingsystem.exception;

/** HTTP 502/504 — External partner API (data.gov.sg) error or timeout. */
public class PartnerApiException extends ApplicationException {

    public PartnerApiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PartnerApiException(ErrorCode errorCode, String userMessage) {
        super(errorCode, userMessage);
    }

    public PartnerApiException(ErrorCode errorCode, String userMessage, Throwable cause) {
        super(errorCode, userMessage, cause);
    }
}

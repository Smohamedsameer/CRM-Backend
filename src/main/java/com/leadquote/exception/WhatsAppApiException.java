package com.leadquote.exception;

public class WhatsAppApiException extends RuntimeException {
    public WhatsAppApiException(String message, Throwable cause) {
        super(message, cause);
    }
    public WhatsAppApiException(String message) {
        super(message);
    }
}

package com.leadquote.exception;

public class PdfGenerationException extends RuntimeException {
    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

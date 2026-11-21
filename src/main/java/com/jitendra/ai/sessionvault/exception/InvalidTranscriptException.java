package com.jitendra.ai.sessionvault.exception;

public class InvalidTranscriptException extends RuntimeException {
    public InvalidTranscriptException(String message) {
        super(message);
    }

    public InvalidTranscriptException(String message, Throwable cause) {
        super(message, cause);
    }
}

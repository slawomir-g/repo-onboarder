package com.jlabs.repo.onboarder.service.exceptions;

/**
 * Exception thrown when API key for Google Gemini is missing or invalid.
 * According to PRD, it should be thrown before starting repository processing.
 */
public class AiApiKeyException extends AiException {

    public AiApiKeyException(String message) {
        super(message);
    }

    public AiApiKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}

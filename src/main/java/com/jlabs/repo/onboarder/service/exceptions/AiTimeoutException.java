package com.jlabs.repo.onboarder.service.exceptions;

/**
 * Wyjątek rzucany gdy wystąpi timeout podczas komunikacji z API Google Gemini.
 */
public class AiTimeoutException extends AiException {

    public AiTimeoutException(String message) {
        super(message);
    }

    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}


package com.jlabs.repo.onboarder.service.exceptions;

/**
 * Wyjątek rzucany gdy wystąpi rate limiting z API Google Gemini.
 * Powinien być obsługiwany przez retry logic z exponential backoff.
 */
public class AiRateLimitException extends AiException {

    public AiRateLimitException(String message) {
        super(message);
    }

    public AiRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}


package com.jlabs.repo.onboarder.service.exceptions;

/**
 * Exception thrown when rate limiting occurs from Google Gemini API.
 * Should be handled by retry logic with exponential backoff.
 */
public class AiRateLimitException extends AiException {

    public AiRateLimitException(String message) {
        super(message);
    }

    public AiRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}

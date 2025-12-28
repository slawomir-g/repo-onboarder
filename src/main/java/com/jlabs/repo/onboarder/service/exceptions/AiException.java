package com.jlabs.repo.onboarder.service.exceptions;

/**
 * Base class for exceptions related to AI operations.
 * All exceptions related to Spring AI integration inherit from this class.
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}

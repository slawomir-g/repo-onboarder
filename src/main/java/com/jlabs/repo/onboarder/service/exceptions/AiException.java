package com.jlabs.repo.onboarder.service.exceptions;

/**
 * Bazowa klasa wyjątków związanych z operacjami AI.
 * Wszystkie wyjątki związane z integracją Spring AI dziedziczą po tej klasie.
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}


package com.jlabs.repo.onboarder.service.exceptions;

/**
 * Wyjątek rzucany gdy brakuje lub jest nieprawidłowy klucz API dla Google Gemini.
 * Zgodnie z PRD, powinien być rzucany przed rozpoczęciem przetwarzania repozytorium.
 */
public class AiApiKeyException extends AiException {

    public AiApiKeyException(String message) {
        super(message);
    }

    public AiApiKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}


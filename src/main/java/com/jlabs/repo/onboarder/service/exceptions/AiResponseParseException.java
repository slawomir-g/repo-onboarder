package com.jlabs.repo.onboarder.service.exceptions;

/**
 * Wyjątek rzucany gdy nie można sparsować odpowiedzi JSON z API Google Gemini.
 * Występuje gdy odpowiedź nie jest w oczekiwanym formacie JSON.
 */
public class AiResponseParseException extends AiException {

    public AiResponseParseException(String message) {
        super(message);
    }

    public AiResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }
}


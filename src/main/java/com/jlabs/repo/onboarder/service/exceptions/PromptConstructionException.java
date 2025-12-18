 
package com.jlabs.repo.onboarder.service.exceptions;

public class PromptConstructionException extends RuntimeException {
    public PromptConstructionException(String message) {
        super(message);
    }

    public PromptConstructionException(String message, Throwable cause) {
        super(message, cause);
    }
}
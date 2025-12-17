package com.jlabs.repo.onboarder.infrastructure.springai;

import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.service.exceptions.AiApiKeyException;
import com.jlabs.repo.onboarder.service.exceptions.AiException;
import com.jlabs.repo.onboarder.service.exceptions.AiRateLimitException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Klient infrastrukturalny odpowiedzialny za komunikację z Spring AI ChatModel.
 * Enkapsuluje wszystkie niskopoziomowe szczegóły komunikacji z AI, w tym:
 * - Wywołania ChatModel API
 * - Retry logic z exponential backoff
 * - Obsługę błędów (rate limit, authentication)
 * - Konwersję ChatResponse na String
 * 
 * Zgodnie z PRD:
 * - Używa Spring Retry z exponential backoff strategy dla API failures
 * - Obsługuje rate limiting z odpowiednimi opóźnieniami
 * - Wyklucza AiRateLimitException i AiApiKeyException z retry
 */
@Component
public class ChatModelClient {

    private static final Logger logger = LoggerFactory.getLogger(ChatModelClient.class);

    private final ChatModel chatModel;
    private final AiProperties aiProperties;

    public ChatModelClient(ChatModel chatModel, AiProperties aiProperties) {
        this.chatModel = chatModel;
        this.aiProperties = aiProperties;
    }

    /**
     * Wywołuje API z exponential backoff retry strategy używając Spring Retry.
     * Zgodnie z PRD: exponential backoff z konfigurowalnymi parametrami.
     * 
     * Spring Retry automatycznie obsługuje retry z exponential backoff na podstawie
     * konfiguracji w AiProperties. Wyjątki AiRateLimitException i AiApiKeyException
     * są wykluczone z retry i rzucane natychmiast.
     * 
     * @param promptText tekst promptu do wysłania
     * @return odpowiedź tekstowa z API
     * @throws AiException gdy wyczerpano wszystkie próby retry
     * @throws AiRateLimitException gdy wystąpi rate limiting (nie retryowane)
     * @throws AiApiKeyException gdy wystąpi błąd autoryzacji (nie retryowane)
     */
    @Retryable(
            maxAttemptsExpression = "#{@aiProperties.retry.maxAttempts}",
            noRetryFor = {AiRateLimitException.class, AiApiKeyException.class},
            backoff = @Backoff(
                    delayExpression = "#{@aiProperties.retry.initialDelayMs}",
                    multiplierExpression = "#{@aiProperties.retry.multiplier}",
                    maxDelayExpression = "#{@aiProperties.retry.maxDelayMs}"
            )
    )
    public String call(String promptText) {
        logger.debug("Wywołanie API Gemini");

        try {
            // Utwórz prompt i wywołaj model
            Prompt prompt = new Prompt(promptText);
            ChatResponse response = chatModel.call(prompt);

            // Wyciągnij tekst odpowiedzi
            String responseText = response.getResult().getOutput().getText();
            logger.debug("Otrzymano odpowiedź z API, długość: {} znaków", 
                    responseText != null ? responseText.length() : 0);

            return responseText;

        } catch (Exception e) {
            logger.warn("Błąd podczas wywołania API: {}", e.getMessage());

            // Sprawdź czy to rate limit error (429) lub authentication error (401)
            // Te błędy nie powinny być retryowane - rzucamy odpowiednie wyjątki
            if (isRateLimitError(e)) {
                throw new AiRateLimitException(
                        "Wystąpił rate limiting z API Google Gemini: " + e.getMessage(), e);
            }
            if (isAuthenticationError(e)) {
                throw new AiApiKeyException(
                        "Błąd autoryzacji z API Google Gemini. Sprawdź poprawność API key: " + e.getMessage(), e);
            }

            // Inne błędy będą retryowane przez Spring Retry
            throw new AiException("Błąd podczas wywołania API: " + e.getMessage(), e);
        }
    }

    /**
     * Metoda recover wywoływana po wyczerpaniu wszystkich prób retry.
     * 
     * @param e ostatni wyjątek który wystąpił
     * @param promptText tekst promptu który był wywoływany
     * @throws AiException zawsze rzuca wyjątek z informacją o nieudanych próbach
     */
    @Recover
    public String recover(AiException e, String promptText) {
        AiProperties.Retry retryConfig = aiProperties.getRetry();
        int maxAttempts = retryConfig.getMaxAttempts();
        logger.error("Nie udało się wywołać API po {} próbach", maxAttempts, e);
        throw new AiException(
                String.format("Nie udało się wywołać API po %d próbach", maxAttempts),
                e);
    }

    /**
     * Sprawdza czy wyjątek jest związany z rate limiting (429).
     * 
     * @param e wyjątek do sprawdzenia
     * @return true jeśli to rate limit error
     */
    private boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("429") || 
               message.toLowerCase().contains("rate limit") ||
               message.toLowerCase().contains("quota exceeded");
    }

    /**
     * Sprawdza czy wyjątek jest związany z autoryzacją (401).
     * 
     * @param e wyjątek do sprawdzenia
     * @return true jeśli to authentication error
     */
    private boolean isAuthenticationError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("401") || 
               message.toLowerCase().contains("unauthorized") ||
               message.toLowerCase().contains("invalid api key") ||
               message.toLowerCase().contains("authentication");
    }
}


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
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import org.springframework.ai.chat.metadata.Usage;

@Component
public class ChatModelClient {

    private static final Logger logger = LoggerFactory.getLogger(ChatModelClient.class);

    private final ChatModel chatModel;
    private final AiProperties aiProperties;

    public ChatModelClient(ChatModel chatModel, AiProperties aiProperties) {
        this.chatModel = chatModel;
        this.aiProperties = aiProperties;
    }
    @Retryable(
            maxAttemptsExpression = "#{@aiProperties.retry.maxAttempts}",
            noRetryFor = {AiRateLimitException.class, AiApiKeyException.class},
            backoff = @Backoff(
                    delayExpression = "#{@aiProperties.retry.initialDelayMs}",
                    multiplierExpression = "#{@aiProperties.retry.multiplier}",
                    maxDelayExpression = "#{@aiProperties.retry.maxDelayMs}"
            )
    )
    public String call(String promptText, GoogleGenAiChatOptions options) {
        logger.debug("Wywołanie API Gemini" + (options != null ? " z opcjami" : ""));

        try {

            long estimatedTokens = logPromptTokenEstimation(promptText);

            Prompt prompt = new Prompt(promptText, options);

            ChatResponse response = chatModel.call(prompt);

            logActualTokenUsage(response);

            return response.getResult().getOutput().getText();

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
     * @param options opcje wywołania API (może być null)
     * @throws AiException zawsze rzuca wyjątek z informacją o nieudanych próbach
     */
    @Recover
    public String recover(AiException e, String promptText, GoogleGenAiChatOptions options) {
        AiProperties.Retry retryConfig = aiProperties.getRetry();
        int maxAttempts = retryConfig.getMaxAttempts();
        logger.error("Nie udało się wywołać API po {} próbach", maxAttempts, e);
        throw new AiException(
                String.format("Nie udało się wywołać API po %d próbach", maxAttempts),
                e);
    }


    private long logPromptTokenEstimation(String promptText) {
        long estimatedTokens = estimateTokenCount(promptText);
        int promptLength = promptText.length();
        double promptSizeKB = promptLength / 1024.0;
        
        logger.info("Przygotowanie do wysłania promptu: {} znaków ({} KB), szacowana liczba tokenów: ~{}",
                promptLength, String.format("%.2f", promptSizeKB), estimatedTokens);

        return estimatedTokens;
    }

    /**
     * Szacuje liczbę tokenów w tekście promptu.
     * Dla modeli Gemini używa przybliżenia: 1 token ≈ 3.5 znaków.
     * To jest konserwatywne szacowanie - rzeczywista liczba tokenów może być nieco niższa.
     * 
     * Uwaga: To jest przybliżenie. Rzeczywista liczba tokenów zależy od:
     * - Języka tekstu (polski może mieć więcej tokenów niż angielski)
     * - Specyfiki tokenizera modelu Gemini
     * - Obecności znaków specjalnych, liczb, kodów itp.
     * 
     * @param text tekst do oszacowania
     * @return szacowana liczba tokenów
     */
    private long estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // Dla Gemini: konserwatywne szacowanie 1 token = 3.5 znaki
        // Rzeczywista wartość może być różna, ale to daje bezpieczne oszacowanie
        return Math.round(text.length() / 3.5);
    }


    private void logActualTokenUsage(ChatResponse response) {
        try {
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                logger.debug("Brak informacji o użyciu tokenów w metadata odpowiedzi");
                return;
            }

            var totalTokens = usage.getTotalTokens();
            if(usage.getNativeUsage() instanceof com.google.genai.types.GenerateContentResponseUsageMetadata nativeUsage) {
                var cachedTokens = nativeUsage.cachedContentTokenCount().orElse(0);
                Long paidTokens = (long) totalTokens - cachedTokens;

                logger.info("Total tokens: {}", totalTokens);
                logger.info("Cached tokens: {}", cachedTokens);
                logger.info("Płatne tokeny: {}", paidTokens);
            } else {
                logger.info("Nie udało się odczytać informacji o tokenach z metadata");
            }
        } catch (Exception e) {
            logger.debug("Nie udało się odczytać informacji o tokenach z metadata: {}", e.getMessage());
        }
    }

    private boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("429") || 
               message.toLowerCase().contains("rate limit") ||
               message.toLowerCase().contains("quota exceeded");
    }

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


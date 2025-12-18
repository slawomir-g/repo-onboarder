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

import org.springframework.ai.chat.metadata.Usage;

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
            // Szacuj i loguj informacje o promptcie przed wysłaniem
            long estimatedTokens = logPromptTokenEstimation(promptText);
            
            // Utwórz prompt i wywołaj model
            Prompt prompt = new Prompt(promptText);
            
            ChatResponse response = chatModel.call(prompt);
            
            // Loguj rzeczywistą liczbę tokenów z odpowiedzi (jeśli dostępna)
            logActualTokenUsage(response, estimatedTokens);

            // Wyciągnij tekst odpowiedzi z ChatResponse
            return extractResponseText(response, promptText);

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
     * Szacuje liczbę tokenów w promptcie, loguje informacje i ostrzega jeśli prompt jest zbyt duży.
     * 
     * @param promptText tekst promptu do analizy
     * @return szacowana liczba tokenów
     */
    private long logPromptTokenEstimation(String promptText) {
        long estimatedTokens = estimateTokenCount(promptText);
        int promptLength = promptText.length();
        double promptSizeKB = promptLength / 1024.0;
        
        logger.info("Przygotowanie do wysłania promptu: {} znaków ({} KB), szacowana liczba tokenów: ~{}",
                promptLength, String.format("%.2f", promptSizeKB), estimatedTokens);
        
        return estimatedTokens;
    }

    /**
     * Wyciąga tekst odpowiedzi z ChatResponse, obsługując różne przypadki braku generacji.
     * 
     * @param response odpowiedź z API
     * @param promptText tekst promptu (używany do logowania błędów)
     * @return tekst odpowiedzi z API
     * @throws AiException gdy odpowiedź nie zawiera generacji
     */
    private String extractResponseText(ChatResponse response, String promptText) {
        // Sprawdź czy odpowiedź zawiera generacje
        if (response.getResult() == null) {
            // Sprawdź czy lista generacji jest pusta
            if (response.getResults() == null || response.getResults().isEmpty()) {
                logger.error("Otrzymano pustą odpowiedź z API - brak generacji. Metadata: {}",
                        response.getMetadata());
                logger.error("Długość promptu: {} znaków ({} KB)",
                        promptText.length(), promptText.length() / 1024);
                throw new AiException("API zwróciło pustą odpowiedź - brak generacji. " +
                        "Możliwe przyczyny: zbyt długi prompt (" + (promptText.length() / 1024) + " KB), " +
                        "limit tokenów przekroczony, lub błąd modelu. Metadata: " + response.getMetadata());
            }
            // Jeśli getResult() zwraca null, ale lista nie jest pusta, użyj pierwszego elementu
            String responseText = response.getResults().get(0).getOutput().getText();
            logger.debug("Otrzymano odpowiedź z API (użyto getResults()), długość: {} znaków",
                    responseText != null ? responseText.length() : 0);
            return responseText;
        }

        // Wyciągnij tekst odpowiedzi
        String responseText = response.getResult().getOutput().getText();
        logger.debug("Otrzymano odpowiedź z API, długość: {} znaków",
                responseText != null ? responseText.length() : 0);

        return responseText;
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

    /**
     * Loguje rzeczywistą liczbę tokenów z odpowiedzi API (jeśli dostępna w metadata).
     * Porównuje z szacowaną wartością dla celów diagnostycznych.
     * 
     * @param response odpowiedź z API
     * @param estimatedTokens szacowana liczba tokenów użyta przed wysłaniem
     */
    private void logActualTokenUsage(ChatResponse response, long estimatedTokens) {
        try {
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                logger.debug("Brak informacji o użyciu tokenów w metadata odpowiedzi");
                return;
            }

            var promptTokens = usage.getPromptTokens();
            var completionTokens = getCompletionTokens(usage);
            var totalTokens = usage.getTotalTokens();

            logger.info("Rzeczywista liczba tokenów z API - Input: {}, Output: {}, Total: {} (szacowane: ~{})",
                    formatTokenCount(promptTokens),
                    formatTokenCount(completionTokens),
                    formatTokenCount(totalTokens),
                    estimatedTokens);

            if (promptTokens != null && estimatedTokens > 0) {
                double ratio = (double) promptTokens / estimatedTokens;
                logger.debug("Stosunek rzeczywistych/szacowanych tokenów: {:.2f}", ratio);
            }
        } catch (Exception e) {
            logger.debug("Nie udało się odczytać informacji o tokenach z metadata: {}", e.getMessage());
        }
    }

    /**
     * Konwertuje Number na Long, zwraca null jeśli wartość jest null.
     */
    private Long toLong(Number number) {
        return number != null ? number.longValue() : null;
    }

    /**
     * Pobiera liczbę tokenów completion z Usage, obsługując różne wersje Spring AI.
     */
    private Long getCompletionTokens(org.springframework.ai.chat.metadata.Usage usage) {
        try {
            return toLong(usage.getCompletionTokens());
        } catch (Exception e) {
            logger.debug("getCompletionTokens() nie dostępne: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Formatuje liczbę tokenów do wyświetlenia w logach.
     */
    private String formatTokenCount(Number count) {
        return count != null ? count.toString() : "N/A";
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


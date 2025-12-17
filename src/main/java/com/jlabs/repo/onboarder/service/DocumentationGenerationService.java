package com.jlabs.repo.onboarder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import com.jlabs.repo.onboarder.service.exceptions.AiApiKeyException;
import com.jlabs.repo.onboarder.service.exceptions.AiException;
import com.jlabs.repo.onboarder.service.exceptions.AiRateLimitException;
import com.jlabs.repo.onboarder.service.exceptions.AiResponseParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Serwis odpowiedzialny za generację dokumentacji przy użyciu AI modelu.
 * Integruje się z Spring AI ChatModel, implementuje exponential backoff retry strategy
 * i parsuje odpowiedzi JSON zgodnie z PRD.
 * 
 * Zgodnie z PRD:
 * - Implementuje exponential backoff retry strategy dla API failures
 * - Obsługuje rate limiting z odpowiednimi opóźnieniami
 * - Parsuje odpowiedzi JSON z modelu
 * - Generuje trzy typy dokumentacji: README, Architecture, Context File
 */
@Service
public class DocumentationGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationGenerationService.class);

    private final ChatModel chatModel;
    private final PromptConstructionService promptConstructionService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public DocumentationGenerationService(
            ChatModel chatModel,
            PromptConstructionService promptConstructionService,
            AiProperties aiProperties,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.promptConstructionService = promptConstructionService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Generuje dokumentację dla repozytorium używając AI modelu.
     * Implementuje exponential backoff retry strategy zgodnie z PRD.
     * 
     * @param report raport z analizy Git repozytorium
     * @param repoRoot ścieżka do katalogu głównego repozytorium
     * @return wynik generacji dokumentacji zawierający README, Architecture i Context File
     * @throws AiException gdy wystąpi błąd podczas generacji (po wyczerpaniu prób retry)
     */
    public DocumentationResult generateDocumentation(GitReport report, Path repoRoot) {
        // Konstruuj prompt
        String promptText = promptConstructionService.constructPrompt(report, repoRoot);

        // Wywołaj API z retry logic
        String responseText = callApiWithRetry(promptText);

        // Parsuj odpowiedź JSON
        return parseResponse(responseText);
    }

    /**
     * Wywołuje API z exponential backoff retry strategy.
     * Zgodnie z PRD: exponential backoff z konfigurowalnymi parametrami.
     * 
     * @param promptText tekst promptu do wysłania
     * @return odpowiedź tekstowa z API
     * @throws AiException gdy wyczerpano wszystkie próby retry
     */
    private String callApiWithRetry(String promptText) {
        AiProperties.Retry retryConfig = aiProperties.getRetry();
        int maxAttempts = retryConfig.getMaxAttempts();
        long initialDelayMs = retryConfig.getInitialDelayMs();
        double multiplier = retryConfig.getMultiplier();
        long maxDelayMs = retryConfig.getMaxDelayMs();

        Exception lastException = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                logger.debug("Wywołanie API Gemini, próba {}/{}", attempt + 1, maxAttempts);

                // Utwórz prompt i wywołaj model
                Prompt prompt = new Prompt(promptText);
                ChatResponse response = chatModel.call(prompt);

                // Wyciągnij tekst odpowiedzi
                // getResults() zwraca listę Generation, bierzemy pierwszy element
                String responseText = response.getResult().getOutput().getText();
                logger.debug("Otrzymano odpowiedź z API, długość: {} znaków", responseText != null ? responseText.length() : 0);

                return responseText;

            } catch (Exception e) {
                lastException = e;
                logger.warn("Błąd podczas wywołania API (próba {}/{}): {}", 
                        attempt + 1, maxAttempts, e.getMessage());

                // Sprawdź czy to rate limit error (429) lub authentication error (401)
                if (isRateLimitError(e)) {
                    throw new AiRateLimitException(
                            "Wystąpił rate limiting z API Google Gemini: " + e.getMessage(), e);
                }
                if (isAuthenticationError(e)) {
                    throw new AiApiKeyException(
                            "Błąd autoryzacji z API Google Gemini. Sprawdź poprawność API key: " + e.getMessage(), e);
                }

                // Jeśli to nie ostatnia próba, oblicz opóźnienie i czekaj
                if (attempt < maxAttempts - 1) {
                    long delayMs = calculateBackoffDelay(attempt, initialDelayMs, multiplier, maxDelayMs);
                    logger.info("Czekanie {}ms przed kolejną próbą...", delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiException("Przerwano podczas retry", ie);
                    }
                }
            }
        }

        // Wyczerpano wszystkie próby
        throw new AiException(
                String.format("Nie udało się wywołać API po %d próbach", maxAttempts),
                lastException);
    }

    /**
     * Oblicza opóźnienie dla exponential backoff.
     * Formuła: delay = initialDelayMs * (multiplier ^ attemptNumber)
     * Z ograniczeniem do maxDelayMs.
     * 
     * @param attempt numer próby (0-based)
     * @param initialDelayMs początkowe opóźnienie w ms
     * @param multiplier mnożnik dla exponential backoff
     * @param maxDelayMs maksymalne opóźnienie w ms
     * @return opóźnienie w milisekundach
     */
    private long calculateBackoffDelay(int attempt, long initialDelayMs, double multiplier, long maxDelayMs) {
        double delay = initialDelayMs * Math.pow(multiplier, attempt);
        return Math.min((long) delay, maxDelayMs);
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

    /**
     * Parsuje odpowiedź JSON z API i tworzy DocumentationResult.
     * Zgodnie z PRD, odpowiedź powinna zawierać pola: readme, architecture, contextFile.
     * Obsługuje odpowiedzi zawierające JSON w markdown code block (```json ... ```).
     * 
     * @param responseText tekst odpowiedzi z API (może zawierać JSON w markdown code block)
     * @return sparsowany DocumentationResult
     * @throws AiResponseParseException gdy nie można sparsować JSON lub brakuje wymaganych pól
     */
    private DocumentationResult parseResponse(String responseText) {
        try {
            // Usuń markdown code block jeśli istnieje (```json ... ```)
            String cleanedJson = extractJsonFromMarkdown(responseText);
            
            // Spróbuj sparsować JSON
            JsonNode jsonNode = objectMapper.readTree(cleanedJson);

            // Wyciągnij pola z JSON
            // Zgodnie z template promptu, odpowiedź może zawierać "ai_context_file"
            // Ale PRD wymaga trzech pól: readme, architecture, contextFile
            // Dla MVP, jeśli jest tylko "ai_context_file", użyjemy go jako contextFile
            String readme = extractField(jsonNode, "readme");
            String architecture = extractField(jsonNode, "architecture");
            String contextFile = extractField(jsonNode, "contextFile");

            // Jeśli nie ma standardowych pól, sprawdź czy jest "ai_context_file" (z template)
            if (contextFile == null || contextFile.isBlank()) {
                contextFile = extractField(jsonNode, "ai_context_file");
            }

            // Walidacja - przynajmniej jedno pole musi być wypełnione
            if ((readme == null || readme.isBlank()) && 
                (architecture == null || architecture.isBlank()) && 
                (contextFile == null || contextFile.isBlank())) {
                throw new AiResponseParseException(
                        "Odpowiedź JSON nie zawiera wymaganych pól (readme, architecture, contextFile)");
            }

            return new DocumentationResult(
                    readme != null ? readme : "",
                    architecture != null ? architecture : "",
                    contextFile != null ? contextFile : ""
            );

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AiResponseParseException(
                    "Nie można sparsować odpowiedzi JSON z API: " + e.getMessage(), e);
        }
    }

    /**
     * Wyciąga pole z JSON node, obsługując różne formaty (tekst, obiekt z "content", etc.).
     * 
     * @param jsonNode node JSON
     * @param fieldName nazwa pola
     * @return wartość pola jako String lub null jeśli nie istnieje
     */
    private String extractField(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }

        // Jeśli to tekst, zwróć bezpośrednio
        if (fieldNode.isTextual()) {
            return fieldNode.asText();
        }

        // Jeśli to obiekt, spróbuj wyciągnąć "content" lub "text"
        if (fieldNode.isObject()) {
            JsonNode contentNode = fieldNode.get("content");
            if (contentNode != null && contentNode.isTextual()) {
                return contentNode.asText();
            }
            JsonNode textNode = fieldNode.get("text");
            if (textNode != null && textNode.isTextual()) {
                return textNode.asText();
            }
        }

        // W przeciwnym razie, użyj toString()
        return fieldNode.toString();
    }

    /**
     * Wyciąga JSON z markdown code block jeśli jest opakowany w ```json ... ```.
     * Jeśli odpowiedź nie zawiera markdown code block, zwraca oryginalny tekst.
     * 
     * @param responseText tekst odpowiedzi z API
     * @return wyczyszczony JSON bez markdown code block
     */
    private String extractJsonFromMarkdown(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return responseText;
        }

        // Usuń białe znaki na początku i końcu
        String trimmed = responseText.trim();

        // Sprawdź czy zaczyna się od ```json lub ```
        if (trimmed.startsWith("```json")) {
            // Znajdź początek JSON (po ```json)
            int jsonStart = trimmed.indexOf("```json") + 7;
            // Znajdź koniec (```)
            int jsonEnd = trimmed.lastIndexOf("```");
            
            if (jsonEnd > jsonStart) {
                String jsonContent = trimmed.substring(jsonStart, jsonEnd).trim();
                logger.debug("Wyciągnięto JSON z markdown code block, długość: {} znaków", jsonContent.length());
                return jsonContent;
            }
        } else if (trimmed.startsWith("```")) {
            // Obsługa przypadku gdy jest tylko ``` bez json
            int jsonStart = trimmed.indexOf("```") + 3;
            int jsonEnd = trimmed.lastIndexOf("```");
            
            if (jsonEnd > jsonStart) {
                String jsonContent = trimmed.substring(jsonStart, jsonEnd).trim();
                logger.debug("Wyciągnięto JSON z markdown code block (bez json), długość: {} znaków", jsonContent.length());
                return jsonContent;
            }
        }

        // Jeśli nie ma markdown code block, zwróć oryginalny tekst
        return trimmed;
    }
}


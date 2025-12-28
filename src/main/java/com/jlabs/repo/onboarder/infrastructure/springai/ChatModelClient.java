package com.jlabs.repo.onboarder.infrastructure.springai;

import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.service.exceptions.AiApiKeyException;
import com.jlabs.repo.onboarder.service.exceptions.AiException;
import com.jlabs.repo.onboarder.service.exceptions.AiRateLimitException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class ChatModelClient {

    private final ChatModel chatModel;
    private final AiProperties aiProperties;

    @Retryable(maxAttemptsExpression = "#{@aiProperties.retry.maxAttempts}", noRetryFor = { AiRateLimitException.class,
            AiApiKeyException.class }, backoff = @Backoff(delayExpression = "#{@aiProperties.retry.initialDelayMs}", multiplierExpression = "#{@aiProperties.retry.multiplier}", maxDelayExpression = "#{@aiProperties.retry.maxDelayMs}"))
    public String call(String promptText, GoogleGenAiChatOptions options) {
        log.debug("Calling Gemini API" + (options != null ? " with options" : ""));

        try {

            long estimatedTokens = logPromptTokenEstimation(promptText);

            Prompt prompt = new Prompt(promptText, options);

            ChatResponse response = chatModel.call(prompt);

            logActualTokenUsage(response);

            return response.getResult().getOutput().getText();

        } catch (Exception e) {
            log.warn("Error during API call: {}", e.getMessage());

            // Check if it is rate limit error (429) or authentication error (401)
            // These errors should not be retried - throwing appropriate exceptions
            if (isRateLimitError(e)) {
                throw new AiRateLimitException(
                        "Rate limiting occurred from Google Gemini API: " + e.getMessage(), e);
            }
            if (isAuthenticationError(e)) {
                throw new AiApiKeyException(
                        "Authentication error from Google Gemini API. Check API key correctness: " + e.getMessage(), e);
            }

            // Other errors will be retried by Spring Retry
            throw new AiException("Error during API call: " + e.getMessage(), e);
        }
    }

    /**
     * Recover method called after exhausting all retry attempts.
     * 
     * @param e          last exception that occurred
     * @param promptText prompt text that was called
     * @param options    API call options (can be null)
     * @throws AiException always throws exception with information about failed
     *                     attempts
     */
    @Recover
    public String recover(AiException e, String promptText, GoogleGenAiChatOptions options) {
        AiProperties.Retry retryConfig = aiProperties.getRetry();
        int maxAttempts = retryConfig.getMaxAttempts();
        log.error("Failed to call API after {} attempts", maxAttempts, e);
        throw new AiException(
                String.format("Failed to call API after %d attempts", maxAttempts),
                e);
    }

    private long logPromptTokenEstimation(String promptText) {
        long estimatedTokens = estimateTokenCount(promptText);
        int promptLength = promptText.length();
        double promptSizeKB = promptLength / 1024.0;

        log.info("Preparing to send prompt: {} chars ({} KB), estimated tokens: ~{}",
                promptLength, String.format("%.2f", promptSizeKB), estimatedTokens);

        return estimatedTokens;
    }

    /**
     * Estimates the number of tokens in the prompt text.
     * For Gemini models, uses approximation: 1 token ≈ 3.5 characters.
     * This is a conservative estimate - actual token count might be slightly lower.
     * 
     * Note: This is an approximation. Actual token count depends on:
     * - Text language (Polish might have more tokens than English)
     * - Specifics of Gemini model tokenizer
     * - Presence of special characters, numbers, codes etc.
     * 
     * @param text text to estimate
     * @return estimated token count
     */
    private long estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // For Gemini: conservative estimate 1 token = 3.5 characters
        // Actual value may vary, but this gives a safe estimate
        return Math.round(text.length() / 3.5);
    }

    private void logActualTokenUsage(ChatResponse response) {
        try {
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                log.debug("No token usage info in response metadata");
                return;
            }

            var totalTokens = usage.getTotalTokens();
            if (usage
                    .getNativeUsage() instanceof com.google.genai.types.GenerateContentResponseUsageMetadata nativeUsage) {
                var cachedTokens = nativeUsage.cachedContentTokenCount().orElse(0);
                Long paidTokens = (long) totalTokens - cachedTokens;

                log.info("Total tokens: {}", totalTokens);
                log.info("Cached tokens: {}", cachedTokens);
                log.info("Paid tokens: {}", paidTokens);
            } else {
                log.info("Failed to read token info from metadata");
            }
        } catch (Exception e) {
            log.debug("Failed to read token info from metadata: {}", e.getMessage());
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

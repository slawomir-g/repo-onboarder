package com.jlabs.repo.onboarder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration properties for Spring AI and Google Gemini API integration.
 * Properties are loaded from application.yml with prefix
 * "spring.ai.google.genai".
 */
@ConfigurationProperties(prefix = "spring.ai.google.genai")
@Data
public class AiProperties {

    /**
     * API key for Google Gemini API.
     * Required for API authorization.
     */
    private String apiKey;

    /**
     * Chat model option configuration.
     */
    private Chat chat = new Chat();

    /**
     * Retry strategy configuration for API calls.
     */
    private Retry retry = new Retry();

    /**
     * Gemini chat model option configuration.
     */
    @Data
    public static class Chat {
        /**
         * Chat model options.
         */
        private Options options = new Options();

        /**
         * Detailed chat model options.
         */
        @Data
        public static class Options {
            /**
             * Gemini model name to use (e.g., "gemini-1.5-pro", "gemini-2.0-flash").
             * Default: "gemini-1.5-pro"
             */
            private String model = "gemini-1.5-pro";

            /**
             * Response temperature (0.0-1.0).
             * Higher values produce more creative responses.
             * Default: 0.7
             */
            private double temperature = 0.7;

            /**
             * Maximum number of output tokens.
             * Default: 8192
             */
            private Integer maxOutputTokens = 8192;

            private Duration repositoryCacheTtl = Duration.ofHours(1);
        }
    }

    /**
     * Retry strategy configuration with exponential backoff.
     */
    @Data
    public static class Retry {
        /**
         * Maximum number of retry attempts.
         * Default: 3
         */
        private int maxAttempts = 3;

        /**
         * Initial delay in milliseconds before the first retry attempt.
         * Default: 1000ms (1 second)
         */
        private long initialDelayMs = 1000;

        /**
         * Multiplier for exponential backoff.
         * Delay = initialDelayMs * (multiplier ^ attemptNumber)
         * Default: 2.0
         */
        private double multiplier = 2.0;

        /**
         * Maximum delay in milliseconds.
         * Default: 30000ms (30 seconds)
         */
        private long maxDelayMs = 30000;
    }
}

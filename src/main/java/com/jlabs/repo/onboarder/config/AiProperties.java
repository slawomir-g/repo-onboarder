package com.jlabs.repo.onboarder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

import java.time.Duration;

/**
 * Konfiguracja właściwości dla integracji z Spring AI i Google Gemini API.
 * Właściwości są ładowane z application.yml z prefiksem
 * "spring.ai.google.genai".
 */
@ConfigurationProperties(prefix = "spring.ai.google.genai")
@Data
public class AiProperties {

    /**
     * Klucz API dla Google Gemini API.
     * Wymagany do autoryzacji wywołań API.
     */
    private String apiKey;

    /**
     * Konfiguracja opcji chat modelu.
     */
    private Chat chat = new Chat();

    /**
     * Konfiguracja strategii retry dla wywołań API.
     */
    private Retry retry = new Retry();

    /**
     * Konfiguracja opcji chat modelu Gemini.
     */
    @Data
    public static class Chat {
        /**
         * Opcje chat modelu.
         */
        private Options options = new Options();

        /**
         * Szczegółowe opcje chat modelu.
         */
        @Data
        public static class Options {
            /**
             * Nazwa modelu Gemini do użycia (np. "gemini-1.5-pro", "gemini-2.0-flash").
             * Domyślnie: "gemini-1.5-pro"
             */
            private String model = "gemini-1.5-pro";

            /**
             * Temperatura odpowiedzi (0.0-1.0).
             * Wyższe wartości dają bardziej kreatywne odpowiedzi.
             * Domyślnie: 0.7
             */
            private double temperature = 0.7;

            /**
             * Maksymalna liczba tokenów w odpowiedzi.
             * Domyślnie: 8192
             */
            private Integer maxOutputTokens = 8192;

            private Duration repositoryCacheTtl = Duration.ofHours(1);
        }
    }

    /**
     * Konfiguracja strategii retry z exponential backoff.
     */
    @Data
    public static class Retry {
        /**
         * Maksymalna liczba prób retry.
         * Domyślnie: 3
         */
        private int maxAttempts = 3;

        /**
         * Początkowe opóźnienie w milisekundach przed pierwszą próbą retry.
         * Domyślnie: 1000ms (1 sekunda)
         */
        private long initialDelayMs = 1000;

        /**
         * Mnożnik dla exponential backoff.
         * Opóźnienie = initialDelayMs * (multiplier ^ attemptNumber)
         * Domyślnie: 2.0
         */
        private double multiplier = 2.0;

        /**
         * Maksymalne opóźnienie w milisekundach.
         * Domyślnie: 30000ms (30 sekund)
         */
        private long maxDelayMs = 30000;
    }
}

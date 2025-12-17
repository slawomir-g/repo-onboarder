package com.jlabs.repo.onboarder.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.jlabs.repo.onboarder.service.exceptions.AiApiKeyException;

import jakarta.annotation.PostConstruct;

/**
 * Konfiguracja Spring AI dla integracji z Google Gemini API.
 * Waliduje konfigurację API key przy starcie aplikacji.
 * 
 * ChatModel jest automatycznie skonfigurowany przez Spring Boot auto-configuration
 * na podstawie właściwości w application.yml (spring.ai.google.genai.*).
 * 
 * Zgodnie z PRD, walidacja API key powinna nastąpić przed rozpoczęciem przetwarzania
 * repozytorium, aby oszczędzić zasoby w przypadku błędnej konfiguracji.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    private final AiProperties aiProperties;

    public AiConfiguration(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    /**
     * Waliduje obecność API key przy starcie aplikacji.
     * Zgodnie z PRD (US-011), brak API key powinien być wykryty przed rozpoczęciem przetwarzania.
     * 
     * @throws AiApiKeyException gdy API key jest brakujący lub pusty
     */
    @PostConstruct
    public void validateApiKey() {
        String apiKey = aiProperties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new AiApiKeyException(
                    "Brak konfiguracji API key dla Google Gemini. " +
                    "Ustaw właściwość 'spring.ai.google.genai.api-key' w application.yml lub zmienną środowiskową."
            );
        }
    }
}


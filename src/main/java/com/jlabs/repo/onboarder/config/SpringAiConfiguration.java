package com.jlabs.repo.onboarder.config;

import com.google.genai.Client;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SpringAiConfiguration {

    /**
     * Tworzy bean GoogleGenAiCachedContentService używany przez RepositoryCacheService
     * do zarządzania cache'owanym kontekstem repozytoriów.
     * 
     * @param genAiClient Client Google GenAI (automatycznie wstrzykiwany przez Spring AI)
     * @return instancja GoogleGenAiCachedContentService
     */
    @Bean
    public GoogleGenAiCachedContentService googleGenAiCachedContentService(Client genAiClient) {
        return new GoogleGenAiCachedContentService(genAiClient);
    }
}


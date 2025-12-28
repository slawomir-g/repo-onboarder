package com.jlabs.repo.onboarder.config;

import com.google.genai.Client;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfiguration {

    /**
     * Creates GoogleGenAiCachedContentService bean used by RepositoryCacheService
     * to manage cached repository contexts.
     * 
     * @param genAiClient Google GenAI Client (automatically injected by Spring AI)
     * @return GoogleGenAiCachedContentService instance
     */
    @Bean
    public GoogleGenAiCachedContentService googleGenAiCachedContentService(Client genAiClient) {
        return new GoogleGenAiCachedContentService(genAiClient);
    }
}

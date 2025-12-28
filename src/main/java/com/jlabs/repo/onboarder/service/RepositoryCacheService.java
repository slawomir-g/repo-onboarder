package com.jlabs.repo.onboarder.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.jlabs.repo.onboarder.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

/**
 * Service responsible for managing cached repository context in Google GenAI.
 * 
 * Uses Google GenAI Cached Content API to store large repository contexts
 * (XML with directory tree, hotspots, commits, source code), which:
 * - Reduces API costs (cached tokens are 10x cheaper than input tokens)
 * - Speeds up API calls (no need to send large context every time)
 * - Preserves semantics (cache is treated as system instruction/context)
 * 
 * Cache is identified by repository URL and automatically expires after
 * configured TTL.
 * 
 * NOTE: Requires cached content enabled in configuration:
 * spring.ai.google.genai.chat.enable-cached-content=true
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RepositoryCacheService {

    private final GoogleGenAiCachedContentService cachedContentService;
    private final AiProperties aiProperties;
    // Removing final field cacheEnabled and recalculating it or handling it
    // differently since RequiredArgsConstructor expects final fields to be in
    // constructor.
    // However, cacheEnabled logic was in constructor. I can move it to
    // @PostConstruct or keep it as non-final but initialized.
    // Actually, looking at the code, cacheEnabled is derived from
    // cachedContentService != null.
    // If I use @RequiredArgsConstructor, I can't have custom logic in constructor.
    // I will use a @PostConstruct method or just check cachedContentService != null
    // directly.

    private boolean isCacheEnabled() {
        return cachedContentService != null;
    }

    @PostConstruct
    public void init() {
        if (!isCacheEnabled()) {
            log.warn("GoogleGenAiCachedContentService is not available - cache will be disabled. " +
                    "To enable cache, set: spring.ai.google.genai.chat.enable-cached-content=true");
        } else {
            log.info("RepositoryCacheService initialized with cache enabled");
        }
    }

    /**
     * Generates cache name for repository based on its URL.
     * Format: github-user-repo (after URL sanitization).
     * 
     * @param repoUrl Git repository URL
     * @return sanitized cache name
     */
    public String getCacheNameForRepository(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return "unknown-repo";
        }

        // Remove protocol (https://, git@)
        String sanitized = repoUrl.replaceAll("^(https?://|git@)", "");

        // Remove .git at the end
        sanitized = sanitized.replaceAll("\\.git$", "");

        // Replace special characters with hyphens
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9]+", "-");

        // Remove hyphens at start and end
        sanitized = sanitized.replaceAll("^-+|-+$", "");

        // Convert to lowercase
        sanitized = sanitized.toLowerCase();

        log.debug("Cache name for repo URL '{}': '{}'", repoUrl, sanitized);
        return sanitized;
    }

    /**
     * Checks if cache for repository already exists and is active (not expired).
     * 
     * @param repoUrl Git repository URL
     * @return Optional with cached content name if exists and active, Empty if not
     *         exists or expired
     */
    public Optional<String> getCachedContentName(String repoUrl) {
        if (!isCacheEnabled()) {
            return Optional.empty();
        }

        String cacheName = getCacheNameForRepository(repoUrl);

        try {
            // Search all caches looking for ours
            List<GoogleGenAiCachedContent> allCaches = cachedContentService.listAll();

            for (GoogleGenAiCachedContent cache : allCaches) {
                // Check if display name matches our name
                if (cache.getDisplayName() != null && cache.getDisplayName().contains(cacheName)) {
                    // Check if cache didn't expire
                    if (!cache.isExpired()) {
                        String fullCacheName = cache.getName();
                        Duration remainingTtl = cache.getRemainingTtl();

                        log.info("Found active cache for repo '{}': '{}', remaining TTL: {} minutes",
                                repoUrl, fullCacheName, remainingTtl.toMinutes());

                        return Optional.of(fullCacheName);
                    } else {
                        log.info("Cache for repo '{}' expired, will be deleted and recreated", repoUrl);
                        // Delete expired cache
                        cachedContentService.delete(cache.getName());
                    }
                }
            }

            log.info("Active cache not found for repo '{}'", repoUrl);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Error while checking cache for repo '{}': {}", repoUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Creates new cached content for repository containing repository context XML.
     * 
     * Cache will contain:
     * - System instruction: Repository context XML (directory tree, hotspots,
     * commits, source code)
     * - Display name: Repository name + timestamp
     * - TTL: Configured TTL (default 1 hour)
     * - Model: Same model as used for generation
     * 
     * @param repoUrl              Git repository URL
     * @param repositoryContextXml XML containing full repository context
     * @param model                Google GenAI model name (e.g. "gemini-2.5-pro")
     * @return full name of created cached content (format: cachedContent/xxx),
     *         or null if cache is disabled
     * @throws RuntimeException when cannot create cache
     */
    public String createCachedContent(String repoUrl, String repositoryContextXml, String model) {
        if (!isCacheEnabled()) {
            log.warn("Cache is disabled - cannot create cached content");
            return null;
        }

        String cacheName = getCacheNameForRepository(repoUrl);

        try {
            log.info("Creating new cache for repo '{}' (display name: '{}')", repoUrl, cacheName);

            // Estimate token count
            long estimatedTokens = estimateTokenCount(repositoryContextXml);
            log.info("Estimated tokens in cache: ~{}", estimatedTokens);

            if (estimatedTokens < 32768) {
                log.warn(
                        "Warning: Estimated token count ({}) is below minimum 32,768 required by Google GenAI Cached Content API. "
                                +
                                "Cache might not be created.",
                        estimatedTokens);
            }

            // Create cached content request
            CachedContentRequest request = CachedContentRequest.builder()
                    .model(model)
                    .contents(List.of(
                            Content.builder()
                                    .role("user")
                                    .parts(List.of(Part.fromText(repositoryContextXml)))
                                    .build()))
                    .displayName(cacheName)
                    .ttl(aiProperties.getChat().getOptions().getRepositoryCacheTtl())
                    .build();

            // Create cached content
            GoogleGenAiCachedContent cachedContent = cachedContentService.create(request);
            String fullCacheName = cachedContent.getName();

            // Log info about created cache
            logCacheCreated(cachedContent, repoUrl);

            return fullCacheName;

        } catch (Exception e) {
            log.error("Failed to create cache for repo '{}': {}", repoUrl, e.getMessage(), e);
            throw new RuntimeException("Failed to create cached content: " + e.getMessage(), e);
        }
    }

    /**
     * Estimates number of tokens in text.
     * For Gemini models uses approximation: 1 token ≈ 3.5 characters.
     */
    private long estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.round(text.length() / 3.5);
    }

    /**
     * Logs detailed information about created cache.
     */
    private void logCacheCreated(GoogleGenAiCachedContent cachedContent, String repoUrl) {
        log.info("Created cache for repo '{}': '{}'", repoUrl, cachedContent.getName());
        log.info("  Display name: {}", cachedContent.getDisplayName());
        log.info("  Model: {}", cachedContent.getModel());
        log.info("  TTL: {} minutes", cachedContent.getRemainingTtl().toMinutes());

        // Log usage metadata if available
        if (cachedContent.getUsageMetadata() != null) {
            var metadata = cachedContent.getUsageMetadata();
            metadata.totalTokenCount().ifPresent(tokens -> log.info("  Total tokens in cache: {}", tokens));
        }
    }
}

package com.jlabs.repo.onboarder.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.jlabs.repo.onboarder.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Serwis odpowiedzialny za zarządzanie cache'owanym kontekstem repozytoriów w Google GenAI.
 * 
 * Wykorzystuje Google GenAI Cached Content API do przechowywania dużych kontekstów
 * repozytoriów (XML z directory tree, hotspots, commits, source code), co:
 * - Redukuje koszty API (cached tokens są 10x tańsze niż input tokens)
 * - Przyspiesza wywołania API (nie trzeba przesyłać dużego kontekstu za każdym razem)
 * - Zachowuje semantykę (cache jest traktowany jak system instruction/context)
 * 
 * Cache jest identyfikowany przez URL repozytorium i automatycznie wygasa po skonfigurowanym TTL.
 * 
 * UWAGA: Wymaga włączenia cached content w konfiguracji:
 * spring.ai.google.genai.chat.enable-cached-content=true
 */
@Service
public class RepositoryCacheService {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryCacheService.class);
    
    private final GoogleGenAiCachedContentService cachedContentService;
    private final AiProperties aiProperties;
    private final boolean cacheEnabled;

    public RepositoryCacheService(            
          GoogleGenAiCachedContentService cachedContentService,
          AiProperties aiProperties) {
        this.cachedContentService = cachedContentService;
        this.aiProperties = aiProperties;
        this.cacheEnabled = cachedContentService != null;
        
        if (!cacheEnabled) {
            logger.warn("GoogleGenAiCachedContentService nie jest dostępny - cache będzie wyłączony. " +
                    "Aby włączyć cache, ustaw: spring.ai.google.genai.chat.enable-cached-content=true");
        } else {
            logger.info("RepositoryCacheService został zainicjalizowany z włączonym cache");
        }
    }

    /**
     * Generuje nazwę cache dla repozytorium na podstawie jego URL.
     * Format: github-user-repo (po sanityzacji URL).
     * 
     * @param repoUrl URL repozytorium Git
     * @return sanityzowana nazwa cache
     */
    public String getCacheNameForRepository(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return "unknown-repo";
        }

        // Usuń protokół (https://, git@)
        String sanitized = repoUrl.replaceAll("^(https?://|git@)", "");
        
        // Usuń .git na końcu
        sanitized = sanitized.replaceAll("\\.git$", "");
        
        // Zamień znaki specjalne na myślniki
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9]+", "-");
        
        // Usuń myślniki na początku i końcu
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        
        // Konwertuj na małe litery
        sanitized = sanitized.toLowerCase();
        
        logger.debug("Cache name dla repo URL '{}': '{}'", repoUrl, sanitized);
        return "v3_" + sanitized;
    }

    /**
     * Sprawdza czy cache dla repozytorium już istnieje i jest aktywny (nie wygasł).
     * 
     * @param repoUrl URL repozytorium Git
     * @return Optional z nazwą cached content jeśli istnieje i jest aktywny, Empty jeśli nie istnieje lub wygasł
     */
    public Optional<String> getCachedContentName(String repoUrl) {
        if (!cacheEnabled) {
            return Optional.empty();
        }
        
        String cacheName = getCacheNameForRepository(repoUrl);
        
        try {
            // Przeszukaj wszystkie cache'e szukając naszego
            List<GoogleGenAiCachedContent> allCaches = cachedContentService.listAll();
            
            for (GoogleGenAiCachedContent cache : allCaches) {
                // Sprawdź czy display name pasuje do naszej nazwy
                if (cache.getDisplayName() != null && cache.getDisplayName().contains(cacheName)) {
                    // Sprawdź czy cache nie wygasł
                    if (!cache.isExpired()) {
                        String fullCacheName = cache.getName();
                        Duration remainingTtl = cache.getRemainingTtl();
                        
                        logger.info("Znaleziono aktywny cache dla repo '{}': '{}', pozostały TTL: {} minut", 
                                repoUrl, fullCacheName, remainingTtl.toMinutes());
                        
                        return Optional.of(fullCacheName);
                    } else {
                        logger.info("Cache dla repo '{}' wygasł, zostanie usunięty i utworzony ponownie", repoUrl);
                        // Usuń wygasły cache
                        cachedContentService.delete(cache.getName());
                    }
                }
            }
            
            logger.info("Nie znaleziono aktywnego cache dla repo '{}'", repoUrl);
            return Optional.empty();
            
        } catch (Exception e) {
            logger.warn("Błąd podczas sprawdzania cache dla repo '{}': {}", repoUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Tworzy nowy cached content dla repozytorium zawierający repository context XML.
     * 
     * Cache będzie zawierał:
     * - System instruction: Repository context XML (directory tree, hotspots, commits, source code)
     * - Display name: Nazwa repozytorium + timestamp
     * - TTL: Skonfigurowany TTL (domyślnie 1 godzina)
     * - Model: Ten sam model co używany do generacji
     * 
     * @param repoUrl URL repozytorium Git
     * @param repositoryContextXml XML zawierający pełny kontekst repozytorium
     * @param model nazwa modelu Google GenAI (np. "gemini-2.5-pro")
     * @return pełna nazwa utworzonego cached content (format: cachedContent/xxx), lub null jeśli cache jest wyłączony
     * @throws RuntimeException gdy nie można utworzyć cache
     */
    public String createCachedContent(String repoUrl, String repositoryContextXml, String model) {
        if (!cacheEnabled) {
            logger.warn("Cache jest wyłączony - nie można utworzyć cached content");
            return null;
        }
        
        String cacheName = getCacheNameForRepository(repoUrl);
        String displayName = String.format("cache-%s-%d", cacheName, System.currentTimeMillis() / 1000);
        
        try {
            logger.info("Tworzenie nowego cache dla repo '{}' (display name: '{}')", repoUrl, displayName);
            
            // Oszacuj liczbę tokenów
            long estimatedTokens = estimateTokenCount(repositoryContextXml);
            logger.info("Szacowana liczba tokenów w cache: ~{}", estimatedTokens);
            
            if (estimatedTokens < 32768) {
                logger.warn("Ostrzeżenie: Szacowana liczba tokenów ({}) jest poniżej minimum 32,768 wymaganego przez Google GenAI Cached Content API. " +
                        "Cache może nie zostać utworzony.", estimatedTokens);
            }
            
            // Utwórz request do cached content
            CachedContentRequest request = CachedContentRequest.builder()
                    .model(model)
                    .contents(List.of(
                            Content.builder()
                                    .role("user")
                                    .parts(List.of(Part.fromText(repositoryContextXml)))
                                    .build()
                    ))
                    .displayName(displayName)
                    .ttl(aiProperties.getChat().getOptions().getRepositoryCacheTtl())
                    .build();
            
            // Utwórz cached content
            GoogleGenAiCachedContent cachedContent = cachedContentService.create(request);
            String fullCacheName = cachedContent.getName();
            
            // Loguj informacje o utworzonym cache
            logCacheCreated(cachedContent, repoUrl);
            
            return fullCacheName;
            
        } catch (Exception e) {
            logger.error("Nie udało się utworzyć cache dla repo '{}': {}", repoUrl, e.getMessage(), e);
            throw new RuntimeException("Nie udało się utworzyć cached content: " + e.getMessage(), e);
        }
    }

    /**
     * Usuwa (invaliduje) cache dla repozytorium.
     * 
     * @param repoUrl URL repozytorium Git
     * @return true jeśli cache został usunięty, false jeśli nie istniał lub cache jest wyłączony
     */
    public boolean invalidateCache(String repoUrl) {
        if (!cacheEnabled) {
            logger.warn("Cache jest wyłączony - nie można usunąć cache");
            return false;
        }
        
        Optional<String> cachedContentName = getCachedContentName(repoUrl);
        
        if (cachedContentName.isPresent()) {
            try {
                boolean deleted = cachedContentService.delete(cachedContentName.get());
                if (deleted) {
                    logger.info("Usunięto cache dla repo '{}'", repoUrl);
                } else {
                    logger.warn("Nie udało się usunąć cache dla repo '{}'", repoUrl);
                }
                return deleted;
            } catch (Exception e) {
                logger.error("Błąd podczas usuwania cache dla repo '{}': {}", repoUrl, e.getMessage(), e);
                return false;
            }
        }
        
        logger.info("Cache dla repo '{}' nie istnieje, nie ma czego usuwać", repoUrl);
        return false;
    }

    /**
     * Czyści wszystkie wygasłe cache'e z systemu.
     * 
     * @return liczba usuniętych cache'y, lub 0 jeśli cache jest wyłączony
     */
    public int cleanupExpiredCaches() {
        if (!cacheEnabled) {
            logger.warn("Cache jest wyłączony - nie można wyczyścić cache");
            return 0;
        }
        
        try {
            int removedCount = cachedContentService.cleanupExpired();
            logger.info("Wyczyszczono {} wygasłych cache'y", removedCount);
            return removedCount;
        } catch (Exception e) {
            logger.error("Błąd podczas czyszczenia wygasłych cache'y: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Szacuje liczbę tokenów w tekście.
     * Dla modeli Gemini używa przybliżenia: 1 token ≈ 3.5 znaków.
     */
    private long estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.round(text.length() / 3.5);
    }

    /**
     * Loguje szczegółowe informacje o utworzonym cache.
     */
    private void logCacheCreated(GoogleGenAiCachedContent cachedContent, String repoUrl) {
        logger.info("Utworzono cache dla repo '{}': '{}'", repoUrl, cachedContent.getName());
        logger.info("  Display name: {}", cachedContent.getDisplayName());
        logger.info("  Model: {}", cachedContent.getModel());
        logger.info("  TTL: {} minut", cachedContent.getRemainingTtl().toMinutes());
        
        // Loguj usage metadata jeśli dostępne
        if (cachedContent.getUsageMetadata() != null) {
            var metadata = cachedContent.getUsageMetadata();
            metadata.totalTokenCount().ifPresent(tokens -> 
                logger.info("  Total tokens w cache: {}", tokens));
        }
    }
}


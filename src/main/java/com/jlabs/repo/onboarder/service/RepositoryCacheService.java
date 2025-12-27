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
 * Serwis odpowiedzialny za zarządzanie cache'owanym kontekstem repozytoriów w
 * Google GenAI.
 * 
 * Wykorzystuje Google GenAI Cached Content API do przechowywania dużych
 * kontekstów
 * repozytoriów (XML z directory tree, hotspots, commits, source code), co:
 * - Redukuje koszty API (cached tokens są 10x tańsze niż input tokens)
 * - Przyspiesza wywołania API (nie trzeba przesyłać dużego kontekstu za każdym
 * razem)
 * - Zachowuje semantykę (cache jest traktowany jak system instruction/context)
 * 
 * Cache jest identyfikowany przez URL repozytorium i automatycznie wygasa po
 * skonfigurowanym TTL.
 * 
 * UWAGA: Wymaga włączenia cached content w konfiguracji:
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
            log.warn("GoogleGenAiCachedContentService nie jest dostępny - cache będzie wyłączony. " +
                    "Aby włączyć cache, ustaw: spring.ai.google.genai.chat.enable-cached-content=true");
        } else {
            log.info("RepositoryCacheService został zainicjalizowany z włączonym cache");
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

        log.debug("Cache name dla repo URL '{}': '{}'", repoUrl, sanitized);
        return sanitized;
    }

    /**
     * Sprawdza czy cache dla repozytorium już istnieje i jest aktywny (nie wygasł).
     * 
     * @param repoUrl URL repozytorium Git
     * @return Optional z nazwą cached content jeśli istnieje i jest aktywny, Empty
     *         jeśli nie istnieje lub wygasł
     */
    public Optional<String> getCachedContentName(String repoUrl) {
        if (!isCacheEnabled()) {
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

                        log.info("Znaleziono aktywny cache dla repo '{}': '{}', pozostały TTL: {} minut",
                                repoUrl, fullCacheName, remainingTtl.toMinutes());

                        return Optional.of(fullCacheName);
                    } else {
                        log.info("Cache dla repo '{}' wygasł, zostanie usunięty i utworzony ponownie", repoUrl);
                        // Usuń wygasły cache
                        cachedContentService.delete(cache.getName());
                    }
                }
            }

            log.info("Nie znaleziono aktywnego cache dla repo '{}'", repoUrl);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Błąd podczas sprawdzania cache dla repo '{}': {}", repoUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Tworzy nowy cached content dla repozytorium zawierający repository context
     * XML.
     * 
     * Cache będzie zawierał:
     * - System instruction: Repository context XML (directory tree, hotspots,
     * commits, source code)
     * - Display name: Nazwa repozytorium + timestamp
     * - TTL: Skonfigurowany TTL (domyślnie 1 godzina)
     * - Model: Ten sam model co używany do generacji
     * 
     * @param repoUrl              URL repozytorium Git
     * @param repositoryContextXml XML zawierający pełny kontekst repozytorium
     * @param model                nazwa modelu Google GenAI (np. "gemini-2.5-pro")
     * @return pełna nazwa utworzonego cached content (format: cachedContent/xxx),
     *         lub null jeśli cache jest wyłączony
     * @throws RuntimeException gdy nie można utworzyć cache
     */
    public String createCachedContent(String repoUrl, String repositoryContextXml, String model) {
        if (!isCacheEnabled()) {
            log.warn("Cache jest wyłączony - nie można utworzyć cached content");
            return null;
        }

        String cacheName = getCacheNameForRepository(repoUrl);

        try {
            log.info("Tworzenie nowego cache dla repo '{}' (display name: '{}')", repoUrl, cacheName);

            // Oszacuj liczbę tokenów
            long estimatedTokens = estimateTokenCount(repositoryContextXml);
            log.info("Szacowana liczba tokenów w cache: ~{}", estimatedTokens);

            if (estimatedTokens < 32768) {
                log.warn(
                        "Ostrzeżenie: Szacowana liczba tokenów ({}) jest poniżej minimum 32,768 wymaganego przez Google GenAI Cached Content API. "
                                +
                                "Cache może nie zostać utworzony.",
                        estimatedTokens);
            }

            // Utwórz request do cached content
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

            // Utwórz cached content
            GoogleGenAiCachedContent cachedContent = cachedContentService.create(request);
            String fullCacheName = cachedContent.getName();

            // Loguj informacje o utworzonym cache
            logCacheCreated(cachedContent, repoUrl);

            return fullCacheName;

        } catch (Exception e) {
            log.error("Nie udało się utworzyć cache dla repo '{}': {}", repoUrl, e.getMessage(), e);
            throw new RuntimeException("Nie udało się utworzyć cached content: " + e.getMessage(), e);
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
        log.info("Utworzono cache dla repo '{}': '{}'", repoUrl, cachedContent.getName());
        log.info("  Display name: {}", cachedContent.getDisplayName());
        log.info("  Model: {}", cachedContent.getModel());
        log.info("  TTL: {} minut", cachedContent.getRemainingTtl().toMinutes());

        // Loguj usage metadata jeśli dostępne
        if (cachedContent.getUsageMetadata() != null) {
            var metadata = cachedContent.getUsageMetadata();
            metadata.totalTokenCount().ifPresent(tokens -> log.info("  Total tokens w cache: {}", tokens));
        }
    }
}

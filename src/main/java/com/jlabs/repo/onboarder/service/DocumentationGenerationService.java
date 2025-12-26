package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentationGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationGenerationService.class);

    private final PromptConstructionService promptConstructionService;
    private final RepositoryCacheService repositoryCacheService;
    private final AiProperties aiProperties;
    private final List<DocumentGenerationService> documentGenerators;

    public DocumentationGenerationService(
            PromptConstructionService promptConstructionService,
            RepositoryCacheService repositoryCacheService,
            AiProperties aiProperties,
            List<DocumentGenerationService> documentGenerators) {
        this.promptConstructionService = promptConstructionService;
        this.repositoryCacheService = repositoryCacheService;
        this.aiProperties = aiProperties;
        this.documentGenerators = documentGenerators;
    }

    /**
     * Generuje dokumentację dla repozytorium używając AI modelu z wykorzystaniem
     * cache.
     * <p>
     * Flow:
     * 1. Zapewnia dostępność cache z repository context (sprawdza/tworzy)
     * 2. Uruchamia generatory dla poszczególnych dokumentów (AI Context, README,
     * Refactoring, DDD)
     * 3. Zwraca wynik
     * <p>
     * Cache zawiera repository context XML (directory tree, hotspots, commits,
     * source code)
     * i jest identyfikowany przez URL repozytorium. Automatycznie wygasa po
     * skonfigurowanym TTL.
     * Reużycie cache dla wielu dokumentów oszczędza koszty i czas.
     *
     * @param report         raport z analizy Git repozytorium
     * @param repoRoot       ścieżka do katalogu głównego repozytorium
     * @param debugOutputDir katalog do zapisu plików debugowania
     * @return wynik generacji dokumentacji
     */
    public DocumentationResult generateDocumentation(GitReport report, Path repoRoot, Path debugOutputDir) {
        logger.info("Rozpoczęcie generacji dokumentacji dla repo: {}", report.repo.url);

        // 1. Zapewnij dostępność cache (jeden raz dla wszystkich dokumentów)
        String repositoryContentCacheName = ensureRepositoryContentCache(report, repoRoot, debugOutputDir);

        DocumentationResult result = new DocumentationResult();

        // 2. Uruchom wszystkie generatory
        for (DocumentGenerationService generator : documentGenerators) {
            generator.generate(result, report, repoRoot, debugOutputDir, repositoryContentCacheName);
        }

        logger.info("Dokumentacja wygenerowana pomyślnie");
        logger.debug("AI Context File długość: {} znaków",
                result.getAiContextFile() != null ? result.getAiContextFile().length() : 0);
        logger.debug("README długość: {} znaków",
                result.getReadme() != null ? result.getReadme().length() : 0);

        return result;
    }

    /**
     * Zapewnia dostępność cache z repository context.
     * Sprawdza czy cache istnieje, jeśli nie - próbuje utworzyć.
     *
     * @param report         raport Git
     * @param repoRoot       ścieżka do repozytorium
     * @param debugOutputDir katalog debugowania
     * @return nazwa cache (pełna nazwa w formacie cachedContent/xxx) lub null jeśli
     *         cache niedostępny
     */
    private String ensureRepositoryContentCache(GitReport report, Path repoRoot, Path debugOutputDir) {
        String repoUrl = report.repo.url;
        String model = aiProperties.getChat().getOptions().getModel();

        // 1. Sprawdź czy cache dla repozytorium już istnieje
        Optional<String> cachedContentName = repositoryCacheService.getCachedContentName(repoUrl);

        if (cachedContentName.isPresent()) {
            // Cache istnieje - zwróć jego nazwę
            logger.info("Używanie istniejącego cache dla repo: {}", repoUrl);
            return cachedContentName.get();
        }

        // 2. Cache nie istnieje - spróbuj utworzyć nowy
        logger.info("Cache nie istnieje dla repo: {}, próba utworzenia nowego...", repoUrl);

        // Przygotuj repository context XML
        String repoContextXml = promptConstructionService.prepareRepositoryContext(report, repoRoot);
        saveDebugFile(debugOutputDir, "ai_context_prompt_debug.txt", repoContextXml);

        // Spróbuj utworzyć cached content
        String newCacheName = repositoryCacheService.createCachedContent(repoUrl, repoContextXml, model);

        if (newCacheName != null) {
            logger.info("Cache został utworzony pomyślnie");
            return newCacheName;
        } else {
            logger.info("Cache nie jest dostępny, będzie używany tradycyjny prompt z pełnym kontekstem");
            return null;
        }
    }

    /**
     * Zapisuje zawartość do pliku w celach debugowania.
     * Metoda nie przerywa głównego flow w przypadku błędów - tylko loguje
     * ostrzeżenia.
     *
     * @param outputDir katalog docelowy
     * @param filename  nazwa pliku do zapisania
     * @param content   zawartość do zapisania
     */
    private void saveDebugFile(Path outputDir, String filename, String content) {
        try {
            Path debugFile = outputDir.resolve(filename);
            Files.writeString(debugFile, content, StandardCharsets.UTF_8);
            logger.debug("Zapisano plik debugowania: {}", debugFile);
        } catch (Exception e) {
            logger.warn("Nie udało się zapisać pliku debugowania {}: {}", filename, e.getMessage());
        }
    }
}

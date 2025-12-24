package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class DocumentationGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationGenerationService.class);

    private final ChatModelClient chatModelClient;
    private final PromptConstructionService promptConstructionService;
    private final RepositoryCacheService repositoryCacheService;
    private final DocumentationPostProcessingService documentationPostProcessingService;
    private final AiProperties aiProperties;

    public DocumentationGenerationService(
            ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService,
            RepositoryCacheService repositoryCacheService,
            DocumentationPostProcessingService documentationPostProcessingService,
            AiProperties aiProperties) {
        this.chatModelClient = chatModelClient;
        this.promptConstructionService = promptConstructionService;
        this.repositoryCacheService = repositoryCacheService;
        this.documentationPostProcessingService = documentationPostProcessingService;
        this.aiProperties = aiProperties;
    }

    /**
     * Generuje dokumentację dla repozytorium używając AI modelu z wykorzystaniem
     * cache.
     * <p>
     * Flow:
     * 1. Zapewnia dostępność cache z repository context (sprawdza/tworzy)
     * 2. Generuje AI Context File używając cache
     * 3. Generuje README używając tego samego cache
     * 4. Zwraca wynik z oboma dokumentami
     * <p>
     * Cache zawiera repository context XML (directory tree, hotspots, commits,
     * source code)
     * i jest identyfikowany przez URL repozytorium. Automatycznie wygasa po
     * skonfigurowanym TTL.
     * Reużycie cache dla wielu dokumentów oszczędza koszty i czas.
     *
     * @param report   raport z analizy Git repozytorium
     * @param repoRoot ścieżka do katalogu głównego repozytorium
     * @return wynik generacji dokumentacji zawierający README, Architecture i
     *         Context File
     */
    public DocumentationResult generateDocumentation(GitReport report, Path repoRoot, Path debugOutputDir) {
        logger.info("Rozpoczęcie generacji dokumentacji dla repo: {}", report.repo.url);

        // 1. Zapewnij dostępność cache (jeden raz dla obu dokumentów)
        // Pass debugOutputDir to ensureRepositoryContentCache
        String repositoryContentCacheName = ensureRepositoryContentCache(report, repoRoot, debugOutputDir);

        // 2. Wygeneruj AI Context File
        String aiContextFile = generateSingleDocument(
                repositoryContentCacheName,
                PromptConstructionService.AI_CONTEXT_PROMPT_TEMPLATE_PATH,
                PromptConstructionService.AI_CONTEXT_DOCUMENTATION_TEMPLATE_PATH,
                report,
                repoRoot,
                debugOutputDir); // Pass debugOutputDir

        // 2.1 Post-processing - dodaj sekcję Project Structure
        aiContextFile = documentationPostProcessingService.enhance(aiContextFile, report);

        saveDebugFile(debugOutputDir, "generated_context_file_debug.md", aiContextFile);

        // 3. Wygeneruj README
        String readme = generateSingleDocument(
                repositoryContentCacheName,
                PromptConstructionService.README_PROMPT_TEMPLATE_PATH,
                PromptConstructionService.README_DOCUMENTATION_TEMPLATE_PATH,
                report,
                repoRoot,
                debugOutputDir); // Pass debugOutputDir
        saveDebugFile(debugOutputDir, "generated_readme_file_debug.md", readme);

        // 3. Wygeneruj README
        String refactorings = generateSingleDocument(
                repositoryContentCacheName,
                PromptConstructionService.REFACTORING_PROMPT_TEMPLATE_PATH,
                PromptConstructionService.REFACTORING_DOCUMENTATION_TEMPLATE_PATH,
                report,
                repoRoot,
                debugOutputDir); // Pass debugOutputDir
        saveDebugFile(debugOutputDir, "generated_refactoring_file_debug.md", refactorings);

        // 5. Wygeneruj DDD Refactoring
        String dddRefactorings = generateSingleDocument(
                repositoryContentCacheName,
                PromptConstructionService.DDD_REFACTORING_PROMPT_TEMPLATE_PATH,
                PromptConstructionService.DDD_REFACTORING_DOCUMENTATION_TEMPLATE_PATH,
                report,
                repoRoot,
                debugOutputDir); // Pass debugOutputDir
        saveDebugFile(debugOutputDir, "generated_ddd_refactoring_file_debug.md", dddRefactorings);

        // 6. Złóż wynik
        DocumentationResult result = new DocumentationResult();
        result.setAiContextFile(aiContextFile);
        result.setReadme(readme);
        result.setRefactorings(refactorings);
        result.setDddRefactorings(dddRefactorings);

        logger.info("Dokumentacja wygenerowana pomyślnie");
        logger.debug("AI Context File długość: {} znaków",
                aiContextFile != null ? aiContextFile.length() : 0);
        logger.debug("README długość: {} znaków",
                readme != null ? readme.length() : 0);

        return result;
    }

    /**
     * Zapewnia dostępność cache z repository context.
     * Sprawdza czy cache istnieje, jeśli nie - próbuje utworzyć.
     *
     * @param report   raport Git
     * @param repoRoot ścieżka do repozytorium
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
     * Generuje pojedynczy dokument używając cache (jeśli dostępny) lub pełnego
     * promptu.
     *
     * @param cacheName          nazwa cache lub null jeśli cache niedostępny
     * @param promptTemplatePath ścieżka do template promptu
     * @param docTemplatePath    ścieżka do template dokumentu
     * @param report             raport Git (używany tylko gdy cacheName == null)
     * @param repoRoot           ścieżka do repo (używany tylko gdy cacheName ==
     *                           null)
     * @return wygenerowany dokument Markdown (po parsowaniu)
     */
    private String generateSingleDocument(
            String cacheName,
            String promptTemplatePath,
            String docTemplatePath,
            GitReport report,
            Path repoRoot,
            Path debugOutputDir) {

        String promptText;
        GoogleGenAiChatOptions chatOptions;

        if (cacheName != null) {
            // Cache dostępny - użyj cached content
            logger.debug("Generowanie dokumentu z użyciem cache: {}", cacheName);

            // Konstruuj prompt z referencją do cache
            promptText = promptConstructionService.constructPromptWithCache(
                    cacheName, promptTemplatePath, docTemplatePath);

            // Utwórz opcje z referencją do cached content
            chatOptions = GoogleGenAiChatOptions.builder()
                    .useCachedContent(true)
                    .cachedContentName(cacheName)
                    .build();

        } else {
            // Cache niedostępny - użyj pełnego promptu
            logger.debug("Generowanie dokumentu bez cache (pełny prompt)");

            // Konstruuj pełny prompt z repository context
            promptText = promptConstructionService.constructPrompt(
                    report, repoRoot, promptTemplatePath, docTemplatePath);

            chatOptions = null; // Użyj domyślnych opcji
        }

        // Zapisz prompt do pliku debugowania
        String debugFilename = createDebugPromptFilename(promptTemplatePath);
        saveDebugFile(debugOutputDir, debugFilename, promptText);

        // Wywołaj API
        String responseText = chatModelClient.call(promptText, chatOptions);

        // Parsuj i zwróć czysty Markdown
        return extractMarkdownFromCodeBlock(responseText);
    }

    /**
     * Wyciąga czystą treść z bloku kodu markdown, jeśli odpowiedź została w niego
     * opakowana.
     * Obsługuje bloki typu ```markdown, ``` lub po prostu zwraca tekst, jeśli nie
     * ma bloków.
     *
     * @param responseText tekst odpowiedzi z API
     * @return wyczyszczona treść markdown
     */
    private String extractMarkdownFromCodeBlock(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return responseText;
        }

        String trimmed = responseText.trim();

        // 0. Usuń blok <analysis>...</analysis> jeśli występuje na początku
        if (trimmed.startsWith("<analysis>")) {
            int endTagIndex = trimmed.indexOf("</analysis>");
            if (endTagIndex != -1) {
                logger.debug("Usuwanie bloku <analysis> z odpowiedzi (długość: {})", endTagIndex + 11);
                trimmed = trimmed.substring(endTagIndex + "</analysis>".length()).trim();
            }
        }

        // Sprawdź czy zaczyna się od ```markdown, ```json lub po prostu ```
        String[] prefixes = { "```markdown", "```json", "```" };
        for (String prefix : prefixes) {
            if (trimmed.startsWith(prefix)) {
                int start = trimmed.indexOf(prefix) + prefix.length();
                int end = trimmed.lastIndexOf("```");

                if (end > start) {
                    String content = trimmed.substring(start, end).trim();
                    logger.debug("Wyciągnięto treść z bloku kodu {}, długość: {} znaków", prefix, content.length());
                    return content;
                }
            }
        }

        return trimmed;
    }

    /**
     * Zapisuje zawartość do pliku w katalogu working_directory w celach
     * debugowania.
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

    private String createDebugPromptFilename(String templatePath) {
        String filename = templatePath;
        int lastSlash = templatePath.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = templatePath.substring(lastSlash + 1);
        }

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            filename = filename.substring(0, dotIndex);
        }

        return filename + "_prompt_debug.txt";
    }
}

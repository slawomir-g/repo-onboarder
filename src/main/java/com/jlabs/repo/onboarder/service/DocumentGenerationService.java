package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstract base class for generating specific types of documentation.
 * Defines the template method for the generation process.
 */
@Slf4j
public abstract class DocumentGenerationService {

    protected final ChatModelClient chatModelClient;
    protected final PromptConstructionService promptConstructionService;

    protected DocumentGenerationService(ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService) {
        this.chatModelClient = chatModelClient;
        this.promptConstructionService = promptConstructionService;
    }

    /**
     * Template method to generate a document, save debug info, and add to result.
     */
    public void generate(DocumentationResult result, GitReport report, Path repoRoot, Path debugOutputDir,
            String cacheName, String targetLanguage) {
        // 1. Construct prompt
        String promptText = constructPrompt(cacheName, getPromptTemplatePath(), getDocTemplatePath(), report, repoRoot,
                targetLanguage);

        // 2. Save prompt to debug file
        saveDebugFile(debugOutputDir, createDebugPromptFilename(getPromptTemplatePath()), promptText);

        // 3. Call AI
        GoogleGenAiChatOptions chatOptions = createChatOptions(cacheName);
        String responseText = chatModelClient.call(promptText, chatOptions);
        String content = extractMarkdownFromCodeBlock(responseText);

        // 4. Post-processing (Hook)
        content = postProcess(content, report);

        // 5. Save generated content to debug file
        saveDebugFile(debugOutputDir, getOutputDebugFileName(), content);

        // 6. Add to result
        addToResult(result, content);
    }

    // Abstract methods to be implemented by concrete classes
    protected abstract String getPromptTemplatePath();

    protected abstract String getDocTemplatePath();

    protected abstract String getOutputDebugFileName();

    protected void addToResult(DocumentationResult result, String content) {
        result.addDocument(getDocumentType(), content);
    }

    protected abstract String getDocumentType();

    // Hook for post-processing (default implementation does nothing)
    protected String postProcess(String content, GitReport report) {
        return content;
    }

    // Helper methods

    private String constructPrompt(String cacheName, String promptTemplatePath, String docTemplatePath,
            GitReport report, Path repoRoot, String targetLanguage) {
        if (cacheName != null) {
            log.debug("Generowanie dokumentu z użyciem cache: {}", cacheName);
            return promptConstructionService.constructPromptWithCache(cacheName, promptTemplatePath, docTemplatePath,
                    targetLanguage);
        } else {
            log.debug("Generowanie dokumentu bez cache (pełny prompt)");
            return promptConstructionService.constructPrompt(report, repoRoot, promptTemplatePath, docTemplatePath,
                    targetLanguage);
        }
    }

    private GoogleGenAiChatOptions createChatOptions(String cacheName) {
        if (cacheName != null) {
            return GoogleGenAiChatOptions.builder()
                    .useCachedContent(true)
                    .cachedContentName(cacheName)
                    .build();
        }
        return null;
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

    protected void saveDebugFile(Path outputDir, String filename, String content) {
        try {
            Path debugFile = outputDir.resolve(filename);
            Files.writeString(debugFile, content, StandardCharsets.UTF_8);
            log.debug("Zapisano plik debugowania: {}", debugFile);
        } catch (Exception e) {
            log.warn("Nie udało się zapisać pliku debugowania {}: {}", filename, e.getMessage());
        }
    }

    private String extractMarkdownFromCodeBlock(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return responseText;
        }

        String trimmed = responseText.trim();

        // 0. Usuń blok <analysis>...</analysis> jeśli występuje na początku
        if (trimmed.startsWith("<analysis>")) {
            int endTagIndex = trimmed.indexOf("</analysis>");
            if (endTagIndex != -1) {
                log.debug("Usuwanie bloku <analysis> z odpowiedzi (długość: {})", endTagIndex + 11);
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
                    log.debug("Wyciągnięto treść z bloku kodu {}, długość: {} znaków", prefix, content.length());
                    return content;
                }
            }
        }

        return trimmed;
    }
}

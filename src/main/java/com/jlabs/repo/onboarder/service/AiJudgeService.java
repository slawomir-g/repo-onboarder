package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiJudgeService {

    private final ChatModelClient chatModelClient;
    private final PromptConstructionService promptConstructionService;

    private static final String JUDGE_PROMPT_TEMPLATE_PATH = "prompts/judge-validation-template.md";
    private static final String DOCUMENTATION_TYPE = "Validation Report";
    private static final String OUTPUT_DEBUG_FILENAME = "generated_validation_report_debug.md";

    /**
     * Validates usage of generated documents against repository context.
     *
     * @param result         Generated documentation result containing all documents
     * @param report         Git analysis report
     * @param repoRoot       Path to repo root
     * @param debugOutputDir Directory for debug files
     * @param cacheName      Name of cached content (optional)
     * @param targetLanguage Target language for the report
     */
    public void validate(DocumentationResult result, GitReport report, Path repoRoot, Path debugOutputDir,
            String cacheName, String targetLanguage) {

        log.info("Starting AI Judge validation...");

        // 1. Aggregate generated documentation
        String aggregatedDocs = aggregateGeneratedDocs(result);
        if (aggregatedDocs.isBlank()) {
            log.warn("No documentation generated to validate.");
            return;
        }

        String promptTemplatePath = "prompts/ai-context-prompt-template.md"; // Reusing generic wrapper

        String instructionTemplate = loadResource(JUDGE_PROMPT_TEMPLATE_PATH);
        String finalInstructions = instructionTemplate.replace("$GENERATED_DOCUMENTATION_PLACEHOLDER$", aggregatedDocs);

        String promptText;
        if (cacheName != null) {
            promptText = promptConstructionService.constructPromptWithCacheAndContent(
                    cacheName,
                    promptTemplatePath,
                    finalInstructions,
                    targetLanguage);
        } else {
            promptText = promptConstructionService.constructPromptWithContent(
                    promptConstructionService.prepareRepositoryContext(report, repoRoot),
                    promptTemplatePath,
                    finalInstructions,
                    targetLanguage);
        }

        // 4. Save debug
        saveDebugFile(debugOutputDir, "judge_prompt_debug.txt", promptText);

        // 5. Call AI
        GoogleGenAiChatOptions chatOptions = createChatOptions(cacheName);
        String responseText = chatModelClient.call(promptText, chatOptions);
        String content = extractMarkdownFromCodeBlock(responseText);

        // 6. Save debug output
        saveDebugFile(debugOutputDir, OUTPUT_DEBUG_FILENAME, content);

        // 7. Add to result
        result.addDocument(DOCUMENTATION_TYPE, content);

        log.info("AI Judge validation completed.");
    }

    private String aggregateGeneratedDocs(DocumentationResult result) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : result.getDocuments().entrySet()) {
            sb.append("--- Document: ").append(entry.getKey()).append(" ---\n");
            sb.append(entry.getValue()).append("\n\n");
        }
        return sb.toString();
    }

    // Helper to load resource manually since PromptConstructionService usually
    // loads it
    private String loadResource(String path) {
        try {
            Path file = Path.of(getClass().getClassLoader().getResource(path).toURI());
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fallback for classpath resource reading if filesystem fails (e.g. inside JAR)
            try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is == null)
                    throw new RuntimeException("Resource not found: " + path);
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to load resource: " + path, ex);
            }
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

    private void saveDebugFile(Path outputDir, String filename, String content) {
        try {
            Path debugFile = outputDir.resolve(filename);
            Files.writeString(debugFile, content, StandardCharsets.UTF_8);
            log.debug("Saved debug file: {}", debugFile);
        } catch (Exception e) {
            log.warn("Failed to save debug file {}: {}", filename, e.getMessage());
        }
    }

    private String extractMarkdownFromCodeBlock(String responseText) {
        // Reusing logic from DocumentGenerationService or similar.
        // For simplicity duplicating strictly necessary parts logic or could make a
        // Util class.
        // Given instructions, I'll duplicate the simple extraction logic to avoid
        // creating new classes if not asked.
        if (responseText == null)
            return "";
        String trimmed = responseText.trim();
        if (trimmed.startsWith("```markdown")) {
            int end = trimmed.lastIndexOf("```");
            if (end > 11) {
                return trimmed.substring(11, end).trim();
            }
        }
        if (trimmed.startsWith("```")) {
            int end = trimmed.lastIndexOf("```");
            if (end > 3) {
                return trimmed.substring(3, end).trim();
            }
        }
        return trimmed;
    }
}

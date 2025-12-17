package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.markdown.*;
import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Serwis odpowiedzialny za konstrukcję promptu dla AI modelu.
 * Łączy wygenerowane payloady z template'ami XML i Markdown,
 * tworząc finalny prompt gotowy do wysłania do Gemini API.
 * 
 * Zgodnie z planem optymalizacji, używa bezpośrednio metod generate()
 * z klas PayloadWriter zamiast czytać z plików.
 */
@Service
public class PromptConstructionService {

    private static final String REPOSITORY_CONTEXT_TEMPLATE_PATH = "prompts/repository-context-payload-template.xml";
    private static final String AI_CONTEXT_PROMPT_TEMPLATE_PATH = "prompts/ai-context-prompt-template.md";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final DirectoryTreePayloadWriter directoryTreePayloadWriter;
    private final HotspotsPayloadWriter hotspotsPayloadWriter;
    private final CommitHistoryPayloadWriter commitHistoryPayloadWriter;
    private final SourceCodeCorpusPayloadWriter sourceCodeCorpusPayloadWriter;

    public PromptConstructionService(
            DirectoryTreePayloadWriter directoryTreePayloadWriter,
            HotspotsPayloadWriter hotspotsPayloadWriter,
            CommitHistoryPayloadWriter commitHistoryPayloadWriter,
            SourceCodeCorpusPayloadWriter sourceCodeCorpusPayloadWriter) {
        this.directoryTreePayloadWriter = directoryTreePayloadWriter;
        this.hotspotsPayloadWriter = hotspotsPayloadWriter;
        this.commitHistoryPayloadWriter = commitHistoryPayloadWriter;
        this.sourceCodeCorpusPayloadWriter = sourceCodeCorpusPayloadWriter;
    }

    /**
     * Konstruuje finalny prompt dla AI modelu na podstawie GitReport i repozytorium.
     * 
     * @param report raport z analizy Git repozytorium
     * @param repoRoot ścieżka do katalogu głównego repozytorium
     * @return finalny prompt jako String gotowy do wysłania do API
     * @throws PromptConstructionException gdy nie można wczytać template'ów lub wystąpi błąd podczas konstrukcji
     */
    public String constructPrompt(GitReport report, Path repoRoot) {
        try {
            // Wczytaj template'y z resources
            String repositoryContextTemplate = loadTemplate(REPOSITORY_CONTEXT_TEMPLATE_PATH);
            String aiContextPromptTemplate = loadTemplate(AI_CONTEXT_PROMPT_TEMPLATE_PATH);

            // Wygeneruj payloady używając metod generate() z PayloadWriter
            String directoryTreePayload = directoryTreePayloadWriter.generate(report);
            String hotspotsPayload = hotspotsPayloadWriter.generate(report);
            String commitHistoryPayload = commitHistoryPayloadWriter.generate(report);
            String sourceCodeCorpusPayload = sourceCodeCorpusPayloadWriter.generate(report, repoRoot);

            // Wyciągnij nazwę projektu z URL (np. "repo-onboarder" z "https://github.com/user/repo-onboarder.git")
            String projectName = extractProjectName(report.repo.url);

            // Wypełnij placeholdery w XML template
            String repositoryContextXml = repositoryContextTemplate
                    .replace("{PROJECT_NAME_PAYLOAD_PLACEHOLDER}", projectName)
                    .replace("{ANALYSIS_TIMESTAMP_PAYLOAD_PLACEHOLDER}", 
                            TIMESTAMP_FORMATTER.format(report.generatedAt))
                    .replace("{BRANCH_PAYLOAD_PLACEHOLDER}", 
                            report.repo.branch != null ? report.repo.branch : "main")
                    .replace("{DIRECTORY_TREE_PAYLOAD_PLACEHOLDER}", directoryTreePayload)
                    .replace("{HOTSPOTS_PAYLOAD_PLACEHOLDER}", hotspotsPayload)
                    .replace("{COMMIT_HISTORY_PAYLOAD_PLACEHOLDER}", commitHistoryPayload)
                    .replace("{SOURCE_CODE_CORPUS_PAYLOAD_PLACEHOLDER}", sourceCodeCorpusPayload);

            // Wypełnij placeholder w prompt template
            String finalPrompt = aiContextPromptTemplate
                    .replace("{REPOSITORY_CONTEXT_PAYLOAD_PLACEHOLDER}", repositoryContextXml);

            return finalPrompt;

        } catch (IOException e) {
            throw new PromptConstructionException(
                    "Nie można wczytać template'ów z resources: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PromptConstructionException(
                    "Błąd podczas konstrukcji promptu: " + e.getMessage(), e);
        }
    }

    /**
     * Wczytuje template z resources jako String.
     * 
     * @param resourcePath ścieżka do zasobu w classpath (np. "prompts/template.xml")
     * @return zawartość pliku jako String
     * @throws IOException gdy nie można wczytać pliku
     */
    private String loadTemplate(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IOException("Template nie istnieje: " + resourcePath);
        }
        try (var inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    /**
     * Wyciąga nazwę projektu z URL repozytorium Git.
     * Przykłady:
     * - "https://github.com/user/repo.git" -> "repo"
     * - "https://github.com/user/repo-onboarder.git" -> "repo-onboarder"
     * - "git@github.com:user/repo.git" -> "repo"
     * 
     * @param repoUrl URL repozytorium
     * @return nazwa projektu lub "unknown-project" jeśli nie można wyciągnąć
     */
    private String extractProjectName(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return "unknown-project";
        }

        // Usuń .git na końcu jeśli istnieje
        String url = repoUrl.replaceAll("\\.git$", "");

        // Wyciągnij ostatnią część ścieżki (nazwa repo)
        int lastSlash = Math.max(url.lastIndexOf('/'), url.lastIndexOf(':'));
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }

        return "unknown-project";
    }

    /**
     * Wyjątek rzucany gdy wystąpi błąd podczas konstrukcji promptu.
     */
    public static class PromptConstructionException extends RuntimeException {
        public PromptConstructionException(String message) {
            super(message);
        }

        public PromptConstructionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


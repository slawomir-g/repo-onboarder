package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.markdown.*;
import com.jlabs.repo.onboarder.model.GitReport;
import com.jlabs.repo.onboarder.service.exceptions.PromptConstructionException;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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

    private static final char PLACEHOLDER_TOKEN = '$';
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
     * Używa PromptTemplate z Spring AI do bezpiecznego i profesjonalnego zarządzania template'ami.
     * 
     * @param report raport z analizy Git repozytorium
     * @param repoRoot ścieżka do katalogu głównego repozytorium
     * @return finalny prompt jako String gotowy do wysłania do API
     * @throws PromptConstructionException gdy nie można wczytać template'ów lub wystąpi błąd podczas konstrukcji
     */
    public String constructPrompt(GitReport report, Path repoRoot) {
        try {
          
            String repositoryContextXml = preapreRepositoryContext(report, repoRoot);
            
            PromptTemplate finalPromptTemplate = PromptTemplate.builder()
                    .renderer(StTemplateRenderer.builder()
                            .startDelimiterToken(PLACEHOLDER_TOKEN)
                            .endDelimiterToken(PLACEHOLDER_TOKEN)
                            .build())
                    .resource(new ClassPathResource(AI_CONTEXT_PROMPT_TEMPLATE_PATH))
                    .build();
            
            String finalPrompt = finalPromptTemplate.render(Map.of(
                    "REPOSITORY_CONTEXT_PAYLOAD_PLACEHOLDER", repositoryContextXml
            ));

            return finalPrompt;
        } catch (Exception e) {
            throw new PromptConstructionException(
                    "Błąd podczas konstrukcji promptu: " + e.getMessage(), e);
        }
    }

    private String preapreRepositoryContext(GitReport report, Path repoRoot) {
        String directoryTreePayload = directoryTreePayloadWriter.generate(report);
        String hotspotsPayload = hotspotsPayloadWriter.generate(report);
        String commitHistoryPayload = commitHistoryPayloadWriter.generate(report);
        String sourceCodeCorpusPayload = sourceCodeCorpusPayloadWriter.generate(report, repoRoot);
        String projectName = extractProjectName(report.repo.url);

        PromptTemplate repositoryContextTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                            .startDelimiterToken(PLACEHOLDER_TOKEN)
                        .endDelimiterToken(PLACEHOLDER_TOKEN)
                        .build())
                .resource(new ClassPathResource(REPOSITORY_CONTEXT_TEMPLATE_PATH))
                .build();
        
        String repositoryContextXml = repositoryContextTemplate.render(Map.of(
                "PROJECT_NAME_PAYLOAD_PLACEHOLDER", projectName,
                "ANALYSIS_TIMESTAMP_PAYLOAD_PLACEHOLDER", 
                        TIMESTAMP_FORMATTER.format(report.generatedAt),
                "BRANCH_PAYLOAD_PLACEHOLDER", 
                        report.repo.branch != null ? report.repo.branch : "main",
                "DIRECTORY_TREE_PAYLOAD_PLACEHOLDER", directoryTreePayload,
                "HOTSPOTS_PAYLOAD_PLACEHOLDER", hotspotsPayload,
                "COMMIT_HISTORY_PAYLOAD_PLACEHOLDER", commitHistoryPayload,
                "SOURCE_CODE_CORPUS_PAYLOAD_PLACEHOLDER", sourceCodeCorpusPayload
        ));
        return repositoryContextXml;
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
}


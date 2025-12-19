package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.markdown.*;
import com.jlabs.repo.onboarder.model.GitReport;
import com.jlabs.repo.onboarder.service.exceptions.PromptConstructionException;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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
    private static final String AI_CONTEXT_DOCUMENTATION_TEMPLATE_PATH = "prompts/ai-context-documentation-template.md";
    private static final String README_PROMPT_TEMPLATE_PATH = "prompts/readme-prompt-template.md";
    private static final String README_DOCUMENTATION_TEMPLATE_PATH = "prompts/readme-documentation-template.md";

    /**
     * Nazwa placeholdera w prompt template, pod którym wstrzykujemy instrukcje/strukturę dokumentu
     * (np. szablon dla AI Context File albo szablon README).
     *
     * Uwaga: używamy jednego, generycznego klucza, żeby PromptConstructionService był niezależny
     * od typu dokumentu.
     */
    private static final String DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY = "DOCUMENTATION_TEMPLATE";
    
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
        // Kompatybilność wsteczna: dotychczasowi wywołujący nie muszą znać ścieżek template'ów.
        // Domyślnie generujemy AI Context File w oparciu o istniejące zasoby classpath.
        return constructPrompt(report, repoRoot, AI_CONTEXT_PROMPT_TEMPLATE_PATH, AI_CONTEXT_DOCUMENTATION_TEMPLATE_PATH);
    }

    /**
     * Konstruuje finalny prompt dla AI modelu, pozwalając wskazać template promptu oraz template dokumentacji.
     *
     * Wyjaśnienie (po co to jest):
     * - W projekcie generujemy więcej niż jeden typ dokumentu (np. AI Context File oraz README),
     *   ale oba wymagają tego samego "repository context" oraz innego zestawu instrukcji/sekcji.
     * - Parametryzacja ścieżek pozwala reużywać tę samą logikę konstrukcji promptu dla różnych dokumentów,
     *   bez duplikowania kodu.
     *
     * @param report raport z analizy Git repozytorium
     * @param repoRoot ścieżka do katalogu głównego repozytorium
     * @param promptTemplatePath ścieżka w classpath do zewnętrznego prompt template (Markdown)
     * @param documentationTemplatePath ścieżka w classpath do template instrukcji/struktury dokumentu (Markdown)
     * @return finalny prompt jako String gotowy do wysłania do API
     */
    public String constructPrompt(
            GitReport report,
            Path repoRoot,
            String promptTemplatePath,
            String documentationTemplatePath
    ) {
        try {
          
            String repositoryContextXml = preapreRepositoryContext(report, repoRoot);
            
            String documentationTemplate = loadDocumentationTemplate(documentationTemplatePath);
            
            PromptTemplate finalPromptTemplate = PromptTemplate.builder()
                    .renderer(StTemplateRenderer.builder()
                            .startDelimiterToken(PLACEHOLDER_TOKEN)
                            .endDelimiterToken(PLACEHOLDER_TOKEN)
                            .build())
                    .resource(new ClassPathResource(promptTemplatePath))
                    .build();
            
            String finalPrompt = finalPromptTemplate.render(Map.of(
                    "REPOSITORY_CONTEXT_PAYLOAD_PLACEHOLDER", repositoryContextXml,
                    DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY, documentationTemplate
            ));

            return finalPrompt;
        } catch (Exception e) {
            throw new PromptConstructionException(
                    "Błąd podczas konstrukcji promptu: " + e.getMessage(), e);
        }
    }


    /**
     * Konstruuje prompt dla AI modelu wykorzystując cached content.
     * 
     * Cached content zawiera już repository context XML, więc tutaj budujemy tylko
     * prompt z AI documentation template i instrukcjami. Cached content zostanie
     * automatycznie dołączony jako system instruction przez Google GenAI API.
     * 
     * @param cachedContentName nazwa cached content do użycia
     * @return prompt jako String (tylko AI documentation template, bez repository context)
     * @throws PromptConstructionException gdy nie można wczytać template'ów
     */
    public String constructPromptWithCache(String cachedContentName) {
        // Kompatybilność wsteczna: domyślnie korzystamy z template'ów dla AI Context File.
        return constructPromptWithCache(cachedContentName, AI_CONTEXT_PROMPT_TEMPLATE_PATH, AI_CONTEXT_DOCUMENTATION_TEMPLATE_PATH);
    }

    /**
     * Konstruuje prompt dla AI modelu wykorzystując cached content, pozwalając wskazać template'y.
     *
     * Wyjaśnienie:
     * - Cached content zawiera już repository context XML jako system instruction, więc w prompt template
     *   wstrzykujemy jedynie "cacheInfo" jako placeholder dla kontekstu oraz template instrukcji dokumentu.
     * - Parametryzacja ścieżek umożliwia generowanie różnych dokumentów (AI Context / README) w oparciu
     *   o ten sam cache.
     *
     * @param cachedContentName nazwa cached content do użycia
     * @param promptTemplatePath ścieżka w classpath do zewnętrznego prompt template (Markdown)
     * @param documentationTemplatePath ścieżka w classpath do template instrukcji/struktury dokumentu (Markdown)
     * @return prompt jako String (instrukcje + template dokumentu, bez pełnego repository context)
     */
    public String constructPromptWithCache(
            String cachedContentName,
            String promptTemplatePath,
            String documentationTemplatePath
    ) {
        try {
            // Gdy używamy cached content, nie musimy dołączać repository context do promptu
            // - jest już w cache jako system instruction. Tutaj budujemy tylko 
            // instrukcje dla AI co ma wygenerować.
            String documentationTemplate = loadDocumentationTemplate(documentationTemplatePath);
            
            // Utwórz uproszczony prompt - cached content zawiera już repository context
            PromptTemplate simplePromptTemplate = PromptTemplate.builder()
                    .renderer(StTemplateRenderer.builder()
                            .startDelimiterToken(PLACEHOLDER_TOKEN)
                            .endDelimiterToken(PLACEHOLDER_TOKEN)
                            .build())
                    .resource(new ClassPathResource(promptTemplatePath))
                    .build();
            
            // W prompt template używamy placeholdera, ale zamiast pełnego XML 
            // podajemy tylko informację że kontekst jest w cache
            String cacheInfo = String.format(
                    "<cached_repository_context name=\"%s\">\n" +
                    "Repository context is available in cached content.\n" +
                    "</cached_repository_context>", 
                    cachedContentName);
            
            return simplePromptTemplate.render(Map.of(
                    "REPOSITORY_CONTEXT_PAYLOAD_PLACEHOLDER", cacheInfo,
                    DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY, documentationTemplate
            ));
            
        } catch (Exception e) {
            throw new PromptConstructionException(
                    "Błąd podczas konstrukcji promptu z cached content: " + e.getMessage(), e);
        }
    }

    /**
     * Przygotowuje XML z kontekstem repozytorium (directory tree, hotspots, commits, source code).
     * 
     * Ta metoda jest używana do:
     * 1. Tworzenia cached content (XML jest zapisywany w cache po stronie Google)
     * 2. Tworzenia pełnego promptu gdy cache nie istnieje
     * 
     * @param report raport z analizy Git repozytorium
     * @param repoRoot ścieżka do katalogu głównego repozytorium
     * @return XML z pełnym kontekstem repozytorium
     */
    public String prepareRepositoryContext(GitReport report, Path repoRoot) {
        return preapreRepositoryContext(report, repoRoot);
    }

    /**
     * Wczytuje z classpath template instrukcji/struktury dokumentu.
     *
     * Wyjaśnienie:
     * - Template dokumentacji (np. skeleton README albo skeleton AI Context File) trzymamy jako zasób w classpath,
     *   aby był wersjonowany razem z aplikacją i możliwy do łatwej edycji bez zmian w kodzie.
     * - Przyjmujemy ścieżkę jako argument, dzięki czemu ta sama logika działa dla wielu typów dokumentów.
     */
    private String loadDocumentationTemplate(String documentationTemplatePath) throws Exception {
        ClassPathResource docTemplateResource = new ClassPathResource(documentationTemplatePath);
        return docTemplateResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Pomocnicze stałe dla wywołujących (np. DocumentationGenerationService), jeśli chcą generować README.
     * Na tym etapie nie używamy ich bezpośrednio w logice (logika jest parametryzowana).
     */
    public String getReadmePromptTemplatePath() {
        return README_PROMPT_TEMPLATE_PATH;
    }

    public String getReadmeDocumentationTemplatePath() {
        return README_DOCUMENTATION_TEMPLATE_PATH;
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


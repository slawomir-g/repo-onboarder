package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.markdown.*;
import com.jlabs.repo.onboarder.model.GitReport;
import com.jlabs.repo.onboarder.service.exceptions.PromptConstructionException;
import lombok.RequiredArgsConstructor;
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
 * <p>
 * Zgodnie z planem optymalizacji, używa bezpośrednio metod generate()
 * z klas PayloadWriter zamiast czytać z plików.
 */
@Service
@RequiredArgsConstructor
public class PromptConstructionService {

        private static final char PLACEHOLDER_TOKEN = '$';
        private static final String REPOSITORY_CONTEXT_TEMPLATE_PATH = "prompts/repository-context-payload-template.xml";

        /**
         * Nazwa placeholdera w prompt template, pod którym wstrzykujemy
         * instrukcje/strukturę dokumentu
         * (np. szablon dla AI Context File albo szablon README).
         * <p>
         * Uwaga: używamy jednego, generycznego klucza, żeby PromptConstructionService
         * był niezależny
         * od typu dokumentu.
         */
        private static final String DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY = "DOCUMENTATION_TEMPLATE";

        private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT
                        .withZone(ZoneOffset.UTC);

        private final DirectoryTreePayloadWriter directoryTreePayloadWriter;
        private final HotspotsPayloadWriter hotspotsPayloadWriter;
        private final CommitHistoryPayloadWriter commitHistoryPayloadWriter;
        private final SourceCodeCorpusPayloadWriter sourceCodeCorpusPayloadWriter;

        /**
         * Konstruuje finalny prompt dla AI modelu, pozwalając wskazać template promptu
         * oraz template dokumentacji.
         * <p>
         * Wyjaśnienie (po co to jest):
         * - W projekcie generujemy więcej niż jeden typ dokumentu (np. AI Context File
         * oraz README),
         * ale oba wymagają tego samego "repository context" oraz innego zestawu
         * instrukcji/sekcji.
         * - Parametryzacja ścieżek pozwala reużywać tę samą logikę konstrukcji promptu
         * dla różnych dokumentów,
         * bez duplikowania kodu.
         *
         * @param report                    raport z analizy Git repozytorium
         * @param repoRoot                  ścieżka do katalogu głównego repozytorium
         * @param promptTemplatePath        ścieżka w classpath do zewnętrznego prompt
         *                                  template (Markdown)
         * @param documentationTemplatePath ścieżka w classpath do template
         *                                  instrukcji/struktury dokumentu (Markdown)
         * @return finalny prompt jako String gotowy do wysłania do API
         */
        public String constructPrompt(
                        GitReport report,
                        Path repoRoot,
                        String promptTemplatePath,
                        String documentationTemplatePath,
                        String targetLanguage) {
                try {

                        String repositoryContextXml = prepareRepositoryContext(report, repoRoot);

                        String documentationTemplate = loadDocumentationTemplate(documentationTemplatePath);

                        // Append language instruction
                        if (targetLanguage != null && !targetLanguage.isBlank()) {
                                documentationTemplate += "\n\nIMPORTANT: Please write the response in " + targetLanguage
                                                + " language.";
                        }

                        PromptTemplate finalPromptTemplate = PromptTemplate.builder()
                                        .renderer(StTemplateRenderer.builder()
                                                        .startDelimiterToken(PLACEHOLDER_TOKEN)
                                                        .endDelimiterToken(PLACEHOLDER_TOKEN)
                                                        .build())
                                        .resource(new ClassPathResource(promptTemplatePath))
                                        .build();

                        String finalPrompt = finalPromptTemplate.render(Map.of(
                                        "REPOSITORY_CONTEXT_PAYLOAD_PLACEHOLDER", repositoryContextXml,
                                        DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY, documentationTemplate));

                        return finalPrompt;
                } catch (Exception e) {
                        throw new PromptConstructionException(
                                        "Błąd podczas konstrukcji promptu: " + e.getMessage(), e);
                }
        }

        public String prepareRepositoryContext(GitReport report, Path repoRoot) {
                String directoryTreePayload = directoryTreePayloadWriter.generate(report);
                String hotspotsPayload = hotspotsPayloadWriter.generate(report);
                String commitHistoryPayload = commitHistoryPayloadWriter.generate(report);
                String sourceCodeCorpusPayload = sourceCodeCorpusPayloadWriter.generate(report, repoRoot);
                String projectName = extractProjectName(report.getRepo().getUrl());

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
                                TIMESTAMP_FORMATTER.format(report.getGeneratedAt()),
                                "BRANCH_PAYLOAD_PLACEHOLDER",
                                report.getRepo().getBranch() != null ? report.getRepo().getBranch() : "main",
                                "DIRECTORY_TREE_PAYLOAD_PLACEHOLDER", directoryTreePayload,
                                "HOTSPOTS_PAYLOAD_PLACEHOLDER", hotspotsPayload,
                                "COMMIT_HISTORY_PAYLOAD_PLACEHOLDER", commitHistoryPayload,
                                "SOURCE_CODE_CORPUS_PAYLOAD_PLACEHOLDER", sourceCodeCorpusPayload));
                return repositoryContextXml;
        }

        /**
         * Konstruuje prompt dla AI modelu wykorzystując cached content, pozwalając
         * wskazać template'y.
         * <p>
         * Wyjaśnienie:
         * - Cached content zawiera już repository context XML jako system instruction,
         * więc w prompt template
         * wstrzykujemy jedynie "cacheInfo" jako placeholder dla kontekstu oraz template
         * instrukcji dokumentu.
         * - Parametryzacja ścieżek umożliwia generowanie różnych dokumentów (AI Context
         * / README) w oparciu
         * o ten sam cache.
         *
         * @param cachedContentName         nazwa cached content do użycia
         * @param promptTemplatePath        ścieżka w classpath do zewnętrznego prompt
         *                                  template (Markdown)
         * @param documentationTemplatePath ścieżka w classpath do template
         *                                  instrukcji/struktury dokumentu (Markdown)
         * @return prompt jako String (instrukcje + template dokumentu, bez pełnego
         *         repository context)
         */
        public String constructPromptWithCache(
                        String cachedContentName,
                        String promptTemplatePath,
                        String documentationTemplatePath,
                        String targetLanguage) {
                try {
                        // Gdy używamy cached content, nie musimy dołączać repository context do promptu
                        // - jest już w cache jako system instruction. Tutaj budujemy tylko
                        // instrukcje dla AI co ma wygenerować.
                        String documentationTemplate = loadDocumentationTemplate(documentationTemplatePath);

                        // Append language instruction
                        if (targetLanguage != null && !targetLanguage.isBlank()) {
                                documentationTemplate += "\n\nIMPORTANT: Please write the response in " + targetLanguage
                                                + " language.";
                        }

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
                                        DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY, documentationTemplate));

                } catch (Exception e) {
                        throw new PromptConstructionException(
                                        "Błąd podczas konstrukcji promptu z cached content: " + e.getMessage(), e);
                }
        }

        private String loadDocumentationTemplate(String documentationTemplatePath) throws Exception {
                ClassPathResource docTemplateResource = new ClassPathResource(documentationTemplatePath);
                return docTemplateResource.getContentAsString(StandardCharsets.UTF_8);
        }

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

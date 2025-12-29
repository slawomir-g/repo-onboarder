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
 * Service responsible for constructing the prompt for the AI model.
 * Joins generated payloads with XML and Markdown templates,
 * creating a final prompt ready to be sent to the Gemini API.
 * <p>
 * According to the optimization plan, it uses generate() methods directly
 * from PayloadWriter classes instead of reading from files.
 */
@Service
@RequiredArgsConstructor
public class PromptConstructionService {

        private static final char PLACEHOLDER_TOKEN = '$';
        private static final String REPOSITORY_CONTEXT_TEMPLATE_PATH = "prompts/repository-context-payload-template.xml";

        /**
         * The name of the placeholder in the prompt template where we inject
         * the document instructions/structure
         * (e.g. template for AI Context File or README template).
         * <p>
         * Note: we use a single, generic key so that PromptConstructionService
         * is independent of the document type.
         */
        private static final String DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY = "DOCUMENTATION_TEMPLATE";

        private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT
                        .withZone(ZoneOffset.UTC);

        private final DirectoryTreePayloadWriter directoryTreePayloadWriter;
        private final HotspotsPayloadWriter hotspotsPayloadWriter;
        private final CommitHistoryPayloadWriter commitHistoryPayloadWriter;
        private final SourceCodeCorpusPayloadWriter sourceCodeCorpusPayloadWriter;

        /**
         * Constructs the final prompt for the AI model, allowing specification of the
         * prompt template
         * and documentation template.
         * <p>
         * Explanation (why this exists):
         * - In the project we generate more than one type of document (e.g. AI Context
         * File
         * and README),
         * but both require the same "repository context" and a different set of
         * instructions/sections.
         * - Parameterization of paths allows reusing the same prompt construction logic
         * for different documents,
         * without duplicating code.
         *
         * @param report                    report from Git repository analysis
         * @param repoRoot                  path to the repository root directory
         * @param promptTemplatePath        classpath path to external prompt
         *                                  template (Markdown)
         * @param documentationTemplatePath classpath path to template of
         *                                  document instructions/structure (Markdown)
         * @return final prompt as String ready to be sent to API
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

                        return constructPromptWithContent(repositoryContextXml, promptTemplatePath,
                                        documentationTemplate, targetLanguage);
                } catch (Exception e) {
                        throw new PromptConstructionException(
                                        "Error during prompt construction: " + e.getMessage(), e);
                }
        }

        public String constructPromptWithContent(
                        String repositoryContextXml,
                        String promptTemplatePath,
                        String documentationTemplateContent,
                        String targetLanguage) {
                try {
                        String languageInstruction = "";
                        if (targetLanguage != null && !targetLanguage.isBlank()) {
                                languageInstruction = "- IMPORTANT: Response MUST be in " + targetLanguage
                                                + " language";
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
                                        DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY, documentationTemplateContent,
                                        "LANGUAGE_INSTRUCTION", languageInstruction));

                        return finalPrompt;
                } catch (Exception e) {
                        throw new PromptConstructionException(
                                        "Error during prompt construction: " + e.getMessage(), e);
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
         * Constructs the prompt for the AI model using cached content, allowing
         * specification of templates.
         * <p>
         * Explanation:
         * - Cached content already contains repository context XML as system
         * instruction,
         * so in the prompt template
         * we inject only "cacheInfo" as a placeholder for context and the document
         * instruction template.
         * - Parameterization of paths allows generating different documents (AI Context
         * / README) based on
         * this same cache.
         *
         * @param cachedContentName         name of cached content to use
         * @param promptTemplatePath        classpath path to external prompt
         *                                  template (Markdown)
         * @param documentationTemplatePath classpath path to template of
         *                                  document instructions/structure (Markdown)
         * @return prompt as String (instructions + document template, without full
         *         repository context)
         */
        public String constructPromptWithCache(
                        String cachedContentName,
                        String promptTemplatePath,
                        String documentationTemplatePath,
                        String targetLanguage) {
                try {
                        String documentationTemplate = loadDocumentationTemplate(documentationTemplatePath);
                        return constructPromptWithCacheAndContent(cachedContentName, promptTemplatePath,
                                        documentationTemplate, targetLanguage);

                } catch (Exception e) {
                        throw new PromptConstructionException(
                                        "Error during prompt construction with cached content: " + e.getMessage(), e);
                }
        }

        public String constructPromptWithCacheAndContent(
                        String cachedContentName,
                        String promptTemplatePath,
                        String documentationTemplateContent,
                        String targetLanguage) {
                try {
                        String languageInstruction = "";
                        if (targetLanguage != null && !targetLanguage.isBlank()) {
                                languageInstruction = "- Response MUST be in language: " + targetLanguage;
                        }

                        // Create simplified prompt - cached content already contains repository context
                        PromptTemplate simplePromptTemplate = PromptTemplate.builder()
                                        .renderer(StTemplateRenderer.builder()
                                                        .startDelimiterToken(PLACEHOLDER_TOKEN)
                                                        .endDelimiterToken(PLACEHOLDER_TOKEN)
                                                        .build())
                                        .resource(new ClassPathResource(promptTemplatePath))
                                        .build();

                        // In prompt template we use placeholder, but instead of full XML
                        // we provide only information that context is in cache
                        String cacheInfo = String.format(
                                        "<cached_repository_context name=\"%s\">\n" +
                                                        "Repository context is available in cached content.\n" +
                                                        "</cached_repository_context>",
                                        cachedContentName);

                        return simplePromptTemplate.render(Map.of(
                                        "REPOSITORY_CONTEXT_PAYLOAD_PLACEHOLDER", cacheInfo,
                                        DOCUMENTATION_TEMPLATE_PLACEHOLDER_KEY, documentationTemplateContent,
                                        "LANGUAGE_INSTRUCTION", languageInstruction));

                } catch (Exception e) {
                        throw new PromptConstructionException(
                                        "Error during prompt construction with cached content: " + e.getMessage(), e);
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

                // Remove .git at the end if exists
                String url = repoUrl.replaceAll("\\.git$", "");

                // Extract last part of path (repo name)
                int lastSlash = Math.max(url.lastIndexOf('/'), url.lastIndexOf(':'));
                if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                        return url.substring(lastSlash + 1);
                }

                return "unknown-project";
        }
}

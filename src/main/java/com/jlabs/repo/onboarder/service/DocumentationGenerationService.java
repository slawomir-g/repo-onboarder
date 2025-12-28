package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentationGenerationService {

    private final PromptConstructionService promptConstructionService;
    private final RepositoryCacheService repositoryCacheService;
    private final AiProperties aiProperties;
    private final List<DocumentGenerationService> documentGenerators;

    /**
     * Generates documentation for the repository using the AI model with cache.
     * <p>
     * Flow:
     * 1. Ensures repository context cache availability (checks/creates)
     * 2. Runs generators for individual documents (AI Context, README, Refactoring,
     * DDD)
     * 3. Returns the result
     * <p>
     * The cache contains the repository context XML (directory tree, hotspots,
     * commits,
     * source code) and is identified by the repository URL. It automatically
     * expires
     * after the configured TTL.
     * Reusing cache for multiple documents saves costs and time.
     *
     * @param report         report from Git repository analysis
     * @param repoRoot       path to the repository root directory
     * @param debugOutputDir directory for saving debug files
     * @return documentation generation result
     */
    public DocumentationResult generateDocumentation(GitReport report, Path repoRoot, Path debugOutputDir,
            String targetLanguage) {
        log.info("Starting documentation generation for repo: {}", report.getRepo().getUrl());

        // 1. Ensure cache availability (once for all documents)
        String repositoryContentCacheName = ensureRepositoryContentCache(report, repoRoot, debugOutputDir);

        DocumentationResult result = new DocumentationResult();

        // 2. Run all generators
        for (DocumentGenerationService generator : documentGenerators) {
            generator.generate(result, report, repoRoot, debugOutputDir, repositoryContentCacheName, targetLanguage);
        }

        log.info("Documentation generated successfully");
        result.getDocuments().forEach((type, content) -> {
            log.debug("{} length: {} chars", type, content != null ? content.length() : 0);
        });

        return result;
    }

    /**
     * Ensures availability of cache with repository context.
     * Checks if cache exists, if not - tries to create it.
     *
     * @param report         Git report
     * @param repoRoot       path to repository
     * @param debugOutputDir debug directory
     * @return cache name (full name in format cachedContent/xxx) or null if
     *         cache unavailable
     */
    private String ensureRepositoryContentCache(GitReport report, Path repoRoot, Path debugOutputDir) {
        String repoUrl = report.getRepo().getUrl();
        String model = aiProperties.getChat().getOptions().getModel();

        // 1. Check if cache for repository already exists
        Optional<String> cachedContentName = repositoryCacheService.getCachedContentName(repoUrl);

        if (cachedContentName.isPresent()) {
            // Cache exists - return its name
            log.info("Using existing cache for repo: {}", repoUrl);
            return cachedContentName.get();
        }

        // 2. Cache does not exist - try to create new one
        log.info("Cache does not exist for repo: {}, attempting to create new one...", repoUrl);

        // Prepare repository context XML
        String repoContextXml = promptConstructionService.prepareRepositoryContext(report, repoRoot);
        saveDebugFile(debugOutputDir, "ai_context_prompt_debug.txt", repoContextXml);

        // Try to create cached content
        String newCacheName = repositoryCacheService.createCachedContent(repoUrl, repoContextXml, model);

        if (newCacheName != null) {
            log.info("Cache created successfully");
            return newCacheName;
        } else {
            log.info("Cache is not available, traditional prompt with full context will be used");
            return null;
        }
    }

    /**
     * Saves content to file for debugging purposes.
     * The method does not interrupt the main flow in case of errors - only logs
     * warnings.
     *
     * @param outputDir target directory
     * @param filename  name of file to save
     * @param content   content to save
     */
    private void saveDebugFile(Path outputDir, String filename, String content) {
        try {
            Path debugFile = outputDir.resolve(filename);
            Files.writeString(debugFile, content, StandardCharsets.UTF_8);
            log.debug("Saved debug file: {}", debugFile);
        } catch (Exception e) {
            log.warn("Failed to save debug file {}: {}", filename, e.getMessage());
        }
    }
}

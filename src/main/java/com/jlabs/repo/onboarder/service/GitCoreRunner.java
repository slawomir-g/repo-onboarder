package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import com.jlabs.repo.onboarder.git.*;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.jspecify.annotations.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitCoreRunner {

    private final GitCoreProperties properties;
    private final GitAnalysisContext analysisContext;

    private final GitRepositoryManager repositoryManager;
    private final GitCheckoutService checkoutService;
    private final GitMetaCollector metaCollector;
    private final GitFileCollector fileCollector;
    private final GitCommitCollector commitCollector;
    private final GitHotspotsCollector hotspotsCollector;
    private final DocumentationGenerationService documentationGenerationService;
    private final TestDirectoryCleaner testDirectoryCleaner;

    /**
     * Performs full Git repository analysis and generates documentation using AI.
     *
     * @return documentation generation result containing README, Architecture and
     *         Context File
     * @throws Exception when error occurs during analysis or documentation
     *                   generation
     */
    public DocumentationResult run(String repoUrl, String branch, boolean withTest, String targetLanguage)
            throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String temporaryDirName = timestamp + "_" + UUID.randomUUID();

        // workDir = .../workdir/{timestamp_uuid}
        Path workDir = Path.of(properties.getWorkdir(), temporaryDirName);
        Files.createDirectories(workDir);

        // repoDir = .../workdir/{timestamp_uuid}/repo
        Path repoDir = workDir.resolve("repo");
        Files.createDirectories(repoDir);

        CredentialsProvider credentials = repositoryManager.credentials(properties);

        try (GitAnalysisContext ctx = analysisContext) {

            Git git = ctx.open(properties, repoDir.toString(), repoUrl);

            checkoutService.fetchCheckoutPull(git, properties, credentials, branch);

            if (!withTest) {
                testDirectoryCleaner.clean(ctx.repositoryRoot());
            }

            Path repoRoot = ctx.repositoryRoot();

            GitReport report = createGitReport(repoUrl, branch, withTest, git, workDir.toString());

            DocumentationResult result = documentationGenerationService.generateDocumentation(report, repoRoot,
                    workDir, targetLanguage);

            saveDocumentationResult(result, workDir);

            log.info("Documentation generated successfully");
            result.getDocuments().forEach((type, content) -> log.debug("{} length: {} chars", type,
                    content != null ? content.length() : 0));

            return result;
        }
    }

    private @NonNull GitReport createGitReport(String repoUrl, String branch, boolean withTest, Git git, String workDir)
            throws Exception {
        GitReport report = new GitReport();

        metaCollector.collect(git, git.getRepository(), properties, repoUrl, branch, workDir, report);

        fileCollector.collect(git.getRepository(), report, withTest);

        commitCollector.collect(git, git.getRepository(), properties, report);

        hotspotsCollector.collect(report);
        return report;
    }

    private void saveDocumentationResult(DocumentationResult result, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        for (var entry : result.getDocuments().entrySet()) {
            String type = entry.getKey();
            String content = entry.getValue();

            if (content != null && !content.isBlank()) {
                String filename = sanitizeFilename(type);
                if (!filename.toLowerCase().endsWith(".md")) {
                    filename += ".md";
                }
                Path filePath = outputDir.resolve(filename);
                Files.writeString(filePath, content, StandardCharsets.UTF_8);
                log.info("Saved: {}", filePath);
            }
        }
    }

    private String sanitizeFilename(String type) {
        return type.toUpperCase().replace(" ", "_").replaceAll("[^A-Z0-9_\\.]", "");
    }
}

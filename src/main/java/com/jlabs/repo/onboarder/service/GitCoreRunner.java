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

        log.info("▶ Starting documentation generation");
        log.info("  ├─ repoUrl        : {}", repoUrl);
        log.info("  ├─ branch         : {}", branch);
        log.info("  ├─ include tests  : {}", withTest);
        log.info("  └─ target language: {}", targetLanguage);

        long startTime = System.currentTimeMillis();

        // workDir = .../workdir/{timestamp_uuid}
        Path workDir = Path.of(properties.getWorkdir(), temporaryDirName);
        Files.createDirectories(workDir);

        log.debug("Work directory created at {}", workDir.toAbsolutePath());

        // repoDir = .../workdir/{timestamp_uuid}/repo
        Path repoDir = workDir.resolve("repo");
        Files.createDirectories(repoDir);

        log.debug("Repository directory prepared at {}", repoDir.toAbsolutePath());

        CredentialsProvider credentials = repositoryManager.credentials(properties);
        log.debug("Git credentials provider initialized");

        try (GitAnalysisContext ctx = analysisContext) {

            log.info("📥 Cloning repository");
            Git git = ctx.open(properties, repoDir.toString(), repoUrl);
            log.info("✔ Repository cloned successfully");

            log.info("🔀 Fetching and checking out branch '{}'", branch);
            checkoutService.fetchCheckoutPull(git, properties, credentials, branch);
            log.info("✔ Branch '{}' checked out", branch);

            if (!withTest) {
                log.warn("🧹 Test directories will be removed (withTest = false)");
                testDirectoryCleaner.clean(ctx.repositoryRoot());
                log.info("✔ Test directories removed");
            } else {
                log.info("🧪 Test directories preserved");
            }

            Path repoRoot = ctx.repositoryRoot();
            log.debug("Repository root resolved to {}", repoRoot.toAbsolutePath());

            log.info("📊 Generating git report");
            GitReport report = createGitReport(repoUrl, branch, withTest, git, workDir.toString());
            log.info("✔ Git report generated");

            log.info("📝 Generating documentation");
            DocumentationResult result = documentationGenerationService.generateDocumentation(report, repoRoot,
                    workDir, targetLanguage);
            log.info("✔ Documentation generated");

            log.info("💾 Saving documentation output");
            saveDocumentationResult(result, workDir);
            log.info("✔ Documentation saved to {}", workDir.toAbsolutePath());

            log.info("Documentation generated successfully");
            result.getDocuments().forEach((type, content) -> log.debug("{} length: {} chars", type,
                    content != null ? content.length() : 0));


            long durationMs = System.currentTimeMillis() - startTime;
            log.info("✅ Documentation pipeline finished successfully in {} ms", durationMs);

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

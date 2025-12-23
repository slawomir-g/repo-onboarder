package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import com.jlabs.repo.onboarder.git.*;
import com.jlabs.repo.onboarder.markdown.*;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class GitCoreRunner {

    private final GitCoreProperties properties;
    private final GitAnalysisContext analysisContext;

    private final GitRepositoryManager repositoryManager;
    private final GitCheckoutService checkoutService;
    private final GitMetaCollector metaCollector;
    private final GitFileCollector fileCollector;
    private final GitCommitCollector commitCollector;
    private final GitHotspotsCollector hotspotsCollector;
    private final MarkdownReportWriter markdownWriter;
    private final CommitHistoryPayloadWriter commitHistoryPayloadWriter;
    private final DirectoryTreePayloadWriter directoryTreePayloadWriter;
    private final HotspotsPayloadWriter hotspotsPayloadWriter;
    private final SourceCodeCorpusPayloadWriter sourceCodeCorpusPayloadWriter;
    private final DocumentationGenerationService documentationGenerationService;
    private final TestDirectoryCleaner testDirectoryCleaner;

    public GitCoreRunner(
            GitCoreProperties properties,
            GitAnalysisContext analysisContext,
            GitRepositoryManager repositoryManager,
            GitCheckoutService checkoutService,
            GitMetaCollector metaCollector,
            GitFileCollector fileCollector,
            GitCommitCollector commitCollector,
            GitHotspotsCollector hotspotsCollector,
            MarkdownReportWriter markdownWriter,
            CommitHistoryPayloadWriter commitHistoryPayloadWriter,
            DirectoryTreePayloadWriter directoryTreePayloadWriter,
            HotspotsPayloadWriter hotspotsPayloadWriter,
            SourceCodeCorpusPayloadWriter sourceCodeCorpusPayloadWriter,
            DocumentationGenerationService documentationGenerationService,
            TestDirectoryCleaner testDirectoryCleaner) {
        this.properties = properties;
        this.analysisContext = analysisContext;
        this.repositoryManager = repositoryManager;
        this.checkoutService = checkoutService;
        this.metaCollector = metaCollector;
        this.fileCollector = fileCollector;
        this.commitCollector = commitCollector;
        this.hotspotsCollector = hotspotsCollector;
        this.markdownWriter = markdownWriter;
        this.commitHistoryPayloadWriter = commitHistoryPayloadWriter;
        this.directoryTreePayloadWriter = directoryTreePayloadWriter;
        this.hotspotsPayloadWriter = hotspotsPayloadWriter;
        this.sourceCodeCorpusPayloadWriter = sourceCodeCorpusPayloadWriter;
        this.documentationGenerationService = documentationGenerationService;
        this.testDirectoryCleaner = testDirectoryCleaner;
    }

    private static final Logger logger = LoggerFactory.getLogger(GitCoreRunner.class);

    /**
     * Wykonuje pełną analizę repozytorium Git i generuje dokumentację przy użyciu
     * AI.
     *
     * @return wynik generacji dokumentacji zawierający README, Architecture i
     *         Context File
     * @throws Exception gdy wystąpi błąd podczas analizy lub generacji dokumentacji
     */
    public DocumentationResult run(String repoUrl, String branch, boolean withTest) throws Exception {
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
                    workDir);

            saveDocumentationResult(result, workDir);

            logger.info("Dokumentacja wygenerowana pomyślnie");
            logger.debug("README długość: {} znaków",
                    result.getReadme() != null ? result.getReadme().length() : 0);
            logger.debug("Architecture długość: {} znaków",
                    result.getArchitecture() != null ? result.getArchitecture().length() : 0);
            logger.debug("Context File długość: {} znaków",
                    result.getAiContextFile() != null ? result.getAiContextFile().length() : 0);

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

        // Zapisz README.md
        if (result.getReadme() != null && !result.getReadme().isBlank()) {
            Path readmeFile = outputDir.resolve("README.md");
            Files.writeString(readmeFile, result.getReadme(), StandardCharsets.UTF_8);
        }

        // Zapisz ARCHITECTURE.md
        if (result.getArchitecture() != null && !result.getArchitecture().isBlank()) {
            Path architectureFile = outputDir.resolve("ARCHITECTURE.md");
            Files.writeString(architectureFile, result.getArchitecture(), StandardCharsets.UTF_8);
        }

        // Zapisz AI_CONTEXT_FILE.md
        if (result.getAiContextFile() != null && !result.getAiContextFile().isBlank()) {
            Path contextFile = outputDir.resolve("AI_CONTEXT_FILE.md");
            Files.writeString(contextFile, result.getAiContextFile(), StandardCharsets.UTF_8);
        }
    }
}

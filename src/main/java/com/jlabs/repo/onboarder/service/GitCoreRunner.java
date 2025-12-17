package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import com.jlabs.repo.onboarder.git.*;
import com.jlabs.repo.onboarder.markdown.*;
import com.jlabs.repo.onboarder.model.GitReport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

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

    public GitCoreRunner(
            GitCoreProperties properties,
            GitAnalysisContext analysisContext,
            GitRepositoryManager repositoryManager,
            GitCheckoutService checkoutService,
            GitMetaCollector metaCollector,
            GitFileCollector fileCollector,
            GitCommitCollector commitCollector, GitHotspotsCollector hotspotsCollector,
            MarkdownReportWriter markdownWriter,
            CommitHistoryPayloadWriter commitHistoryPayloadWriter, DirectoryTreePayloadWriter directoryTreePayloadWriter, HotspotsPayloadWriter hotspotsPayloadWriter, SourceCodeCorpusPayloadWriter sourceCodeCorpusPayloadWriter
    ) {
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
    }

public void run() throws Exception {

        Path appWorkingDir = Path.of(System.getProperty("user.dir"));
        Path outputFile = appWorkingDir.resolve(properties.getOutput().getMarkdown());

        CredentialsProvider credentials =
                repositoryManager.credentials(properties);

        try (GitAnalysisContext ctx = analysisContext) {

            Git git = ctx.open(properties);

            checkoutService.fetchCheckoutPull(git, properties, credentials);

            GitReport report = new GitReport();

            metaCollector.collect(git, git.getRepository(), properties, report);

            fileCollector.collect(git.getRepository(), report);

            commitCollector.collect(git, git.getRepository(), properties, report);

            hotspotsCollector.collect(report);

            markdownWriter.write(report, outputFile);

            Path commitHistoryFile =
                    appWorkingDir.resolve("COMMIT_HISTORY_PAYLOAD.txt");

            commitHistoryPayloadWriter.write(report, commitHistoryFile);

            Path treePayloadFile =
                    appWorkingDir.resolve("DIRECTORY_TREE_PAYLOAD.txt");

            directoryTreePayloadWriter.write(report, treePayloadFile);

            Path hotspotPayloadFile =
                    appWorkingDir.resolve("HOTSPOTS_PAYLOAD.txt");

            hotspotsPayloadWriter.write(report, hotspotPayloadFile);

            Path sourceCorpusFile = appWorkingDir.resolve(
                    "SOURCE_CODE_CORPUS_PAYLOAD.txt"
            );

            sourceCodeCorpusPayloadWriter.write(
                    report,
                    Path.of(properties.getWorkdir()),
                    sourceCorpusFile
            );
        }
    }
}


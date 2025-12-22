package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;

@Service
public class GitAnalysisContext implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GitAnalysisContext.class);

    private final GitRepositoryManager repositoryManager;
    private Git git;

    public GitAnalysisContext(GitRepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    public Git open(GitCoreProperties props, String workDir, String repoUrl) throws Exception {

        this.git = repositoryManager.openOrClone(props, workDir, repoUrl);

        Repository repository = git.getRepository();
        File workTree = repository.getWorkTree();
        String absolutePath = workTree.getAbsolutePath();
        logger.info(absolutePath);

        return this.git;
    }

    public Git git() {
        if (git == null) {
            throw new IllegalStateException("GitAnalysisContext not initialized. Call open() first.");
        }
        return git;
    }

    public Path repositoryRoot() {
        if (git == null) {
            throw new IllegalStateException("GitAnalysisContext not initialized.");
        }
        return git.getRepository().getWorkTree().toPath();
    }

    @Override
    public void close() throws Exception {
        if (git != null) {
            git.close();
        }
    }
}

package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class GitAnalysisContext implements AutoCloseable {

    private final GitRepositoryManager repositoryManager;
    private Git git;

    public GitAnalysisContext(GitRepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
    }

    public Git open(GitCoreProperties props, String workDir, String repoUrl) throws Exception {

        CredentialsProvider credentials = repositoryManager.credentials(props);

        this.git = repositoryManager.openOrClone(props, workDir, repoUrl);

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

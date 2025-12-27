package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitAnalysisContext implements AutoCloseable {

    private final GitRepositoryManager repositoryManager;
    private Git git;

    public Git open(GitCoreProperties props, String workDir, String repoUrl) throws Exception {

        this.git = repositoryManager.openOrClone(props, workDir, repoUrl);

        Repository repository = git.getRepository();
        File workTree = repository.getWorkTree();
        String absolutePath = workTree.getAbsolutePath();

        log.info(absolutePath);

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

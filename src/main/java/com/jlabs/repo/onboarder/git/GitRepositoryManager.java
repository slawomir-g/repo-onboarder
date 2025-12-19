package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.nio.file.*;

@Service
public class GitRepositoryManager {

    public Git openOrClone(GitCoreProperties props, String workDirPath, String repoUrl) throws Exception {
        Path workDir = Path.of(workDirPath);
        Files.createDirectories(workDir);

        Path gitDir = workDir.resolve(".git");
        if (Files.exists(gitDir)) {
            var repo = new FileRepositoryBuilder()
                    .setGitDir(gitDir.toFile())
                    .readEnvironment()
                    .build();
            return new Git(repo);
        }

        var cmd = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(workDir.toFile())
                .setCloneAllBranches(true);

        CredentialsProvider cp = credentials(props);
        if (cp != null) cmd.setCredentialsProvider(cp);

        return cmd.call();
    }

    public CredentialsProvider credentials(GitCoreProperties props) {
        String token = props.getAuth().getToken();
        if (token != null && !token.isBlank()) {
            String user = props.getAuth().getUsername();
            if (user == null || user.isBlank()) user = "x-access-token";
            return new UsernamePasswordCredentialsProvider(user, token);
        }
        return null;
    }
}

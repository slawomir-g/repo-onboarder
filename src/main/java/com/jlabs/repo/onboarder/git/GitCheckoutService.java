package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.springframework.stereotype.Service;

@Service
public class GitCheckoutService {

    /**
     * Fetches remote changes, checks out the configured branch
     * (creates it if missing), and performs pull --rebase.
     */
    public void fetchCheckoutPull(
            Git git,
            GitCoreProperties props,
            CredentialsProvider credentialsProvider
    ) throws Exception {

        // --- FETCH (equivalent to: git fetch --prune)
        FetchCommand fetchCommand = git.fetch().setRemoveDeletedRefs(true);

        if (credentialsProvider != null) {
            fetchCommand.setCredentialsProvider(credentialsProvider);
        }

        fetchCommand.call();

        // --- CHECKOUT
        String branch = props.getBranch();

        try {
            // Try to checkout existing local branch
            git.checkout()
                    .setName(branch)
                    .call();

        } catch (RefNotFoundException e) {
            // Create local branch tracking origin/<branch>
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branch)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .setStartPoint("origin/" + branch)
                    .call();
        }

        // --- PULL (equivalent to: git pull --rebase)
        PullCommand pullCommand = git.pull()
                .setRebase(true);

        if (credentialsProvider != null) {
            pullCommand.setCredentialsProvider(credentialsProvider);
        }

        pullCommand.call();
    }
}
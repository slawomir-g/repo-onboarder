package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import com.jlabs.repo.onboarder.model.GitReport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class GitMetaCollector {

    public void collect(
            Git git,
            Repository repo,
            GitCoreProperties props,
            GitReport report
    ) throws Exception {

        // ===== BASIC REPO INFO =====
        report.repo.url = props.getRepoUrl();
        report.repo.branch = repo.getBranch();
        report.repo.workdir = repo.getWorkTree().getAbsolutePath();

        // ===== HEAD INFO =====
        ObjectId head = repo.resolve(Constants.HEAD);
        if (head != null) {
            report.repo.headCommit = head.getName();
            try (RevWalk walk = new RevWalk(repo)) {
                RevCommit c = walk.parseCommit(head);
                report.repo.headShortMessage = c.getShortMessage();
                report.repo.headCommitTime = Instant.ofEpochSecond(c.getCommitTime());
            }
        }

        // ===== REMOTES =====
        Config cfg = repo.getConfig();
        Set<String> remoteNames = cfg.getSubsections("remote");

        for (String remote : remoteNames) {
            GitReport.RemoteInfo ri = new GitReport.RemoteInfo();
            ri.name = remote;

            String[] urls = cfg.getStringList("remote", remote, "url");
            if (urls != null) {
                ri.uris.addAll(Arrays.asList(urls));
            }

            report.remotes.add(ri);
        }

        // ===== BRANCHES (LOCAL + REMOTE) =====
        report.branches = git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call()
                .stream()
                .map(Ref::getName)
                .sorted()
                .toList();

        // ===== TAGS =====
        report.tags = git.tagList()
                .call()
                .stream()
                .map(Ref::getName)
                .sorted()
                .toList();
    }
}

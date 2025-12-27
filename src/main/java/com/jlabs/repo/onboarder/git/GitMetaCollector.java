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
            String repoUrl,
            String branch,
            String workDir,
            GitReport report) throws Exception {

        // ===== BASIC REPO INFO =====
        // ===== BASIC REPO INFO =====
        report.getRepo().setUrl(repoUrl);
        report.getRepo().setBranch(branch);
        report.getRepo().setWorkdir(workDir);

        // ===== HEAD INFO =====
        ObjectId head = repo.resolve(Constants.HEAD);
        if (head != null) {
            if (head != null) {
                report.getRepo().setHeadCommit(head.getName());
                try (RevWalk walk = new RevWalk(repo)) {
                    RevCommit c = walk.parseCommit(head);
                    report.getRepo().setHeadShortMessage(c.getShortMessage());
                    report.getRepo().setHeadCommitTime(Instant.ofEpochSecond(c.getCommitTime()));
                }
            }
        }

        // ===== REMOTES =====
        Config cfg = repo.getConfig();
        Set<String> remoteNames = cfg.getSubsections("remote");

        for (String remote : remoteNames) {
            GitReport.RemoteInfo ri = new GitReport.RemoteInfo();
            ri.setName(remote);

            String[] urls = cfg.getStringList("remote", remote, "url");
            if (urls != null) {
                ri.getUris().addAll(Arrays.asList(urls));
            }

            report.getRemotes().add(ri);
        }

        // ===== BRANCHES (LOCAL + REMOTE) =====
        // ===== BRANCHES (LOCAL + REMOTE) =====
        report.setBranches(git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call()
                .stream()
                .map(Ref::getName)
                .sorted()
                .toList());

        // ===== TAGS =====
        // ===== TAGS =====
        report.setTags(git.tagList()
                .call()
                .stream()
                .map(Ref::getName)
                .sorted()
                .toList());
    }
}

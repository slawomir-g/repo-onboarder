package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import com.jlabs.repo.onboarder.model.GitReport;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.springframework.stereotype.Service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class GitCommitCollector {

    public void collect(Git git, Repository repo, GitCoreProperties props, GitReport report) throws Exception {

        int maxCommits = props.getLimits().getMaxCommits();
        int maxChangedFiles = props.getLimits().getMaxChangedFiles();

        try (RevWalk walk = new RevWalk(repo)) {

            DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
            diffFormatter.setRepository(repo);
            diffFormatter.setDetectRenames(true);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);

            int counter = 0;

            for (RevCommit commit : git.log().call()) {
                counter++;
                if (maxCommits > 0 && counter > maxCommits) break;

                GitReport.CommitInfo ci = new GitReport.CommitInfo();

                ci.commitId = commit.getName();
                ci.shortId = commit.getName().substring(0, 8);

                ci.authorName = commit.getAuthorIdent().getName();
                ci.authorEmail = commit.getAuthorIdent().getEmailAddress();
                ci.authorTime = Instant.ofEpochSecond(commit.getAuthorIdent().getWhen().toInstant().getEpochSecond());

                ci.committerName = commit.getCommitterIdent().getName();
                ci.committerEmail = commit.getCommitterIdent().getEmailAddress();
                ci.committerTime = Instant.ofEpochSecond(commit.getCommitterIdent().getWhen().toInstant().getEpochSecond());

                ci.messageShort = commit.getShortMessage();
                ci.messageFull = commit.getFullMessage();

                for (RevCommit p : commit.getParents()) {
                    ci.parents.add(p.getName());
                }

                if (commit.getParentCount() > 0) {
                    RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
                    RevCommit current = walk.parseCommit(commit.getId());

                    List<DiffEntry> diffs =
                            diffFormatter.scan(parent.getTree(), current.getTree());

                    int totalAdded = 0;
                    int totalDeleted = 0;

                    int take =
                            (maxChangedFiles > 0)
                                    ? Math.min(maxChangedFiles, diffs.size())
                                    : diffs.size();

                    for (int i = 0; i < diffs.size(); i++) {
                        DiffEntry de = diffs.get(i);

                        int added = 0;
                        int deleted = 0;

                        FileHeader fh = diffFormatter.toFileHeader(de);
                        for (HunkHeader hh : fh.getHunks()) {
                            EditList edits = hh.toEditList();
                            for (Edit e : edits) {
                                added += e.getEndB() - e.getBeginB();
                                deleted += e.getEndA() - e.getBeginA();
                            }
                        }

                        totalAdded += added;
                        totalDeleted += deleted;

                        if (i < take) {
                            GitReport.CommitInfo.FileChange fc =
                                    new GitReport.CommitInfo.FileChange();
                            fc.type = de.getChangeType().name();
                            fc.oldPath = de.getOldPath();
                            fc.newPath = de.getNewPath();
                            fc.linesAdded = added;
                            fc.linesDeleted = deleted;

                            ci.changes.add(fc);
                        }
                    }

                    ci.diffStats.filesChanged = diffs.size();
                    ci.diffStats.linesAdded = totalAdded;
                    ci.diffStats.linesDeleted = totalDeleted;
                    ci.diffStats.linesTotal = totalAdded + totalDeleted;

                    if (props.getLimits().isIncludePatch()) {
                        ci.patchSnippet =
                                patchSnippet(repo, parent, current, props.getLimits().getMaxPatchChars());
                    }
                }

                report.commits.add(ci);
            }
        }
    }

    private String patchSnippet(
            Repository repo,
            RevCommit parent,
            RevCommit current,
            int maxChars
    ) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DiffFormatter fmt = new DiffFormatter(baos)) {
            fmt.setRepository(repo);
            fmt.setDetectRenames(true);
            fmt.format(parent.getTree(), current.getTree());
        }

        String patch = baos.toString(StandardCharsets.UTF_8);
        if (maxChars <= 0) return patch;

        return patch.length() <= maxChars
                ? patch
                : patch.substring(0, maxChars) + "\n...[truncated]...\n";
    }
}

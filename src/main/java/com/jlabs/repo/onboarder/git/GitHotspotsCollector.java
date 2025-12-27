package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.stereotype.Service;

@Service
public class GitHotspotsCollector {

    public void collect(GitReport report) {
        report.getFileStats().clear();

        for (GitReport.CommitInfo commit : report.getCommits()) {
            for (GitReport.CommitInfo.FileChange change : commit.getChanges()) {

                String path = change.getNewPath() != null
                        ? change.getNewPath()
                        : change.getOldPath();

                if (path == null || path.isBlank()) {
                    continue;
                }

                GitReport.FileStats stats = report.getFileStats().computeIfAbsent(path, p -> new GitReport.FileStats());

                stats.setCommits(stats.getCommits() + 1);
                stats.setLinesAdded(stats.getLinesAdded() + change.getLinesAdded());
                stats.setLinesDeleted(stats.getLinesDeleted() + change.getLinesDeleted());
            }
        }
    }
}

package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.stereotype.Service;

@Service
public class GitHotspotsCollector {

    public void collect(GitReport report) {
        report.fileStats.clear();

        for (GitReport.CommitInfo commit : report.commits) {
            for (GitReport.CommitInfo.FileChange change : commit.changes) {

                String path = change.newPath != null
                        ? change.newPath
                        : change.oldPath;

                if (path == null || path.isBlank()) {
                    continue;
                }

                GitReport.FileStats stats =
                        report.fileStats.computeIfAbsent(path, p -> new GitReport.FileStats());

                stats.commits++;
                stats.linesAdded += change.linesAdded;
                stats.linesDeleted += change.linesDeleted;
            }
        }
    }
}

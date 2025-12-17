package com.jlabs.repo.onboarder.markdown;

import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class MarkdownReportWriter {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    public void write(GitReport report, Path output) throws Exception {

        try (BufferedWriter w = Files.newBufferedWriter(output)) {

            w.write("# Git Report\n\n");
            w.write("- Generated at (UTC): **" + TS.format(report.generatedAt) + "**\n\n");

            // === REPO ===
            w.write("## Repository\n\n");
            w.write("- URL: **" + safe(report.repo.url) + "**\n");
            w.write("- Branch: **" + safe(report.repo.branch) + "**\n");
            w.write("- Workdir: `" + safe(report.repo.workdir) + "`\n\n");

            // === HEAD ===
            w.write("## HEAD\n\n");
            w.write("- Commit: `" + safe(report.repo.headCommit) + "`\n");
            w.write("- Time: **" + format(report.repo.headCommitTime) + "**\n");
            w.write("- Message: " + safe(report.repo.headShortMessage) + "\n\n");

            // === FILES ===
            w.write("## Files at HEAD\n\n");
            w.write("- Total: **" + report.allFilesAtHead.size() + "**\n\n");

            int maxFiles = Math.min(300, report.allFilesAtHead.size());
            for (int i = 0; i < maxFiles; i++) {
                w.write("- `" + report.allFilesAtHead.get(i) + "`\n");
            }
            if (report.allFilesAtHead.size() > maxFiles) {
                w.write("\n_... truncated (" +
                        (report.allFilesAtHead.size() - maxFiles) +
                        " more files)_\n");
            }

            // === COMMITS ===
            w.write("\n## Commits\n\n");
            for (GitReport.CommitInfo c : report.commits) {

                w.write("### " + c.shortId + " — " + inline(c.messageShort) + "\n\n");
                w.write("- Author: **" + inline(c.authorName) + "** <" + inline(c.authorEmail) + ">\n");
                w.write("- Author time: **" + format(c.authorTime) + "**\n");
                w.write("- Committer: **" + inline(c.committerName) + "** <" + inline(c.committerEmail) + ">\n");
                w.write("- Committer time: **" + format(c.committerTime) + "**\n\n");

                w.write("**Diff stats**: files=" + c.diffStats.filesChanged +
                        ", + " + c.diffStats.linesAdded +
                        ", - " + c.diffStats.linesDeleted +
                        "\n\n");

                if (!c.changes.isEmpty()) {
                    w.write("| Type | Path | + | - |\n");
                    w.write("|---|---|---:|---:|\n");
                    for (var ch : c.changes) {
                        String path =
                                (ch.newPath != null && !ch.newPath.isBlank())
                                        ? ch.newPath
                                        : ch.oldPath;

                        w.write("| " + ch.type + " | `" + escape(path) +
                                "` | " + ch.linesAdded +
                                " | " + ch.linesDeleted + " |\n");
                    }
                    w.write("\n");
                }

                if (c.patchSnippet != null) {
                    w.write("<details><summary>Patch</summary>\n\n```diff\n");
                    w.write(c.patchSnippet);
                    w.write("\n```\n</details>\n\n");
                }
            }
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String inline(String s) {
        return safe(s).replace("\n", " ");
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("`", "\\`");
    }

    private static String format(java.time.Instant i) {
        return i == null ? "-" : TS.format(i);
    }
}

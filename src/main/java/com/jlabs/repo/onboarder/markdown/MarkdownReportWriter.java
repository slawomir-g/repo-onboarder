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

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    public void write(GitReport report, Path output) throws Exception {

        try (BufferedWriter w = Files.newBufferedWriter(output)) {

            w.write("# Git Report\n\n");
            w.write("- Generated at (UTC): **" + TS.format(report.getGeneratedAt()) + "**\n\n");

            // === REPO ===
            w.write("## Repository\n\n");
            w.write("- URL: **" + safe(report.getRepo().getUrl()) + "**\n");
            w.write("- Branch: **" + safe(report.getRepo().getBranch()) + "**\n");
            w.write("- Workdir: `" + safe(report.getRepo().getWorkdir()) + "`\n\n");

            // === HEAD ===
            w.write("## HEAD\n\n");
            w.write("- Commit: `" + safe(report.getRepo().getHeadCommit()) + "`\n");
            w.write("- Time: **" + format(report.getRepo().getHeadCommitTime()) + "**\n");
            w.write("- Message: " + safe(report.getRepo().getHeadShortMessage()) + "\n\n");

            // === FILES ===
            w.write("## Files at HEAD\n\n");
            w.write("- Total: **" + report.getAllFilesAtHead().size() + "**\n\n");

            int maxFiles = Math.min(300, report.getAllFilesAtHead().size());
            for (int i = 0; i < maxFiles; i++) {
                w.write("- `" + report.getAllFilesAtHead().get(i) + "`\n");
            }
            if (report.getAllFilesAtHead().size() > maxFiles) {
                w.write("\n_... truncated (" +
                        (report.getAllFilesAtHead().size() - maxFiles) +
                        " more files)_\n");
            }

            // === COMMITS ===
            w.write("\n## Commits\n\n");
            for (GitReport.CommitInfo c : report.getCommits()) {

                w.write("### " + c.getShortId() + " — " + inline(c.getMessageShort()) + "\n\n");
                w.write("- Author: **" + inline(c.getAuthorName()) + "** <" + inline(c.getAuthorEmail()) + ">\n");
                w.write("- Author time: **" + format(c.getAuthorTime()) + "**\n");
                w.write("- Committer: **" + inline(c.getCommitterName()) + "** <" + inline(c.getCommitterEmail())
                        + ">\n");
                w.write("- Committer time: **" + format(c.getCommitterTime()) + "**\n\n");

                w.write("**Diff stats**: files=" + c.getDiffStats().getFilesChanged() +
                        ", + " + c.getDiffStats().getLinesAdded() +
                        ", - " + c.getDiffStats().getLinesDeleted() +
                        "\n\n");

                if (!c.getChanges().isEmpty()) {
                    w.write("| Type | Path | + | - |\n");
                    w.write("|---|---|---:|---:|\n");
                    for (var ch : c.getChanges()) {
                        String path = (ch.getNewPath() != null && !ch.getNewPath().isBlank())
                                ? ch.getNewPath()
                                : ch.getOldPath();

                        w.write("| " + ch.getType() + " | `" + escape(path) +
                                "` | " + ch.getLinesAdded() +
                                " | " + ch.getLinesDeleted() + " |\n");
                    }
                    w.write("\n");
                }

                if (c.getPatchSnippet() != null) {
                    w.write("<details><summary>Patch</summary>\n\n```diff\n");
                    w.write(c.getPatchSnippet());
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

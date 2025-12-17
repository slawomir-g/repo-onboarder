package com.jlabs.repo.onboarder.markdown;

import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class CommitHistoryPayloadWriter {

    public void write(GitReport report, Path outputFile) {
        String content = generate(report);

        try {
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String generate(GitReport report) {
        StringBuilder sb = new StringBuilder();

        report.commits.forEach(c -> {
            sb.append("<commit date='")
                    .append(c.committerTime)
                    .append("'>")
                    .append(escape(c.messageShort))
                    .append("</commit>")
                    .append(System.lineSeparator());
        });

        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;");
    }
}

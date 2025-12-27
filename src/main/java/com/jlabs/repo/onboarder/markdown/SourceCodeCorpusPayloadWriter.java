package com.jlabs.repo.onboarder.markdown;

import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SourceCodeCorpusPayloadWriter {

    public void write(GitReport report, Path repoRoot, Path outputFile) {
        String content = generate(report, repoRoot);
        try {
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String generate(GitReport report, Path repoRoot) {
        StringBuilder sb = new StringBuilder();

        for (String relativePath : report.getAllFilesAtHead()) {
            if (relativePath.contains("test")) {
                continue;
            }
            Path file = repoRoot.resolve(relativePath);

            if (!Files.isRegularFile(file)) {
                continue;
            }

            String content;
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }

            sb.append("<file path=\"")
                    .append(relativePath)
                    .append("\">")
                    .append(System.lineSeparator());

            sb.append(content)
                    .append(System.lineSeparator());

            sb.append("</file>")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        return sb.toString();
    }
}

package com.jlabs.repo.onboarder.markdown;

import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

@Service
public class HotspotsPayloadWriter {

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

        report.getFileStats().entrySet().stream()
                .sorted(Map.Entry.<String, GitReport.FileStats>comparingByValue(
                        Comparator.comparingInt(fs -> fs.getLinesAdded() + fs.getLinesDeleted())).reversed())
                .forEach(entry -> {
                    int churn = entry.getValue().getLinesAdded() + entry.getValue().getLinesDeleted();
                    sb.append("<file path=\"")
                            .append(entry.getKey())
                            .append("\" churn_score=\"")
                            .append(churn)
                            .append("\" />")
                            .append(System.lineSeparator());
                });

        return sb.toString();
    }
}

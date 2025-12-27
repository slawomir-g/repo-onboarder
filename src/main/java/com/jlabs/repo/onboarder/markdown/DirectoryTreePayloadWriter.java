package com.jlabs.repo.onboarder.markdown;

import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

@Service
public class DirectoryTreePayloadWriter {

    private static final String INDENT = "  ";

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
        DirNode root = new DirNode("", true);

        for (String path : report.getAllFilesAtHead()) {
            addPath(root, path);
        }

        StringBuilder sb = new StringBuilder();
        render(root, sb, 0);
        return sb.toString();
    }

    private void addPath(DirNode root, String path) {
        String[] parts = path.split("/");
        DirNode current = root;

        for (int i = 0; i < parts.length; i++) {
            boolean directory = i < parts.length - 1;
            current.children.putIfAbsent(parts[i], new DirNode(parts[i], directory));
            current = current.children.get(parts[i]);
        }
    }

    private void render(DirNode node, StringBuilder sb, int depth) {
        if (!node.name.isEmpty()) {
            sb.append(INDENT.repeat(depth))
                    .append(node.name);
            if (node.directory) {
                sb.append("/");
            }
            sb.append(System.lineSeparator());
        }

        for (DirNode child : node.children.values()) {
            render(child, sb, depth + 1);
        }
    }

    private static class DirNode {
        String name;
        boolean directory;
        Map<String, DirNode> children = new TreeMap<>();

        DirNode(String name, boolean directory) {
            this.name = name;
            this.directory = directory;
        }
    }
}

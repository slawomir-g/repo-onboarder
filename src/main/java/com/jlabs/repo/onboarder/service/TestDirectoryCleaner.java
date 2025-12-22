package com.jlabs.repo.onboarder.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Service
public class TestDirectoryCleaner {
    private static final String DEFAULT_TEST_DIR = "src/test";

    public void clean(Path repoRoot) {
        clean(repoRoot, DEFAULT_TEST_DIR);
    }

    public void clean(Path repoRoot, String relativePath) {
        Path testDir = repoRoot.resolve(relativePath).normalize();

        if (!Files.exists(testDir)) {
            return;
        }

        if (!testDir.startsWith(repoRoot)) {
            throw new IllegalStateException(
                    "Refusing to delete directory outside repository: " + testDir
            );
        }

        try {
            Files.walkFileTree(testDir, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to delete test directory: " + testDir, e
            );
        }
    }
}

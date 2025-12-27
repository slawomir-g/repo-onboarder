package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.markdown.DirectoryTreePayloadWriter;
import com.jlabs.repo.onboarder.model.GitReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za post-processing wygenerowanej dokumentacji.
 * Służy do wzbogacania dokumentów o sekcje generowane programowo,
 * których AI nie musi generować samodzielnie (np. pełna struktura plików).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentationPostProcessingService {

    private final DirectoryTreePayloadWriter directoryTreePayloadWriter;

    /**
     * Wzbogaca AI Context File o sekcję Project Structure zawierającą pełne drzewo
     * plików.
     *
     * @param aiContextFile wygenerowana treść markdown od AI
     * @param report        raport Git zawierający listę plików
     * @return wzbogacona treść markdown
     */
    public String enhance(String aiContextFile, GitReport report) {
        if (aiContextFile == null) {
            return null;
        }

        log.info("Rozpoczynam post-processing AI Context File - dodawanie Project Structure");

        String tree = directoryTreePayloadWriter.generate(report);

        StringBuilder sb = new StringBuilder(aiContextFile);

        // Dodaj nową linię jeśli dokument nie kończy się nią
        if (!aiContextFile.endsWith("\n")) {
            sb.append("\n");
        }

        sb.append("\n## Project Structure\n\n");
        sb.append("```\n");
        sb.append(tree);
        sb.append("```\n");

        log.debug("Sekcja Project Structure została dodana do dokumentu");
        return sb.toString();
    }
}

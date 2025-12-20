package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.markdown.DirectoryTreePayloadWriter;
import com.jlabs.repo.onboarder.model.GitReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za post-processing wygenerowanej dokumentacji.
 * Służy do wzbogacania dokumentów o sekcje generowane programowo, 
 * których AI nie musi generować samodzielnie (np. pełna struktura plików).
 */
@Service
public class DocumentationPostProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationPostProcessingService.class);
    private final DirectoryTreePayloadWriter directoryTreePayloadWriter;

    public DocumentationPostProcessingService(DirectoryTreePayloadWriter directoryTreePayloadWriter) {
        this.directoryTreePayloadWriter = directoryTreePayloadWriter;
    }

    /**
     * Wzbogaca AI Context File o sekcję Project Structure zawierającą pełne drzewo plików.
     *
     * @param aiContextFile wygenerowana treść markdown od AI
     * @param report        raport Git zawierający listę plików
     * @return wzbogacona treść markdown
     */
    public String enhance(String aiContextFile, GitReport report) {
        if (aiContextFile == null) {
            return null;
        }

        logger.info("Rozpoczynam post-processing AI Context File - dodawanie Project Structure");

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

        logger.debug("Sekcja Project Structure została dodana do dokumentu");
        return sb.toString();
    }
}

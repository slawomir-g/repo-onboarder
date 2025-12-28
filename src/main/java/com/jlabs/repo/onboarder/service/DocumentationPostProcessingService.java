package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.markdown.DirectoryTreePayloadWriter;
import com.jlabs.repo.onboarder.model.GitReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for post-processing of generated documentation.
 * Used to enrich documents with programmatically generated sections,
 * which the AI does not have to generate itself (e.g. full file structure).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentationPostProcessingService {

    private final DirectoryTreePayloadWriter directoryTreePayloadWriter;

    /**
     * Enriches AI Context File with Project Structure section containing full file
     * tree.
     *
     * @param aiContextFile generated markdown content from AI
     * @param report        Git report containing file list
     * @return enriched markdown content
     */
    public String enhance(String aiContextFile, GitReport report) {
        if (aiContextFile == null) {
            return null;
        }

        log.info("Starting AI Context File post-processing - adding Project Structure");

        String tree = directoryTreePayloadWriter.generate(report);

        StringBuilder sb = new StringBuilder(aiContextFile);

        // Add new line if document does not end with one
        if (!aiContextFile.endsWith("\n")) {
            sb.append("\n");
        }

        sb.append("\n## Project Structure\n\n");
        sb.append("```\n");
        sb.append(tree);
        sb.append("```\n");

        log.debug("Project Structure section added to document");
        return sb.toString();
    }
}

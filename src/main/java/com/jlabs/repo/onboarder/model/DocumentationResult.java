package com.jlabs.repo.onboarder.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentationResult {

    /**
     * Map storing generated documents.
     * Key: document type name (e.g., "README.md", "Refactorings")
     * Value: document content in Markdown format
     */
    private Map<String, String> documents = new LinkedHashMap<>();

    public void addDocument(String type, String content) {
        this.documents.put(type, content);
    }
}

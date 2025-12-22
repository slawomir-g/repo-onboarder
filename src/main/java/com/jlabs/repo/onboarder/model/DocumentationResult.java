package com.jlabs.repo.onboarder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Model danych reprezentujący wynik generacji dokumentacji przez AI.
 * Zawiera trzy typy dokumentacji zgodnie z PRD:
 * - README.md - przegląd projektu i instrukcje setupu
 * - Architecture Documentation - szczegółowy opis architektury
 * - Context File - zoptymalizowany plik kontekstu dla AI agentów
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentationResult {

    /**
     * Zawartość README.md w formacie Markdown.
     * Zawiera przegląd projektu, instrukcje setupu i kluczowe informacje dla deweloperów.
     */
    private String readme;

    /**
     * Dokumentacja architektury w formacie Markdown.
     * Zawiera szczegółowy opis architektury, wzorców projektowych i struktury projektu.
     */
    private String architecture;

    /**
     * Plik kontekstu zoptymalizowany dla AI coding assistants.
     * Zawiera strukturę projektu, konwencje kodowania i kluczowe informacje.
     */
    private String aiContextFile;

    public DocumentationResult() {
    }

    public String getReadme() {
        return readme;
    }

    public void setReadme(String readme) {
        this.readme = readme;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getAiContextFile() {
        return aiContextFile;
    }

    public void setAiContextFile(String contextFile) {
        this.aiContextFile = contextFile;
    }
}


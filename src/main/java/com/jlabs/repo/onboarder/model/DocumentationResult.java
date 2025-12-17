package com.jlabs.repo.onboarder.model;

/**
 * Model danych reprezentujący wynik generacji dokumentacji przez AI.
 * Zawiera trzy typy dokumentacji zgodnie z PRD:
 * - README.md - przegląd projektu i instrukcje setupu
 * - Architecture Documentation - szczegółowy opis architektury
 * - Context File - zoptymalizowany plik kontekstu dla AI agentów
 */
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
    private String contextFile;

    public DocumentationResult() {
    }

    public DocumentationResult(String readme, String architecture, String contextFile) {
        this.readme = readme;
        this.architecture = architecture;
        this.contextFile = contextFile;
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

    public String getContextFile() {
        return contextFile;
    }

    public void setContextFile(String contextFile) {
        this.contextFile = contextFile;
    }
}


package com.jlabs.repo.onboarder.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model danych reprezentujący wynik generacji dokumentacji przez AI.
 * Zawiera trzy typy dokumentacji zgodnie z PRD:
 * - README.md - przegląd projektu i instrukcje setupu
 * - Architecture Documentation - szczegółowy opis architektury
 * - Context File - zoptymalizowany plik kontekstu dla AI agentów
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentationResult {

    /**
     * Mapa przechowująca wygenerowane dokumenty.
     * Klucz: nazwa typu dokumentu (np. "README.md", "Refactorings")
     * Wartość: zawartość dokumentu w formacie Markdown
     */
    private Map<String, String> documents = new LinkedHashMap<>();

    public void addDocument(String type, String content) {
        this.documents.put(type, content);
    }
}

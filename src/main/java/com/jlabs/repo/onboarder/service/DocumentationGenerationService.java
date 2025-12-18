package com.jlabs.repo.onboarder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import com.jlabs.repo.onboarder.service.exceptions.AiResponseParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;


@Service
public class DocumentationGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationGenerationService.class);

    private final ChatModelClient chatModelClient;
    private final PromptConstructionService promptConstructionService;
    private final ObjectMapper objectMapper;

    public DocumentationGenerationService(
            ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService,
            ObjectMapper objectMapper) {
        this.chatModelClient = chatModelClient;
        this.promptConstructionService = promptConstructionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Generuje dokumentację dla repozytorium używając AI modelu.
     * 
     * @param report raport z analizy Git repozytorium
     * @param repoRoot ścieżka do katalogu głównego repozytorium
     * @return wynik generacji dokumentacji zawierający README, Architecture i Context File
     */
    public DocumentationResult generateDocumentation(GitReport report, Path repoRoot) {
        // Konstruuj prompt
        String promptText = promptConstructionService.constructPrompt(report, repoRoot);
        saveDebugFile("prompt_debug.txt", promptText);

        // Wywołaj API przez ChatModelClient (retry i obsługa błędów są enkapsulowane w kliencie)
        String responseText = chatModelClient.call(promptText);
        saveDebugFile("response_debug.txt", responseText);

        // Parsuj odpowiedź JSON
        return parseResponse(responseText);
    }

    /**
     * Parsuje odpowiedź JSON z API i tworzy DocumentationResult.
     * Zgodnie z PRD, odpowiedź powinna zawierać pola: readme, architecture, contextFile.
     * Obsługuje odpowiedzi zawierające JSON w markdown code block (```json ... ```).
     * 
     * @param responseText tekst odpowiedzi z API (może zawierać JSON w markdown code block)
     * @return sparsowany DocumentationResult
     * @throws AiResponseParseException gdy nie można sparsować JSON lub brakuje wymaganych pól
     */
    private DocumentationResult parseResponse(String responseText) {
        try {
            // Usuń markdown code block jeśli istnieje (```json ... ```)
            String cleanedJson = extractJsonFromMarkdown(responseText);
            return objectMapper.readValue(cleanedJson, DocumentationResult.class);

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AiResponseParseException(
                    "Nie można sparsować odpowiedzi JSON z API: " + e.getMessage(), e);
        }
    }

    /**
     * Wyciąga JSON z markdown code block jeśli jest opakowany w ```json ... ```.
     * Jeśli odpowiedź nie zawiera markdown code block, zwraca oryginalny tekst.
     * 
     * @param responseText tekst odpowiedzi z API
     * @return wyczyszczony JSON bez markdown code block
     */
    private String extractJsonFromMarkdown(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return responseText;
        }

        // Usuń białe znaki na początku i końcu
        String trimmed = responseText.trim();

        // Sprawdź czy zaczyna się od ```json lub ```
        if (trimmed.startsWith("```json")) {
            // Znajdź początek JSON (po ```json)
            int jsonStart = trimmed.indexOf("```json") + 7;
            // Znajdź koniec (```)
            int jsonEnd = trimmed.lastIndexOf("```");
            
            if (jsonEnd > jsonStart) {
                String jsonContent = trimmed.substring(jsonStart, jsonEnd).trim();
                logger.debug("Wyciągnięto JSON z markdown code block, długość: {} znaków", jsonContent.length());
                return jsonContent;
            }
        } else if (trimmed.startsWith("```")) {
            // Obsługa przypadku gdy jest tylko ``` bez json
            int jsonStart = trimmed.indexOf("```") + 3;
            int jsonEnd = trimmed.lastIndexOf("```");
            
            if (jsonEnd > jsonStart) {
                String jsonContent = trimmed.substring(jsonStart, jsonEnd).trim();
                logger.debug("Wyciągnięto JSON z markdown code block (bez json), długość: {} znaków", jsonContent.length());
                return jsonContent;
            }
        }

        // Jeśli nie ma markdown code block, zwróć oryginalny tekst
        return trimmed;
    }

    /**
     * Zapisuje zawartość do pliku w katalogu working_directory w celach debugowania.
     * Metoda nie przerywa głównego flow w przypadku błędów - tylko loguje ostrzeżenia.
     * 
     * @param filename nazwa pliku do zapisania
     * @param content zawartość do zapisania
     */
    private void saveDebugFile(String filename, String content) {
        try {
            Path appWorkingDir = Path.of(System.getProperty("user.dir"), "working_directory");
            Files.createDirectories(appWorkingDir);
            Path debugFile = appWorkingDir.resolve(filename);
            Files.writeString(debugFile, content, StandardCharsets.UTF_8);
            logger.debug("Zapisano plik debugowania: {}", debugFile);
        } catch (Exception e) {
            logger.warn("Nie udało się zapisać pliku debugowania {}: {}", filename, e.getMessage());
        }
    }
}


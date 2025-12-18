package com.jlabs.repo.onboarder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import com.jlabs.repo.onboarder.service.exceptions.AiResponseParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;


@Service
public class DocumentationGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationGenerationService.class);

    private final ChatModelClient chatModelClient;
    private final PromptConstructionService promptConstructionService;
    private final RepositoryCacheService repositoryCacheService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public DocumentationGenerationService(
            ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService,
            RepositoryCacheService repositoryCacheService,
            AiProperties aiProperties,
            ObjectMapper objectMapper) {
        this.chatModelClient = chatModelClient;
        this.promptConstructionService = promptConstructionService;
        this.repositoryCacheService = repositoryCacheService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Generuje dokumentację dla repozytorium używając AI modelu z wykorzystaniem cache.
     * 
     * Flow:
     * 1. Sprawdza czy cache dla repozytorium już istnieje
     * 2a. Jeśli istnieje - używa cached content (oszczędność kosztów i czasu)
     * 2b. Jeśli nie istnieje - tworzy nowy cache z repository context XML
     * 3. Wywołuje API z cached content lub normalnym promptem
     * 4. Parsuje odpowiedź JSON
     * 
     * Cache zawiera repository context XML (directory tree, hotspots, commits, source code)
     * i jest identyfikowany przez URL repozytorium. Automatycznie wygasa po skonfigurowanym TTL.
     * 
     * @param report raport z analizy Git repozytorium
     * @param repoRoot ścieżka do katalogu głównego repozytorium
     * @return wynik generacji dokumentacji zawierający README, Architecture i Context File
     */
    public DocumentationResult generateDocumentation(GitReport report, Path repoRoot) {
        String repoUrl = report.repo.url;
        String model = aiProperties.getChat().getOptions().getModel();
        
        logger.info("Rozpoczęcie generacji dokumentacji dla repo: {}", repoUrl);
        
        // 1. Sprawdź czy cache dla repozytorium już istnieje
        Optional<String> cachedContentName = repositoryCacheService.getCachedContentName(repoUrl);
        
        String promptText;
        GoogleGenAiChatOptions chatOptions = null;
        
        if (cachedContentName.isPresent()) {
            // 2a. Cache istnieje - użyj cached content
            logger.info("Używanie istniejącego cache dla repo: {}", repoUrl);
            
            // Konstruuj uproszczony prompt (bez repository context, bo jest w cache)
            promptText = promptConstructionService.constructPromptWithCache(cachedContentName.get());
            
            // Utwórz opcje z referencją do cached content
            chatOptions = GoogleGenAiChatOptions.builder()
                    .useCachedContent(true)
                    .cachedContentName(cachedContentName.get())
                    .build();
            
        } else {
            // 2b. Cache nie istnieje - spróbuj utworzyć nowy cache
            logger.info("Cache nie istnieje dla repo: {}, próba utworzenia nowego...", repoUrl);
            
            // Przygotuj repository context XML
            String repoContextXml = promptConstructionService.prepareRepositoryContext(report, repoRoot);
            saveDebugFile("ai_context_prompt_debug.txt", repoContextXml);
            
            // Spróbuj utworzyć cached content
            String newCacheName = repositoryCacheService.createCachedContent(repoUrl, repoContextXml, model);
            
            if (newCacheName != null) {
                // Cache został utworzony - użyj go
                logger.info("Cache został utworzony pomyślnie, używanie cached content");
                
                // Konstruuj prompt z referencją do nowo utworzonego cache
                promptText = promptConstructionService.constructPromptWithCache(newCacheName);
                
                // Utwórz opcje z referencją do cached content
                chatOptions = GoogleGenAiChatOptions.builder()
                        .useCachedContent(true)
                        .cachedContentName(newCacheName)
                        .build();
            } else {
                // Cache nie jest dostępny - użyj tradycyjnego promptu z pełnym kontekstem
                logger.info("Cache nie jest dostępny, używanie tradycyjnego promptu z pełnym kontekstem");
                promptText = promptConstructionService.constructPrompt(report, repoRoot);
                chatOptions = null; // Użyj domyślnych opcji
            }
        }
        
        saveDebugFile("prompt_debug.txt", promptText);

        // 3. Wywołaj API z cached content przez ChatModelClient
        String responseText = chatModelClient.call(promptText, chatOptions);
        saveDebugFile("response_debug.txt", responseText);

        // 4. Parsuj odpowiedź JSON
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


package com.jlabs.repo.onboarder.service;

import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;

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

    public DocumentationGenerationService(
            ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService,
            RepositoryCacheService repositoryCacheService,
            AiProperties aiProperties) {
        this.chatModelClient = chatModelClient;
        this.promptConstructionService = promptConstructionService;
        this.repositoryCacheService = repositoryCacheService;
        this.aiProperties = aiProperties;
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
     * Parsuje odpowiedź z API i tworzy DocumentationResult.
     * Zgodnie z nowymi wymaganiami, odpowiedź jest czystym tekstem Markdown.
     * 
     * @param responseText tekst odpowiedzi z API
     * @return DocumentationResult z wypełnionym polem aiContextFile
     */
    private DocumentationResult parseResponse(String responseText) {
        // Usuń markdown code block jeśli model go dodał (np. ```markdown ... ```)
        String cleanedContent = extractMarkdownFromCodeBlock(responseText);
        
        DocumentationResult result = new DocumentationResult();
        result.setAiContextFile(cleanedContent);
        // Pola readme i architecture pozostają puste, ponieważ obecnie generujemy 
        // tylko skonsolidowany plik kontekstu AI.
        
        return result;
    }

    /**
     * Wyciąga czystą treść z bloku kodu markdown, jeśli odpowiedź została w niego opakowana.
     * Obsługuje bloki typu ```markdown, ``` lub po prostu zwraca tekst, jeśli nie ma bloków.
     * 
     * @param responseText tekst odpowiedzi z API
     * @return wyczyszczona treść markdown
     */
    private String extractMarkdownFromCodeBlock(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return responseText;
        }

        String trimmed = responseText.trim();

        // Sprawdź czy zaczyna się od ```markdown, ```json lub po prostu ```
        String[] prefixes = {"```markdown", "```json", "```"};
        for (String prefix : prefixes) {
            if (trimmed.startsWith(prefix)) {
                int start = trimmed.indexOf(prefix) + prefix.length();
                int end = trimmed.lastIndexOf("```");
                
                if (end > start) {
                    String content = trimmed.substring(start, end).trim();
                    logger.debug("Wyciągnięto treść z bloku kodu {}, długość: {} znaków", prefix, content.length());
                    return content;
                }
            }
        }

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


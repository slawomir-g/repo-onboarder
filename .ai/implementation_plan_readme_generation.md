# Implementation Plan: Human Documentation (README.md) Generation

This plan outlines the steps to implement the generation of human-readable documentation (`README.md`) in addition to the existing AI Context File. The implementation focuses on reusing the existing AI repository context cache and parameterized prompt construction.

## Current progress / Stan na dziś (2025-12-19)

Poniżej jest stan faktyczny w repozytorium (na podstawie istniejącego kodu i zasobów), żeby plan był “żywy” i jednoznaczny.

- **DONE: README prompt templates są dodane**
  - `src/main/resources/prompts/readme-prompt-template.md` istnieje
  - `src/main/resources/prompts/readme-documentation-template.md` istnieje

- **DONE: `PromptConstructionService` jest już sparametryzowany**
  - `constructPrompt(...)` ma przeciążenie przyjmujące `promptTemplatePath` i `documentationTemplatePath`
  - `constructPromptWithCache(...)` ma przeciążenie przyjmujące `promptTemplatePath` i `documentationTemplatePath`
  - Zachowana kompatybilność wsteczna: istnieją stare signatury delegujące do wersji parametryzowanych.
  - Dodane helpery do ścieżek README (`getReadmePromptTemplatePath()`, `getReadmeDocumentationTemplatePath()`).

- **PENDING: `DocumentationGenerationService` nadal generuje tylko AI Context File**
  - Aktualnie `generateDocumentation(...)` buduje jeden prompt i `parseResponse(...)` ustawia wyłącznie `aiContextFile`.
  - Pole `readme` w `DocumentationResult` pozostaje puste (a `GitCoreRunner` zapisze `README.md` tylko jeśli `readme` jest niepuste).

- **READY (po kroku 3): API/DTO są przygotowane na README**
  - `DocumentationResult` ma pole `readme` + getter/setter, więc po ustawieniu wartości w serwisie pole pojawi się w JSON bez dodatkowych zmian w kontrolerze.

- **Uwaga (do weryfikacji w trakcie kroku 3): `readme-prompt-template.md`**
  - Szablon zawiera `$DOCUMENTATION_TEMPLATE$` dwa razy (raz w sekcji “README structure…”, drugi raz w sekcji “Documentation template”). To nie blokuje implementacji, ale warto zdecydować, czy to celowe (powielenie instrukcji) czy do uproszczenia.

## 1. Create Prompt Templates for README

We need separate templates for the README generation instructions to distinguish it from the AI Context File generation.

**Files to create:**
*   `src/main/resources/prompts/readme-prompt-template.md`: The outer prompt wrapper (similar to `ai-context-prompt-template.md`).
*   `src/main/resources/prompts/readme-documentation-template.md`: The specific instructions for generating the README content.

**Template Structure (Industry Best Practices):**
The `readme-documentation-template.md` will instruct the AI to generate a README with the following standard sections:
1.  **Project Title & Description**: Clear statement of what the project does.
2.  **Key Features**: Bulleted list of main capabilities.
3.  **Technology Stack**: Languages, frameworks, and key libraries used.
4.  **Prerequisites**: Tools required to build/run the project (e.g., Java version, Node.js, Docker).
5.  **Installation & Setup**: Step-by-step guide to get the development environment running.
6.  **Configuration**: Explanation of key configuration files (e.g., `application.yml`, `.env`).
7.  **Usage**: Basic examples of how to run or use the application.
8.  **Architecture Overview**: Brief high-level description of the system design.

**Action:**
1.  Copy `ai-context-prompt-template.md` to `readme-prompt-template.md`.
2.  Create `readme-documentation-template.md` with the specific instructions listed above.

## 2. Refactor `PromptConstructionService`

The service currently hardcodes the template paths. We need to parametrize these to support different documentation types.

**Modifications:**
1.  **Update `constructPrompt` signature:**
    ```java
    // Old
    public String constructPrompt(GitReport report, Path repoRoot)
    
    // New
    public String constructPrompt(GitReport report, Path repoRoot, String promptTemplatePath, String documentationTemplatePath)
    ```
2.  **Update `constructPromptWithCache` signature:**
    ```java
    // Old
    public String constructPromptWithCache(String cachedContentName)
    
    // New
    public String constructPromptWithCache(String cachedContentName, String promptTemplatePath, String documentationTemplatePath)
    ```
3.  **Update `loadDocumentationTemplate`:**
    *   Change it to accept the path parameter instead of using the constant `AI_CONTEXT_DOCUMENTATION_TEMPLATE_PATH`.
4.  **Refactor Constants:**
    *   Keep the existing constants as defaults or helper constants for the caller, but remove their hardcoded usage inside the methods.

## 3. Refactor `DocumentationGenerationService`

The service needs to orchestrate the generation of multiple documents while efficiently managing the repository context cache.

**Modifications:**
1.  **Extract Cache Management Logic:**
    *   Create a helper method `String ensureRepositoryContentCache(GitReport report, Path repoRoot)` that handles the logic of checking/creating the Google GenAI cache and returning the cache name.
2.  **Extract Single Document Generation Logic:**
    *   Create a helper method `String generateSingleDocument(String cacheName, String promptTemplatePath, String docTemplatePath)` that:
        *   Constructs the prompt (using the parameterized `PromptConstructionService`).
        *   Calls `chatModelClient`.
        *   Parses the Markdown response.
3.  **Update `generateDocumentation` Flow:**
    *   Call `ensureRepositoryContentCache` to get the cache name.
    *   **Generate AI Context File:** Call `generateSingleDocument` with AI Context templates.
    *   **Generate README:** Call `generateSingleDocument` with README templates.
    *   **Assemble Result:** Populate `DocumentationResult` with both generated contents.

## 4. Verify API Response

Since `GitCoreController` directly returns the `DocumentationResult` object (serialized to JSON), populating the `readme` field in Step 3 will automatically include it in the JSON response.

**Expected JSON Response Structure:**
```json
{
  "readme": "# Project Name\n## Description...",
  "architecture": null,
  "aiContextFile": "# AI Context\n..."
}
```

## 5. Execution Steps

1.  **Create Templates**: Add the new `.md` files in `src/main/resources/prompts/` implementing the industry best practices structure. *(DONE)*
2.  **Apply Refactoring**: Modify `PromptConstructionService.java`. *(DONE)*
3.  **Implement Logic**: Update `DocumentationGenerationService.java` with the new flow. *(PENDING)*
4.  **Test**: Run the analysis and verify both fields are populated in the result. *(PENDING)*

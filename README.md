# repo-onboarder

## Description

Repo Onboarder is an AI-powered documentation generation service designed to accelerate developer onboarding to new software projects. It automates the process of understanding a complex codebase by analyzing public Git repositories, extracting structural metadata, and leveraging Large Language Models (LLMs) to produce comprehensive guides.

The project exists to solve the "cold start" problem for developers joining a new team. Instead of spending days manually exploring directories and commit history, developers can use Repo Onboarder to generate human-readable architecture overviews, refactoring plans, and optimized context files for AI coding assistants like GitHub Copilot or Cursor.

## How it works?

The application follows a systematic pipeline to transform a raw Git repository into structured knowledge:

1.  **Repository Acquisition**: The system clones the target public repository into a temporary working directory using JGit.
2.  **Structural & Temporal Analysis**: It scans the file system to build a directory tree and analyzes Git logs to identify "hotspots" (frequently modified files) and recent development trends.
3.  **Context Assembly**: The system extracts source code (filtering out noise and tests if requested) and bundles it with metadata into a large XML-based "Repository Context."
4.  **AI Context Caching**: To optimize costs and speed, the context is uploaded to Google Gemini's Cached Content API, allowing multiple specialized prompts to reuse the same codebase data.
5.  **Specialized Generation**: Multiple generation services (README, Refactoring, DDD, Quality Assessment) query the AI model using specific templates.
6.  **Post-Processing & Delivery**: The generated Markdown is enriched with programmatically generated sections (like the full file tree) and returned via a REST API or displayed in the built-in Web UI.

## Key Features

- **Automated README Generation**: Creates high-level overviews, installation steps, and architecture descriptions.
- **AI Agent Context Optimization**: Generates `.md` files specifically designed to be fed into IDE-based AI assistants to provide them with project-specific "expert" knowledge.
- **Git Hotspot Detection**: Identifies the most volatile parts of the codebase to warn developers about complex or high-maintenance areas.
- **DDD Refactoring Analysis**: Suggests improvements based on Domain-Driven Design principles, identifying potential bounded contexts and aggregates.
- **Quality Assessment**: Provides an "expert" audit of code quality, SOLID principles, and architectural consistency.
- **Context Caching**: Utilizes Gemini's 1M+ token context window efficiently by caching the codebase, reducing latency and API costs.

## Dependencies

- **Google Gemini API**: Used as the core reasoning engine (specifically Gemini 1.5 Pro or 2.0 Flash).
- **Spring AI**: Provides the abstraction layer for interacting with LLMs.

## Flows

### Documentation Generation Flow

```text
[User Request]
      |
      v
[GitCoreRunner] ----> [GitRepositoryManager] (Clone/Pull)
      |                       |
      v                       v
[Analysis Services] <--- [JGit] (File Tree, Commits, Hotspots)
      |
      v
[DocumentationGenerationService]
      |
      +---> [RepositoryCacheService] (Upload context to Gemini Cache)
      |
      +---> [DocumentGenerators] (Strategy Pattern: README, DDD, Refactor)
      |           |
      |           v
      |     [ChatModelClient] (Spring AI + Exponential Backoff Retry)
      |
      v
[Post-Processing] (Inject Directory Tree/Metadata)
      |
      v
[REST API Response / Web UI Display]
```

## Architecture Overview

The project is a Spring Boot 4.0 application following a modular service-oriented architecture:

- **API Layer**: `GitCoreController` provides the entry point for analysis requests.
- **Git Core**: Handles low-level repository operations, metadata collection, and hotspot calculation.
- **AI Infrastructure**: Wraps Spring AI with resilient retry logic (`ChatModelClient`) and manages Google GenAI specific features like content caching.
- **Service Layer**: Orchestrates the analysis and documentation generation using a Strategy pattern where each document type has its own specialized generator.
- **Markdown Engine**: Programmatically generates XML payloads and Markdown structures from Git metadata.

## Package structure

```text
com.jlabs.repo.onboarder
├── api                      // REST Controllers
├── config                   // Spring Configuration & Properties
├── git                      // Git analysis & Repository management
├── infrastructure           // Spring AI & LLM communication
│   └── springai             // Gemini-specific client implementation
├── markdown                 // Payload writers for AI prompts
├── model                    // Data transfer objects and Git reports
└── service                  // Core business logic
    ├── exceptions           // Domain-specific error handling
    └── generator            // Specialized AI document generators
```

## Technology Stack

- **Language**: Java 21 / 25
- **Framework**: Spring Boot 4.0.0
- **AI Framework**: Spring AI 1.1.2
- **LLM Model**: Google Gemini 1.5 Pro / 2.0 Flash
- **Git Library**: Eclipse JGit 6.9.0
- **Build Tool**: Gradle 8.14
- **Documentation**: SpringDoc OpenAPI (Swagger UI)

## Prerequisites

- **Java 21** or higher.
- **Google Gemini API Key** (obtained from Google AI Studio).
- **Docker** (optional, for containerized deployment).

## Installation & Setup

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/jlabs/repo-onboarder.git
    cd repo-onboarder
    ```
2.  **Set your API Key**:
    Add your key to `src/main/resources/application.yml` or set an environment variable:
    ```bash
    export SPRING_AI_GOOGLE_GENAI_API_KEY=your_api_key_here
    ```
3.  **Build and Run**:
    ```bash
    ./gradlew bootRun
    ```

## Configuration

Configuration is managed via `application.yml`. Key properties include:

- `spring.ai.google.genai.chat.options.model`: The Gemini model to use.
- `spring.ai.google.genai.chat.enable-cached-content`: Enables/disables Gemini context caching (default: true).
- `git-core.workdir`: The directory where repositories are cloned.
- `git-core.limits`: Constraints on commit history depth and file changes to manage prompt size.

## Usage

### Web Interface

Once running, navigate to `http://localhost:8080` to use the interactive dashboard. Enter a public Git URL to generate documentation.

### API Usage

You can trigger an analysis via `curl`:

```bash
curl -X POST "http://localhost:8080/api/git-core/run?repoUrl=https://github.com/user/repo&targetLanguage=English"
```

### Swagger UI

Detailed API documentation is available at `http://localhost:8080/swagger-ui.html`.

## Hotspots (High-Churn Files)

- `src/main/java/com/jlabs/repo/onboarder/service/DocumentationGenerationService.java` (churn_score: 1470) - Main orchestrator for AI logic.
- `src/main/java/com/jlabs/repo/onboarder/service/PromptConstructionService.java` (churn_score: 1183) - Handles complex XML/Markdown prompt building.
- `src/main/java/com/jlabs/repo/onboarder/infrastructure/springai/ChatModelClient.java` (churn_score: 701) - Core AI communication logic.

## Recent Development Focus

Recent activity has focused on:

- **Resiliency**: Implementation of Spring Retry with exponential backoff for AI calls.
- **Cost Optimization**: Integration of Google GenAI Cached Content API.
- **Extensibility**: Refactoring generators into a strategy-based service list.
- **UX**: Enhancing the Web UI with selectable languages and Markdown copying.

## Key Contributors

- **Sławomir Gefert**: Lead Architect and developer of the AI orchestration and service layer.
- **lscpiotrowski**: Core contributor to Git metadata collection and REST API infrastructure.

## Potential Complexity/Areas to Note

- **AI Context Management**: The `PromptConstructionService` creates massive XML payloads. Understanding how these are tokensized and cached is critical for performance.
- **Token Limits**: While Gemini has a large window, the `SourceCodeCorpusPayloadWriter` must be carefully managed for very large repositories.
- **Resilience Strategy**: The system relies heavily on `ChatModelClient`'s retry logic to handle 429 (Rate Limit) errors from the Google API.

## Helpful Resources

- **Documentation:** Project documentation found in the `docs/` directory.
- **Issue Tracker:** [GitHub Issues](https://github.com/jlabs/repo-onboarder/issues)
- **Spring AI Reference:** [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)

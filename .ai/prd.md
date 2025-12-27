# Product Requirements Document (PRD) - Repo Onboarder

## 1. Product Overview

Repo Onboarder is an AI-powered documentation generation service designed to accelerate developer onboarding to new projects.
The application analyzes public Git repositories, extracts project structure and development history, and generates comprehensive documentation for both human developers and AI coding assistants.

The system operates as a REST API service built on Java 21 and Spring Boot 4, utilizing Google's Gemini 2.5 Pro model via Spring AI framework.
The application is distributed as a locally executable JAR file, allowing users to run it on their own infrastructure with their own API credentials.

Key capabilities include:

- Automated analysis of repository structure and codebase
- Git history analysis to identify frequently modified files (hotspots)
- Generation of human-readable documentation in Markdown format (including Architecture overview)
- Generation of optimized context files for AI coding assistants (e.g., GitHub Copilot, IntelliJ AI Assistant)
- Support for various technology stacks (including Java/Maven and JavaScript/TypeScript/NPM) via AI-driven detection
- Asynchronous processing model support (deferred to future, currently synchronous)

The MVP focuses on public repositories and single-module projects.

## 2. User Problem

Onboarding to a new software project is a time-consuming and challenging process that can take days or weeks. Developers face several critical problems:

1. Lack of Documentation: Many projects lack comprehensive documentation, forcing developers to read through entire codebases to understand architecture, patterns, and conventions.

2. Information Overload: Developers get lost in complex file structures, unable to identify which files are most important or frequently modified.

3. Missing Context: Developers lack understanding of the project's development history, making it difficult to identify active areas of development and architectural decisions.

4. AI Assistant Limitations: AI coding assistants operating in IDEs lack project-specific context, reducing their effectiveness in providing relevant suggestions and assistance.

5. Time Investment: Traditional onboarding requires extensive manual code review and documentation reading, significantly delaying productivity.

Repo Onboarder addresses these problems by automatically generating high-quality, context-aware documentation that reduces onboarding time from days/weeks to hours, enabling developers to become productive faster.

## 3. Functional Requirements

### 3.1 REST API Endpoints

#### 3.1.1 POST /api/git-core/run

Initiates repository analysis and waits for completion. Returns the generated documentation immediately in the response body.

Input Parameters (Query Parameters):

- repoUrl (required): Valid Git repository URL (public repositories only)
- branch (optional): Branch to analyze (default: based on configuration)
- withTest (optional): Whether to include test files in analysis (boolean, default: false)

Response:

- 200 OK: JSON object containing generated documentation (DocumentationResult)
  - documents: Map of document types to their content. Keys: "README", "Refactorings", "DDD Refactoring", "AI Context".
- 500 Internal Server Error: Error message if analysis fails

### 3.2 Repository Analysis

#### 3.2.1 Repository Cloning

The system must clone public Git repositories to a temporary working directory. The cloning process must:

- Support standard Git URL formats (HTTPS)
- Handle cloning failures gracefully with appropriate error messages
- Clean up temporary directories after processing (unless debug mode is enabled)

#### 3.2.2 Technology Stack Detection

The system delegates technology stack detection to the AI model. The Java backend provides the repository structure and configuration files (like pom.xml, package.json, etc.) as part of the context, and the AI model identifies the patterns and conventions.

#### 3.2.3 File Structure Analysis

The system must analyze the repository structure to:

- Map directory hierarchy
- Identify source files vs. configuration files vs. documentation files
- Apply file filtering based on built-in exclusion lists and user configuration
- Extract relevant file content for documentation generation

#### 3.2.4 Dependency Analysis

The system does not perform explicit Java-side parsing of dependency files. Instead, it includes relevant configuration files (e.g., pom.xml, package.json, build.gradle) in the AI prompt context, allowing the model to perform dependency analysis.

#### 3.2.5 Git History Analysis

The system must analyze Git commit history to:

- Identify files that are most frequently modified (hotspots)
- Calculate change frequency metrics
- Use this information as additional context for the AI model

### 3.3 AI-Powered Documentation Generation

#### 3.3.1 Prompt Construction

The system must build a comprehensive prompt containing:

- Project structure and file hierarchy
- Source file contents (filtered and processed)
- Dependency information (raw configuration files)
- Git history hotspots data
- Instructions for generating specific document types

#### 3.3.2 AI Model Integration

The system communicates with Gemini 2.5 Pro via Spring AI framework:

- **Infrastructure Layer (`ChatModelClient`):**
  - Encapsulates all details of communication with Spring AI `ChatModel`
  - Implements exponential backoff retry strategy using Spring Retry framework
  - Uses declarative retry configuration via `@Retryable` annotation
  - Handles various error states (API errors, rate limits, authentication)
- **Service Layer (`DocumentGenerationService` hierarchy):**
  - Abstract base class defining the template method for generation
  - Concrete implementations for each document type
  - Focuses on prompt construction and response parsing

#### 3.3.3 Documentation Output

The system generates the following document types:

- **README**: Comprehensive project overview, including setup instructions, key information for developers, and **Architecture Overview**.
- **Refactorings**: Identification of code smells and suggestions for improvements.
- **DDD Refactoring**: Analysis of the domain model and suggestions for Domain-Driven Design improvements.
- **AI Context**: Optimized file for AI coding assistants containing project structure, conventions, and key information.

All documentation is returned as a JSON structure containing Markdown content.

### 3.4 Configuration and Customization

#### 3.4.1 File Exclusion Configuration

Users can configure custom file exclusion patterns via application properties.

#### 3.4.2 Debug Mode

The system supports developer mode with:

- `debug.save-prompts`: Save AI prompts to disk for inspection
- `clean=false`: Retain temporary working directories after processing

#### 3.4.3 API Key Configuration

Users must configure their Gemini API key via application properties.

### 3.5 Error Handling and Resilience

#### 3.5.1 Error Handling

The system handles:

- Invalid repository URLs
- Repository cloning failures
- API communication failures
- Rate limiting (via retries)
- Processing timeouts

#### 3.5.2 Resilience and Rate Limiting

The system relies on **Spring Retry** for resilience. Exponential backoff is configured to handle transient failures and respect API rate limits over time.

## 4. Product Boundaries

### 4.1 In Scope (MVP)

- Public Git repositories only
- Single-module projects
- AI-driven technology stack detection
- Source code analysis (filtered)
- Git history analysis (hotspots)
- Synchronous REST API
- Basic Web UI for viewing results
- JSON output format
- Local JAR distribution

### 4.2 Out of Scope (MVP)

- Complex Asynchronous Job Queues
- Private repositories requiring auth tokens
- Large repositories (>50k lines of code) optimization
- Multi-module projects support (explicitly)
- Real-time WebSocket updates

### 4.3 Technical Constraints

- Java 21 minimum
- Spring Boot 4
- Spring AI
- Spring Retry
- JGit for Git operations
- Gemini 2.5 Pro API

## 5. User Stories

### US-001: Analyze Repository (Synchronous)

As a developer, I want to submit a repository for analysis and receive the results in a single request.

### US-002: Handle Failures

As a developer, I want clear error messages for invalid URLs, cloning failures, or API issues.

### US-003: View Results

As a developer, I want to view the generated documentation (README, Refactorings, DDD Refactoring, AI Context) in a user-friendly way.

## 6. Implementation Status (Updated December 2025)

- **REST API Layer**: Implemented (`GitCoreController`).
- **Web Interface**: Implemented (`index.html`).
- **Git Operations**: Implemented (Cloning, Analysis, Hotspots).
- **AI Integration**: Implemented (`ChatModelClient` with Spring Retry).
- **Service Layer**: Strategy pattern implemented with concrete generators.
- **Documentation Types**: README (with Architecture), Refactorings, DDD Refactoring, AI Context.

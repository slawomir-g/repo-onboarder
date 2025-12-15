# Product Requirements Document (PRD) - Repo Onboarder

## 1. Product Overview

Repo Onboarder is an AI-powered documentation generation service designed to accelerate developer onboarding to new projects. 
The application analyzes public Git repositories, extracts project structure and development history, and generates comprehensive documentation for both human developers and AI coding assistants.

The system operates as a REST API service built on Java 21 and Spring Boot 3, utilizing Google's Gemini 2.5 Pro model via Spring AI framework. 
The application is distributed as a locally executable JAR file, allowing users to run it on their own infrastructure with their own API credentials.

Key capabilities include:
- Automated analysis of repository structure and codebase
- Git history analysis to identify frequently modified files (hotspots)
- Generation of human-readable documentation in Markdown format
- Generation of optimized context files for AI coding assistants (e.g., GitHub Copilot, IntelliJ AI Assistant)
- Support for Java/Maven and JavaScript/TypeScript/NPM projects
- Asynchronous processing model to handle long-running analysis tasks

The MVP focuses exclusively on public repositories and single-module projects, with support for Java and JavaScript/TypeScript technology stacks.

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

#### 3.1.1 POST /analyze
Initiates repository analysis process. Accepts repository URL and optional authentication token. Returns a job identifier (job_id) immediately, allowing asynchronous processing.

Input Parameters:
- repositoryUrl (required): Valid Git repository URL (public repositories only)

Response:
- jobId: Unique identifier for tracking the analysis job
- status: Initial status (typically "PENDING" or "PROCESSING")

#### 3.1.2 GET /results/{jobId}
Retrieves the status and results of an analysis job. Returns processing status and, upon completion, the generated documentation in JSON format.

Response Fields:
- status: Current job status (PENDING, PROCESSING, COMPLETED, FAILED)
- result: JSON object containing generated documentation (only present when status is COMPLETED)
  - readme: Markdown content for README.md
  - architecture: Markdown content for architecture documentation
  - contextFile: Optimized context file content for AI agents
- error: Error message (only present when status is FAILED)
- progress: Optional progress indicator or log messages

### 3.2 Repository Analysis

#### 3.2.1 Repository Cloning
The system must clone public Git repositories to a temporary working directory. The cloning process must:
- Support standard Git URL formats (HTTPS, SSH)
- Handle cloning failures gracefully with appropriate error messages
- Clean up temporary directories after processing (unless debug mode is enabled)

#### 3.2.2 Technology Stack Detection
The system must identify the project's technology stack by analyzing configuration files:
- Java/Maven: Detection via pom.xml presence
- JavaScript/TypeScript/NPM: Detection via package.json presence
- Support for single-module projects only in MVP
- Reject or handle multi-module projects appropriately

#### 3.2.3 File Structure Analysis
The system must analyze the repository structure to:
- Map directory hierarchy
- Identify source files vs. configuration files vs. documentation files
- Apply file filtering based on built-in exclusion lists and user configuration
- Extract relevant file content for documentation generation

#### 3.2.4 Dependency Analysis
The system must perform static analysis of dependency configuration files:
- For Maven projects: Parse pom.xml to extract dependencies
- For NPM projects: Parse package.json to extract dependencies
- No execution of build processes (static analysis only)

#### 3.2.5 Git History Analysis
The system must analyze Git commit history from the last 12 months to:
- Identify files that are most frequently modified (hotspots)
- Calculate change frequency metrics
- Use this information as additional context for the AI model (not as a separate documentation section)

### 3.3 AI-Powered Documentation Generation

#### 3.3.1 Prompt Construction
The system must build a single comprehensive prompt containing:
- Project structure and file hierarchy
- Source file contents (filtered and processed)
- Dependency information
- Git history hotspots data
- Instructions for generating documentation

#### 3.3.2 AI Model Integration
The system must communicate with Gemini 2.5 Pro via Spring AI framework:
- Enforce JSON format in responses
- Implement exponential backoff retry strategy for API failures
- Handle rate limiting with appropriate delays
- Manage API key configuration via application.properties

#### 3.3.3 Documentation Output
The system must generate three types of documentation:
- README.md: Comprehensive project overview, setup instructions, and key information for developers
- Architecture Documentation: Detailed architecture description, design patterns, and project structure
- Context File: Optimized file for AI coding assistants containing project structure, conventions, and key information

All documentation must be returned as JSON structure containing the Markdown content.

### 3.4 Configuration and Customization

#### 3.4.1 File Exclusion Configuration
Users must be able to configure custom file exclusion patterns:
- Configuration via application.properties or configuration file
- Merge with built-in exclusion lists
- Support for glob patterns or regex patterns

#### 3.4.2 Debug Mode
The system must support developer mode with:
- debug.save-prompts flag: Save AI prompts to disk for inspection
- clean=false flag: Retain temporary working directories after processing
- Enhanced console logging for development purposes

#### 3.4.3 API Key Configuration
Users must configure their Gemini API key:
- Configuration via application.properties file
- Secure handling of API credentials
- Clear error messages when API key is missing or invalid

### 3.5 Error Handling and Resilience

#### 3.5.1 Error Handling
The system must handle various error scenarios:
- Invalid repository URLs
- Repository cloning failures
- Unsupported technology stacks
- API communication failures
- Rate limiting from external APIs
- Processing timeouts

#### 3.5.2 Rate Limiting
The system must implement simple rate limiting:
- Thread.sleep-based rate limiting for API calls
- Configurable delays between requests
- Respect external API rate limits

#### 3.5.3 Progress Logging
The system must provide progress information:
- Console logging of processing stages
- Optional progress updates in API responses
- Clear indication of current processing step

## 4. Product Boundaries

### 4.1 In Scope (MVP)

- Public Git repositories only
- Single-module projects
- Java/Maven projects
- JavaScript/TypeScript/NPM projects
- Static dependency analysis (no build execution)
- Git history analysis (last 12 months)
- Asynchronous REST API
- JSON output format
- Local JAR distribution
- Console-based progress logging
- Configuration via application.properties

### 4.2 Out of Scope (MVP)

- Graphical User Interface (GUI)
- IDE Integration (IntelliJ, VS Code plugins)
- MCP Server implementation
- Large repositories (>50k lines of code) - no hard limit enforced, but not optimized
- Private repositories requiring authentication tokens
- Multi-module projects
- Other technology stacks (Python, Go, Ruby, etc.)
- Dynamic code analysis (running builds or tests)
- Automated testing in CI/CD pipeline
- User authentication and authorization
- Web-based dashboard
- Database persistence of results
- Result caching or history
- Real-time WebSocket updates
- Repository size validation and hard limits
- Advanced rate limiting algorithms
- Footers or disclaimers in generated documentation
- Updates for existing, already generated documentation

### 4.3 Technical Constraints

- Java 21 minimum requirement
- Spring Boot 3 framework
- Spring AI for LLM integration
- JGit for Git operations
- Gemini 2.5 Pro API access required
- Local execution environment
- Temporary disk space for repository cloning

### 4.4 Assumptions

- Users have valid Gemini API keys
- Users have sufficient disk space for repository cloning
- Public repositories are accessible without authentication
- Google Gemini API is available and accessible
- Network connectivity for cloning repositories and API calls
- Single-module project structure assumption
- Small size of repository that fits into LLM token limits

## 5. User Stories

### US-001: Submit Repository for Analysis

Description: As a developer, I want to submit a public repository URL for analysis so that I can receive generated documentation.

Acceptance Criteria:
- User can send POST request to /analyze endpoint with repositoryUrl parameter
- System validates repository URL format
- System returns jobId immediately (within 2 seconds)
- System returns status indicating job has been queued
- System initiates background processing of the repository
- Invalid URL format returns appropriate error response (400 Bad Request)

### US-002: Check Analysis Job Status

Description: As a developer, I want to check the status of my analysis job so that I know when documentation is ready.

Acceptance Criteria:
- User can send GET request to /results/{jobId} endpoint
- System returns current job status (PENDING, PROCESSING, COMPLETED, FAILED)
- System returns appropriate HTTP status codes
- Valid jobId returns status information
- Invalid jobId returns 404 Not Found error
- Response includes timestamp information

### US-003: Retrieve Generated Documentation

Description: As a developer, I want to retrieve the generated documentation when analysis is complete so that I can use it for onboarding.

Acceptance Criteria:
- When job status is COMPLETED, GET /results/{jobId} returns documentation in JSON format
- JSON response contains readme field with Markdown content
- JSON response contains architecture field with Markdown content
- JSON response contains contextFile field with optimized content for AI agents
- All documentation fields are non-empty strings
- Markdown content is properly formatted and valid
- Response is returned within 5 seconds of request

### US-004: Handle Invalid Repository URL

Description: As a developer, I want to receive clear error messages when I provide an invalid repository URL so that I can correct my input.

Acceptance Criteria:
- System validates URL format before processing
- Invalid URL format returns 400 Bad Request
- Error message clearly indicates URL format issue
- No background job is created for invalid URLs
- Error response includes descriptive error message

### US-005: Handle Repository Cloning Failure

Description: As a developer, I want to receive clear error messages when repository cloning fails so that I understand what went wrong.

Acceptance Criteria:
- System attempts to clone repository after job creation
- Cloning failures are caught and handled gracefully
- Job status is updated to FAILED when cloning fails
- Error message indicates cloning failure reason
- Temporary directories are cleaned up after failure
- Error response includes actionable information

### US-006: Handle Unsupported Technology Stack

Description: As a developer, I want to receive clear error messages when my repository uses an unsupported technology stack so that I understand the limitations.

Acceptance Criteria:
- System detects technology stack during analysis
- Unsupported stacks (not Java/Maven or JS/TS/NPM) are identified
- Job status is updated to FAILED with appropriate error message
- Error message clearly lists supported technology stacks
- Error occurs before AI processing to save API costs

### US-007: Handle Multi-Module Project

Description: As a developer, I want to understand when my project structure is not supported so that I can adjust my expectations.

Acceptance Criteria:
- System detects multi-module project structure
- Multi-module projects are rejected or handled appropriately
- Job status is updated to FAILED with clear error message
- Error message explains single-module requirement
- Detection occurs early in the process

### US-008: Handle Empty Repository

Description: As a developer, I want the system to handle empty repositories gracefully so that I receive appropriate feedback.

Acceptance Criteria:
- System detects empty repositories (no files or only .git directory)
- Empty repository is handled without crashing
- Job status is updated to FAILED or returns minimal documentation
- Error message indicates repository is empty
- System does not attempt AI processing for empty repositories

### US-009: Handle Repository with No Git History

Description: As a developer, I want the system to handle repositories without sufficient Git history so that analysis can still proceed.

Acceptance Criteria:
- System detects repositories with no commits or insufficient history
- Analysis proceeds without Git history data
- Hotspot analysis is skipped gracefully
- Documentation is generated using available project structure only
- No error is raised for missing Git history
- System logs warning about missing history

### US-010: Handle API Rate Limiting

Description: As a developer, I want the system to handle API rate limits gracefully so that my requests eventually complete successfully.

Acceptance Criteria:
- System detects rate limit errors from Gemini API
- Exponential backoff retry strategy is implemented
- System retries failed requests with increasing delays
- Maximum retry attempts are configured and enforced
- Job status reflects retry attempts in progress
- After maximum retries, job status is updated to FAILED
- Error message indicates rate limiting issue

### US-011: Handle Missing API Key

Description: As a developer, I want clear error messages when API key is missing so that I can configure it properly.

Acceptance Criteria:
- System checks for Gemini API key on startup
- Missing API key prevents job processing
- Error message clearly indicates missing API key
- Error message provides guidance on configuration location
- Error occurs before repository cloning to save resources

### US-012: Handle Invalid API Key

Description: As a developer, I want clear error messages when my API key is invalid so that I can correct it.

Acceptance Criteria:
- System validates API key during first API call
- Invalid API key results in job failure
- Error message indicates authentication failure
- Error message suggests checking API key configuration
- Job status is updated to FAILED

### US-013: Configure Custom File Exclusions

Description: As a developer, I want to configure custom file exclusion patterns so that irrelevant files are not included in analysis.

Acceptance Criteria:
- User can configure exclusion patterns in application.properties
- Custom exclusions are merged with built-in exclusion lists
- Exclusion patterns support glob or regex syntax
- Configuration is loaded on application startup
- Excluded files are not included in AI prompt
- Configuration changes require application restart

### US-014: Enable Debug Mode

Description: As a developer, I want to enable debug mode so that I can inspect prompts and temporary files during development.

Acceptance Criteria:
- User can set debug.save-prompts flag in configuration
- When enabled, AI prompts are saved to disk
- User can set clean=false flag to retain temporary directories
- Debug mode enables enhanced console logging
- Debug files are saved in predictable locations
- Debug mode does not affect production behavior when disabled

### US-015: View Progress Logging

Description: As a developer, I want to see progress information during analysis so that I know the system is working.

Acceptance Criteria:
- System logs progress messages to console
- Progress messages indicate current processing stage
- Logging includes: cloning, analysis, AI processing, completion
- Log messages are clear and informative
- Progress information is available for monitoring

### US-016: Handle Processing Timeout

Description: As a developer, I want the system to handle long-running processes gracefully so that resources are not indefinitely consumed.

Acceptance Criteria:
- System implements timeout mechanism for analysis jobs
- Timeout duration is configurable
- Jobs exceeding timeout are marked as FAILED
- Error message indicates timeout occurred
- Resources are cleaned up after timeout
- Temporary directories are removed after timeout

### US-017: Handle Large Repository Edge Case

Description: As a developer, I want the system to handle large repositories appropriately even though they are not optimized in MVP.

Acceptance Criteria:
- System processes repositories of various sizes
- No hard limit prevents processing
- System handles memory constraints gracefully
- Large repositories may take longer but should not crash
- Error messages indicate if repository is too large to process
- Performance degrades gracefully with size

### US-018: Generate Human-Readable Documentation

Description: As a developer, I want to receive well-formatted Markdown documentation so that I can quickly understand the project.

Acceptance Criteria:
- Generated README.md contains project overview
- Generated README.md includes setup instructions
- Generated README.md includes key project information
- Architecture documentation describes project structure
- Architecture documentation explains design patterns
- All Markdown is properly formatted and valid
- Documentation is comprehensive and useful

### US-019: Generate AI Agent Context File

Description: As a developer using AI coding assistants, I want to receive an optimized context file so that my AI assistant understands the project.

Acceptance Criteria:
- Context file contains project structure information
- Context file includes coding conventions and patterns
- Context file is optimized for AI consumption
- Context file format is appropriate for IDE integration
- Context file includes key architectural decisions
- Content level (full methods vs. signatures) is determined during implementation

### US-020: Process Java/Maven Project

Description: As a developer with a Java/Maven project, I want the system to correctly analyze my project structure and dependencies.

Acceptance Criteria:
- System detects Java/Maven project via pom.xml
- System parses pom.xml for dependency information
- System identifies Java source files correctly
- System applies appropriate file filtering for Java projects
- Generated documentation reflects Java/Maven project structure
- Maven-specific conventions are recognized

### US-021: Process JavaScript/TypeScript/NPM Project

Description: As a developer with a JavaScript/TypeScript/NPM project, I want the system to correctly analyze my project structure and dependencies.

Acceptance Criteria:
- System detects JS/TS/NPM project via package.json
- System parses package.json for dependency information
- System identifies JavaScript/TypeScript source files correctly
- System applies appropriate file filtering for JS/TS projects
- Generated documentation reflects NPM project structure
- NPM-specific conventions are recognized

### US-022: Analyze Git History Hotspots

Description: As a developer, I want the system to identify frequently modified files so that documentation reflects active development areas.

Acceptance Criteria:
- System analyzes Git commits from last 12 months
- System calculates file modification frequency
- Hotspot data is included in AI prompt as context
- Hotspot information influences documentation generation
- Analysis completes within reasonable time
- Git history analysis does not block on errors

### US-023: Filter Files Appropriately

Description: As a developer, I want irrelevant files excluded from analysis so that documentation focuses on important code.

Acceptance Criteria:
- Built-in exclusion list filters common irrelevant files (node_modules, .git, build artifacts, etc.)
- User-configured exclusions are applied
- Only source files and relevant configuration are analyzed
- Filtering reduces prompt size appropriately
- Filtered files are not included in documentation

### US-024: Return JSON Response Structure

Description: As a developer integrating with the API, I want consistent JSON response format so that I can parse results reliably.

Acceptance Criteria:
- All API responses are valid JSON
- Response structure is consistent across endpoints
- Error responses follow consistent format
- Success responses include all required fields
- JSON is properly formatted and parseable
- Response Content-Type header is application/json

### US-025: Handle Concurrent Job Requests

Description: As a developer, I want to submit multiple analysis jobs so that I can process multiple repositories.

Acceptance Criteria:
- System accepts multiple concurrent job requests
- Each job receives unique jobId
- Jobs are processed independently
- System handles concurrent processing without conflicts
- Resource usage scales appropriately
- No job interferes with another job's processing

## 6. Success Metrics

### 6.1 Technical Correctness Metrics

- System successfully clones public repositories without errors (target: >95% success rate for valid repositories)
- System correctly identifies technology stack (Java/Maven or JS/TS/NPM) for supported projects (target: 100% accuracy)
- System returns valid JSON responses for all API endpoints (target: 100% valid JSON)
- System handles errors gracefully without crashes (target: 0% unhandled exceptions)
- API response times for job creation under 2 seconds (target: <2s p95)
- API response times for result retrieval under 5 seconds when completed (target: <5s p95)

### 6.2 Documentation Quality Metrics

- Generated documentation contains accurate file structure mapping (target: >90% accuracy verified by manual review)
- Generated documentation includes sensible architecture descriptions for supported stacks (target: positive user feedback)
- Context files are properly formatted and usable by AI agents (target: successful integration in IDE environments)
- Documentation completeness: README, Architecture, and Context File are all generated (target: 100% completion rate)

### 6.3 User Experience Metrics

- Reduction in onboarding time from days/weeks to hours (target: >70% time reduction based on user feedback)
- User satisfaction with documentation quality (target: >80% positive feedback)
- Clear error messages enable users to resolve issues independently (target: <20% support requests for error resolution)
- System usability without requiring extensive documentation (target: users can start using system within 10 minutes)

### 6.4 System Reliability Metrics

- System uptime and availability (target: >99% when running locally)
- Successful job completion rate (target: >90% for valid repositories)
- API error rate (target: <5% of requests result in 500 errors)
- Resource cleanup: temporary directories are properly cleaned (target: 100% cleanup rate)

### 6.5 Performance Metrics

- Average job processing time for typical repositories (target: <10 minutes for repositories <10k lines)
- Memory usage remains within reasonable bounds (target: <2GB for typical repositories)
- Disk space usage for temporary files (target: cleaned up after processing)

### 6.6 Measurement Approach

- Technical metrics: Automated logging and monitoring
- Documentation quality: Manual review and user feedback surveys
- User experience: User interviews and feedback collection
- Performance: Application logging and profiling
- Success criteria validation: Regular review against defined targets

Note: MVP does not include automated testing in CI/CD, so metrics collection will be manual and based on user feedback and application logs.


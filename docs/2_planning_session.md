# PRD Planning Session Summary

## Decisions
1. **Business Model and Access:** MVP supports public repositories that doesn't require additional authentication. Cost on the user's side (own API key). Distribution as a locally executable JAR file.
2. **API Architecture:** REST API in an asynchronous model (returns job_id). Backend based on Java 21 + Spring Boot 3.
3. **AI Integration:** Utilization of **Gemini 2.5 Pro** model via **Spring AI** framework. "Single Prompt" strategy (one query with the full context).
4. **Output Format:** JSON containing the structure of generated Markdown files (for humans) and one optimized context file (for AI agents).
5. **Code Analysis:** Support for Java/Maven and JS/TS/NPM. Only single-module projects in MVP. Dependency analysis is static only.
6. **Git Usage:** Analysis of history from the last year to identify "hotspots" (most frequently changed files), serving solely as additional context for the model (not a separate section in the documentation).
7. **Error Handling and Limitations:** No hard limit on repository size. No automated tests in CI/CD for MVP. Simple Rate Limiting (Thread.sleep).
8. **UX:** Progress logging in console. No footers/disclaimers in generated files.
9. **Configuration:** Ability to define custom file exclusions and a flag to keep temporary files (developer mode).

## Matched Recommendations
1. **Asynchronicity:** Adoption of `POST /analyze` -> `GET /results/{id}` model to avoid HTTP timeouts.
2. **Backend Technology:** Choice of Java + Spring Boot as the natural environment for a Senior Java Dev.
3. **Single Prompt Strategy:** Use of Gemini's huge context window instead of complex Map-Reduce.
4. **Libraries:** Use of **Spring AI** for LLM communication and libraries like **JGit** for repository operations.
5. **Error Handling:** Implementation of Exponential Backoff for communication with Gemini API.
6. **Developer Experience:** Implementation of `debug.save-prompts` flag and option not to clean the working directory (`clean=false`) for development purposes.
7. **Security/Resources:** Static analysis of configuration files instead of running builds (security and speed).

## PRD Planning Summary

### Main Functional Requirements
1. **REST API Endpoint (`POST /analyze`):** Accepts repository URL and optional auth token. Starts a background process and returns a task ID.
2. **Cloning and Analysis Mechanism:**
    *   Cloning repository (public support).
    *   File structure analysis and technology identification (Java/JS).
    *   Git history analysis (last 12 months) to determine hotspots.
    *   File filtering based on built-in list and user configuration.
3. **Documentation Generator (AI Engine):**
    *   Building a single prompt containing project structure, source file content, and hotspots.
    *   Communication with Gemini 2.5 Pro via Spring AI (enforced JSON format).
4. **Result Endpoint (`GET /results/{id}`):** Returns processing status and result, and upon completion – a JSON object with generated documentation (README, Architecture, Context File).

### Key User Stories and Usage Paths
1. **Developer Onboarding:** As a new developer on a project, I want to generate documentation for an unknown repository to understand its architecture without reading all the code.
2. **AI Agent Bootstrap:** As a Copilot of Intelij/VS Code user, I want to generate a context file for my IDE so the AI assistant knows the entire project structure and its rules.
3. **Local Run:** User runs the JAR file, provides the Gemini key in `application.properties`, and then sends a curl request to localhost to receive JSON with documentation.

### Success Criteria and Metrics
1. **Technical Correctness:** System correctly clones a public repository, processes it, and returns valid JSON without 500 errors (assuming Google API availability).
2. **Documentation Quality (Subjective):** Generated documentation contains correct file mapping and a sensible architecture description for supported technology stacks. Verification is done via user feedback (no automated metrics in MVP).

## Unresolved Issues
1. **Context File Detail Level:** The exact level of detail in the file generated for AI (whether to include full method bodies or just signatures/skeletons) has been postponed to the implementation phase ("to be determined exactly later").

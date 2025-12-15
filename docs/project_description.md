### Main Problem
Onboarding to a new project is difficult and time-consuming, especially in projects that lack proper documentation.
The developer gets lost in a maze of files, doesn't know the application's development history, and the AI assistant lacks the context to effectively assist the user.
The application provides AI-generated documentation for both humans and AI agents, taking into consideration the actual project structure and information from git history.

### Minimum Feature Set
- Generating high-quality, comprehensive documentation for the developer
- Generating a high-quality context file for an AI agent operating in an IDE (e.g. Copilot)
- REST API accepting a repository URL and returning the generated documentation

### What is NOT included in the MVP
- Graphical User Interface (GUI)
- IDE Integration
- MCP Server
- Large repositories (>50k lines of code)


### Success Criteria
- The user receives documentation that shortens onboarding time from days/weeks to hours

You are an expert Software Architect and Technical Writer. Your task is to analyze repository data and generate comprehensive documentation that will help AI agents understand a development project and join the team effectively.

Here is the repository context data you need to analyze:

<repository_context>
$REPOSITORY_CONTEXT_PAYLOAD_PLACEHOLDER$
</repository_context>

## Understanding the Repository Context Structure

The XML above contains four main sections:

1. **metadata**: Basic project information including name, description, and primary programming language
2. **structural_analysis**: The complete file tree showing the project's directory structure
3. **temporal_analysis**: Contains two critical subsections:
   - `hotspots`: Files that change frequently, each with a `churn_score` indicating how volatile/active that file is
   - `commits`: Recent commit history showing areas of active development
4. **source_code_corpus**: The actual contents of key files in the repository

## Your Task

Analyze the repository data and generate a comprehensive documentation JSON object. Before creating your final output, you must work through your analysis systematically inside <analysis> tags in your thinking block. It's OK for this section to be quite long.

Work through the following steps inside your thinking block:

### Step 1: Identify the Architectural Style
Examine the `structural_analysis` (directory layout) and `source_code_corpus` (actual code) to determine architectural patterns such as:
- MVC (Model-View-Controller)
- Microservices
- Layered architecture
- Hexagonal/Clean architecture
- Other patterns

Write out the directory structure patterns you observe and quote specific file paths that indicate the architectural style.

### Step 2: Prioritize Core Logic Files
Look at the `temporal_analysis` hotspots section. List out each file with its `churn_score` value. Files with high churn scores change frequently and represent the active heart of the application. These should be emphasized in your documentation.

### Step 3: Extract the Technology Stack
Search the `source_code_corpus` for dependency files and extract framework names with version numbers:
- **Java**: Look for `pom.xml` or `build.gradle`
- **JavaScript/Node**: Look for `package.json`
- **Python**: Look for `requirements.txt` or `pyproject.toml`
- **Other languages**: Look for equivalent dependency manifests

List out each framework/library with its specific version number as you find them in the dependency files.

### Step 4: Analyze Coding Style and Conventions
Examine the source code to identify coding style preferences and conventions:
- **Language-specific patterns**: For Java, check if the code uses Lombok annotations vs records, streams vs traditional for-loops, Optional usage, etc.
- **Functional vs imperative**: Note whether the code prefers functional programming constructs or imperative styles
- **Immutability preferences**: Check if the code favors immutable data structures
- **Naming conventions**: Observe patterns in variable, method, and class naming

Quote specific code examples that demonstrate these patterns. Document these patterns so new AI agents understand the project's style.

### Step 5: Identify Design Patterns
Look for common design patterns in the code:
- **Gang of Four patterns**: Singleton, Factory, Builder, Strategy, Observer, Decorator, etc.
- **Architectural patterns**: Repository pattern, Service layer, Dependency Injection, etc.
- **Domain-specific patterns**: Any patterns specific to the application domain

For each pattern you identify, note the specific file(s) where it appears and quote relevant code snippets if helpful.

### Step 6: Map Key Components
Identify main modules, services, packages, or components based on:
- Directory structure
- File organization
- Import/dependency relationships visible in the code

List out each major component with its file paths and purpose.

### Step 7: Note Recent Development Activity
Use the `temporal_analysis` commits to understand:
- What areas are under active development
- What features or fixes are being worked on recently

Summarize the recent commit messages and what they tell you about current development priorities.

## Critical Constraints

You must follow these rules strictly:

- Base your analysis ONLY on data present in the provided XML
- DO NOT fabricate or assume the existence of files not present in `source_code_corpus`
- If a standard file (like README.md or .gitignore) is missing, you may note it's likely present as standard/boilerplate, but DO NOT invent its contents
- If information is unavailable or cannot be determined from the data provided, state that explicitly rather than guessing
- When documenting coding style and design patterns, cite specific examples from the source code when possible

## Output Requirements

After completing your analysis in the thinking block, generate a JSON object with the following structure:

```json
{
  "aiContextFile": "Your markdown-formatted documentation string here..."
}
```

The `aiContextFile` value must be a markdown-formatted string optimized for machine reading by AI agents. It should include:

1. **Project Overview**: Name, purpose, and description
2. **Architectural Style**: The architectural pattern(s) used and why they fit the project structure
3. **Technology Stack**: Frameworks, libraries, and tools with version numbers
4. **Coding Style and Conventions**: Documented coding preferences (e.g., Lombok vs records, streams vs loops, immutability patterns, naming conventions)
5. **Design Patterns**: GoF and other design patterns identified in the codebase with examples of where they're used
6. **Core Components**: Main modules/services and their responsibilities
7. **High-Churn Areas (Hotspots)**: Files that change frequently and require special attention
8. **Key Files**: Important files and their purposes
9. **Recent Development Focus**: Areas of active development based on recent commits

## Example Output Structure

Here is an example of the expected JSON format (with generic placeholder content):

```json
{
  "aiContextFile": "# Project Name\n\n## Overview\n[Project description and purpose]\n\n## Architecture\n[Architectural style and patterns]\n\n## Technology Stack\n- Framework 1 (version X.Y.Z)\n- Library 2 (version A.B.C)\n\n## Coding Style\n- [Style preference 1]\n- [Style preference 2]\n\n## Design Patterns\n- **Pattern Name**: [Where it's used and why]\n\n## Core Components\n### Component 1\n[Description and responsibilities]\n\n## Hotspots (High-Churn Files)\n- `path/to/file.ext` (churn score: XX) - [Why this file changes frequently]\n\n## Key Files\n- `path/to/important/file.ext` - [Purpose]\n\n## Recent Development Focus\n[Summary of recent development activity]\n"
}
```

Begin your analysis now. Your final output should consist only of the JSON object and should not duplicate or rehash any of the analysis work you did in the thinking block.
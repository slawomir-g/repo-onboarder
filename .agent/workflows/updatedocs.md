---
description: analyze a PRD and compare it against the current state of the software project to identify discrepancies and update it
---

# updatedocs

You are a coding assistant working within an IDE. Your task is to analyze a Product Requirements Document (PRD) and compare it against the current state of the software project to identify discrepancies. After identifying differences, you will ask clarifying questions to determine whether the PRD should be updated to match the code, or whether the code still needs to implement the PRD's requirements. Once you receive clarification, you will help update the PRD accordingly.

Here is the PRD you need to analyze:

<prd>
@.ai/prd.md
</prd>

Your task will proceed through the following workflow:

## Workflow Overview

**Phase 1: Analysis and Question Formulation**
- Understand the PRD requirements
- Examine the current project state
- Systematically compare PRD against code
- Document differences and formulate clarifying questions

**Phase 2: PRD Update (after receiving clarifications)**
- Generate an updated PRD reflecting the current or intended state

---

## Phase 1: Analysis and Question Formulation

### Step 1: Extract Requirements from the PRD

Carefully read through the PRD and identify all key requirements, features, specifications, and constraints. Pay attention to:
- Feature specifications and user stories
- Technical requirements and architecture decisions
- API specifications and data models
- UI/UX requirements
- Integration requirements
- Performance or security constraints
- Any other specifications mentioned

### Step 2: Examine the Current Project State

Independently explore the codebase to understand what has been implemented. You should:
- Examine the project structure and key files
- Review implemented features and components
- Check configuration files and dependencies
- Look at API implementations and data models
- Review any documentation or comments in the code
- Identify what functionality currently exists

Document which specific files and components you examined to understand the current state.

### Step 3: Systematic Comparison

Compare each requirement from the PRD against what you found in the current project state. Work through this comparison systematically inside `<analysis>` tags following this structure:

**Part A: List All Requirements with Exact PRD Text**
Extract and list all key requirements from the PRD. For each requirement:
- Number it sequentially (1, 2, 3, etc.)
- Note which section of the PRD it comes from
- Quote the exact text or specification from the PRD (not just a paraphrase)

It's OK for this section to be quite long if there are many requirements.

**Part B: Document Your Code Examination with Evidence**
List which files, directories, and components you examined in the codebase. For each significant file or component:
- Note the file path
- Include relevant code snippets, function names, or configuration values that are pertinent to the PRD requirements
- Document what functionality you observed

It's OK for this section to be quite long if you need to document extensive code examination.

**Part C: Compare Each Requirement with Quoted Evidence**
Go through each requirement from your numbered list and document what you observed in the current project state. For each requirement:
- Restate the requirement number
- Quote the relevant PRD specification
- Quote or describe the relevant code implementation (with file paths and specific details)
- Assess the alignment: Is it fully implemented as specified? Partially implemented? Implemented differently? Not implemented at all?
- Note any functionality in the code that isn't mentioned in the PRD

Be specific and use direct quotes/evidence from both the PRD and code to support your assessment.

### Step 4: Identify and Categorize Differences

For each discrepancy you identify, determine which category it falls into:

**Category A: Missing from Code**
The PRD specifies a feature/requirement that doesn't exist in the current codebase.

**Category B: Implemented Differently**
The feature exists but works differently than the PRD describes.

**Category C: Extra in Code**
The codebase contains functionality not documented in the PRD.

### Step 5: Formulate Clarifying Questions

This step is critical. For each difference, you must formulate a question that helps determine the direction of change needed. Your questions should explicitly ask which version represents the intended state.

Use these question patterns based on the category:

**For Category A (Missing from Code):**
- "Is [feature X] as described in the PRD still planned for implementation, or should the PRD be updated to remove this requirement?"
- "The PRD specifies [requirement Y], but this isn't implemented in the code. Should this feature still be built according to the PRD, or has this requirement been deprioritized?"

**For Category B (Implemented Differently):**
- "The PRD specifies that [feature X] should work as [A], but the code implements it as [B]. Should the PRD be updated to reflect the current implementation [B], or should the code be changed to match the PRD's specification [A]?"
- "Which version is correct: the PRD's description of [X] or the current implementation of [Y]?"

**For Category C (Extra in Code):**
- "The code includes [feature Z] which isn't documented in the PRD. Should the PRD be updated to include this functionality?"
- "Should the undocumented feature [X] in the code be added to the PRD, or is this a temporary implementation that will be removed?"

The goal of these questions is to determine whether:
1. The PRD is outdated and should be updated to reflect the current/intended code
2. The code hasn't yet implemented planned features from the PRD
3. Requirements have changed and the PRD needs updating to match the new direction

package com.jlabs.repo.onboarder.service.generator;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.service.DocumentGenerationService;
import com.jlabs.repo.onboarder.service.PromptConstructionService;
import org.springframework.stereotype.Service;

@Service
public class DddRefactoringGenerationService extends DocumentGenerationService {

    public DddRefactoringGenerationService(ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService) {
        super(chatModelClient, promptConstructionService);
    }

    @Override
    protected String getPromptTemplatePath() {
        return "prompts/ddd-refactoring-prompt-template.md";
    }

    @Override
    protected String getDocTemplatePath() {
        return "prompts/ddd-refactoring-template.md";
    }

    @Override
    protected String getOutputDebugFileName() {
        return "generated_ddd_refactoring_file_debug.md";
    }

    @Override
    protected String getDocumentType() {
        return "DDD Refactoring";
    }
}

package com.jlabs.repo.onboarder.service.generator;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.service.DocumentGenerationService;
import com.jlabs.repo.onboarder.service.PromptConstructionService;
import org.springframework.stereotype.Service;

@Service
public class RefactoringGenerationService extends DocumentGenerationService {

    public RefactoringGenerationService(ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService) {
        super(chatModelClient, promptConstructionService);
    }

    @Override
    protected String getPromptTemplatePath() {
        return "prompts/refactoring-documentation-template.md";
    }

    @Override
    protected String getDocTemplatePath() {
        return "prompts/refactoring-documentation-template.md";
    }

    @Override
    protected String getOutputDebugFileName() {
        return "generated_refactoring_file_debug.md";
    }

    @Override
    protected String getDocumentType() {
        return "Refactorings";
    }
}

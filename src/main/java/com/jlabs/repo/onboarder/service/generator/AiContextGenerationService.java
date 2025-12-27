package com.jlabs.repo.onboarder.service.generator;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.model.GitReport;
import com.jlabs.repo.onboarder.service.DocumentGenerationService;
import com.jlabs.repo.onboarder.service.DocumentationPostProcessingService;
import com.jlabs.repo.onboarder.service.PromptConstructionService;
import org.springframework.stereotype.Service;

@Service
public class AiContextGenerationService extends DocumentGenerationService {

    private final DocumentationPostProcessingService documentationPostProcessingService;

    public AiContextGenerationService(ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService,
            DocumentationPostProcessingService documentationPostProcessingService) {
        super(chatModelClient, promptConstructionService);
        this.documentationPostProcessingService = documentationPostProcessingService;
    }

    @Override
    protected String getPromptTemplatePath() {
        return "prompts/ai-context-prompt-template.md";
    }

    @Override
    protected String getDocTemplatePath() {
        return "prompts/ai-context-documentation-template.md";
    }

    @Override
    protected String getOutputDebugFileName() {
        return "generated_context_file_debug.md";
    }

    @Override
    protected String getDocumentType() {
        return "AI Context";
    }

    @Override
    protected String postProcess(String content, GitReport report) {
        return documentationPostProcessingService.enhance(content, report);
    }
}

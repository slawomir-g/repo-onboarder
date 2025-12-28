package com.jlabs.repo.onboarder.service.generator;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.service.DocumentGenerationService;
import com.jlabs.repo.onboarder.service.PromptConstructionService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(2)
public class ReadmeGenerationService extends DocumentGenerationService {

    public ReadmeGenerationService(ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService) {
        super(chatModelClient, promptConstructionService);
    }

    @Override
    protected String getPromptTemplatePath() {
        return "prompts/readme-prompt-template.md";
    }

    @Override
    protected String getDocTemplatePath() {
        return "prompts/readme-documentation-template.md";
    }

    @Override
    protected String getOutputDebugFileName() {
        return "generated_readme_file_debug.md";
    }

    @Override
    protected String getDocumentType() {
        return "README.md";
    }
}

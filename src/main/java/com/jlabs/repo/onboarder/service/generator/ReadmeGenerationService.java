package com.jlabs.repo.onboarder.service.generator;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.service.DocumentGenerationService;
import com.jlabs.repo.onboarder.service.PromptConstructionService;
import org.springframework.stereotype.Service;

@Service
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
    protected void addToResult(DocumentationResult result, String content) {
        result.setReadme(content);
    }
}

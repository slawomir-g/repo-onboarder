package com.jlabs.repo.onboarder.service.generator;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.service.DocumentGenerationService;
import com.jlabs.repo.onboarder.service.PromptConstructionService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(6)
public class DictionaryGenerationService extends DocumentGenerationService {

    public DictionaryGenerationService(ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService) {
        super(chatModelClient, promptConstructionService);
    }

    @Override
    protected String getPromptTemplatePath() {
        return "prompts/dictionary-prompt-template.md";
    }

    @Override
    protected String getDocTemplatePath() {
        return "prompts/dictionary-documentation-template.md";
    }

    @Override
    protected String getOutputDebugFileName() {
        return "generated_dictionary_debug.md";
    }

    @Override
    protected String getDocumentType() {
        return "Dictionary";
    }
}

package com.jlabs.repo.onboarder.service.generator;

import com.jlabs.repo.onboarder.infrastructure.springai.ChatModelClient;
import com.jlabs.repo.onboarder.service.DocumentGenerationService;
import com.jlabs.repo.onboarder.service.PromptConstructionService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(5)
public class QualityAssessmentGenerationService extends DocumentGenerationService {

    public QualityAssessmentGenerationService(ChatModelClient chatModelClient,
            PromptConstructionService promptConstructionService) {
        super(chatModelClient, promptConstructionService);
    }

    @Override
    protected String getPromptTemplatePath() {
        return "prompts/quality-assessment-prompt-template.md";
    }

    @Override
    protected String getDocTemplatePath() {
        return "prompts/quality-assessment-documentation-template.md";
    }

    @Override
    protected String getOutputDebugFileName() {
        return "generated_quality_assessment_debug.md";
    }

    @Override
    protected String getDocumentType() {
        return "Quality Assessment";
    }

}

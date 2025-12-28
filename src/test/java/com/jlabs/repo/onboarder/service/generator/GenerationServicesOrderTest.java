package com.jlabs.repo.onboarder.service.generator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationServicesOrderTest {

    @Test
    void testOrders() {
        assertOrder(AiContextGenerationService.class, 1);
        assertOrder(ReadmeGenerationService.class, 2);
        assertOrder(RefactoringGenerationService.class, 3);
        assertOrder(DddRefactoringGenerationService.class, 4);
        assertOrder(QualityAssessmentGenerationService.class, 5);
    }

    private void assertOrder(Class<?> clazz, int expectedOrder) {
        org.springframework.core.annotation.Order orderAnnotation = clazz
                .getAnnotation(org.springframework.core.annotation.Order.class);
        assertEquals(expectedOrder, orderAnnotation.value(), clazz.getSimpleName() + " should be " + expectedOrder);
    }
}

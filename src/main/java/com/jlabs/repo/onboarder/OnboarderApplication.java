package com.jlabs.repo.onboarder;

import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.config.GitCoreProperties;
import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.service.GitCoreRunner;
import com.jlabs.repo.onboarder.service.exceptions.AiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GitCoreProperties.class, AiProperties.class})
public class OnboarderApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(OnboarderApplication.class);

    private final GitCoreRunner runner;

    public OnboarderApplication(GitCoreRunner runner) {
        this.runner = runner;
    }

    public static void main(String[] args) {
        SpringApplication.run(OnboarderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            DocumentationResult result = runner.run();
            
            // Logowanie wyniku dla development/debugging
            logger.info("Dokumentacja wygenerowana pomyślnie");
            logger.debug("README długość: {} znaków", 
                    result.getReadme() != null ? result.getReadme().length() : 0);
            logger.debug("Architecture długość: {} znaków", 
                    result.getArchitecture() != null ? result.getArchitecture().length() : 0);
            logger.debug("Context File długość: {} znaków", 
                    result.getAiContextFile() != null ? result.getAiContextFile().length() : 0);
            
            // W przyszłości tutaj będzie zwrot do REST API
            
        } catch (AiException e) {
            logger.error("Błąd podczas generacji dokumentacji: {}", e.getMessage(), e);
            throw e;
        }
    }
}

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
        logger.info("Application is available in: http://localhost:8080/swagger-ui.html\n");
    }
}

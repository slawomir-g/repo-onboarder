package com.jlabs.repo.onboarder;

import com.jlabs.repo.onboarder.config.AiProperties;
import com.jlabs.repo.onboarder.config.GitCoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ GitCoreProperties.class, AiProperties.class })
public class OnboarderApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(OnboarderApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(OnboarderApplication.class, args);
    }

    @Override
    public void run(String... args) {
        logger.info("Documentation is available in: http://localhost:8080/swagger-ui.html\n");
        logger.info("Application is available in: http://localhost:8080\n");
    }
}

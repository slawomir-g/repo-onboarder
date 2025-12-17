package com.jlabs.repo.onboarder;

import com.jlabs.repo.onboarder.config.GitCoreProperties;
import com.jlabs.repo.onboarder.service.GitCoreRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GitCoreProperties.class)
public class OnboarderApplication implements CommandLineRunner {


    private final GitCoreRunner runner;

    public OnboarderApplication(GitCoreRunner runner) {
        this.runner = runner;
    }

    public static void main(String[] args) {
        SpringApplication.run(OnboarderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        runner.run();
    }
}

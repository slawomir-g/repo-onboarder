package com.jlabs.repo.onboarder.api;

import com.jlabs.repo.onboarder.model.DocumentationResult;
import com.jlabs.repo.onboarder.service.GitCoreRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/git-core")
@Tag(name = "Git Core", description = "Git Repository Analysis")
@Slf4j
@RequiredArgsConstructor
public class GitCoreController {

        private final GitCoreRunner runner;

        @Operation(summary = "Starts repository analysis", description = "Clones repository and generates documentation")
        @PostMapping("/run")
        public ResponseEntity<?> runAnalysis(
                        @Parameter(description = "Repository URL") @RequestParam(required = true, defaultValue = "${git-core.repo-url}") String repoUrl,

                        @Parameter(description = "Branch name") @RequestParam(required = true, defaultValue = "${git-core.branch}") String branch,
                        @Parameter(description = "Include tests") @RequestParam(required = true, defaultValue = "${git-core.withTest}") boolean withTest,
                        @Parameter(description = "Target language") @RequestParam(required = false, defaultValue = "English") String targetLanguage) {
                log.info("REST: starting GitCore analysis");
                try {
                        DocumentationResult result = runner.run(repoUrl, branch, withTest, targetLanguage);
                        return ResponseEntity.ok(result);
                } catch (Exception ex) {
                        log.error("Error during GitCore analysis", ex);

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ex.getMessage());
                }

        }
}

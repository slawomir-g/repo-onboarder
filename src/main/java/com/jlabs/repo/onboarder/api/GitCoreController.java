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
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/git-core")
@Tag(name = "Git Core", description = "Analiza repozytorium Git")
@Slf4j
@RequiredArgsConstructor
public class GitCoreController {

        private final GitCoreRunner runner;

        @Operation(summary = "Uruchamia analizę repozytorium", description = "Klonuje repozytorium i generuje dokumentację")
        @PostMapping("/run")
        public ResponseEntity<?> runAnalysis(
                        @Parameter(description = "URL repozytorium") @RequestParam(required = true, defaultValue = "${git-core.repo-url}") String repoUrl,

                        @Parameter(description = "Nazwa brancha") @RequestParam(required = true, defaultValue = "${git-core.branch}") String branch,
                        @Parameter(description = "Nazwa brancha") @RequestParam(required = true, defaultValue = "${git-core.withTest}") boolean withTest) {
                log.info("REST: uruchamiam analizę GitCore");
                try {
                        DocumentationResult result = runner.run(repoUrl, branch, withTest);
                        return ResponseEntity.ok(result);
                } catch (Exception ex) {
                        log.error("Błąd podczas analizy GitCore", ex);

                        return ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ex.getMessage());
                }

        }
}

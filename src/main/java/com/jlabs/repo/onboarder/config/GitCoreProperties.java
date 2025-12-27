package com.jlabs.repo.onboarder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

@ConfigurationProperties(prefix = "git-core")
@Data
public class GitCoreProperties {

    private String workdir = "repo-work";

    private Output output = new Output();
    private Limits limits = new Limits();
    private Auth auth = new Auth();

    @Data
    public static class Output {
        private String markdown = "git_report.md";
    }

    @Data
    public static class Limits {
        private int maxCommits = 200; // 0 = no limit
        private int maxChangedFiles = 200; // 0 = no limit
        private boolean includePatch = false;
        private int maxPatchChars = 4000;
    }

    @Data
    public static class Auth {
        private String username = "x-access-token";
        private String token = "";
    }
}

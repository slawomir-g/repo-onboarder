package com.jlabs.repo.onboarder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "git-core")
public class GitCoreProperties {

    private Output output = new Output();
    private Limits limits = new Limits();
    private Auth auth = new Auth();

    public Output getOutput() { return output; }
    public void setOutput(Output output) { this.output = output; }

    public Limits getLimits() { return limits; }
    public void setLimits(Limits limits) { this.limits = limits; }

    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }

    public static class Output {
        private String markdown = "git_report.md";
        public String getMarkdown() { return markdown; }
        public void setMarkdown(String markdown) { this.markdown = markdown; }
    }

    public static class Limits {
        private int maxCommits = 200;      // 0 = no limit
        private int maxChangedFiles = 200; // 0 = no limit
        private boolean includePatch = false;
        private int maxPatchChars = 4000;

        public int getMaxCommits() { return maxCommits; }
        public void setMaxCommits(int maxCommits) { this.maxCommits = maxCommits; }

        public int getMaxChangedFiles() { return maxChangedFiles; }
        public void setMaxChangedFiles(int maxChangedFiles) { this.maxChangedFiles = maxChangedFiles; }

        public boolean isIncludePatch() { return includePatch; }
        public void setIncludePatch(boolean includePatch) { this.includePatch = includePatch; }

        public int getMaxPatchChars() { return maxPatchChars; }
        public void setMaxPatchChars(int maxPatchChars) { this.maxPatchChars = maxPatchChars; }
    }

    public static class Auth {
        private String username = "x-access-token";
        private String token = "";

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}

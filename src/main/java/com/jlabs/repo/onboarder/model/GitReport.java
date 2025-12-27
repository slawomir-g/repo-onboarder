package com.jlabs.repo.onboarder.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class GitReport {

    private Instant generatedAt = Instant.now();

    private RepoInfo repo = new RepoInfo();
    private List<RemoteInfo> remotes = new ArrayList<>();
    private List<String> branches = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    private List<String> allFilesAtHead = new ArrayList<>();
    private List<CommitInfo> commits = new ArrayList<>();

    private Map<String, FileStats> fileStats = new HashMap<>();

    @Data
    public static class RepoInfo {
        private String url;
        private String branch;
        private String workdir;

        private String headCommit;
        private String headShortMessage;
        private Instant headCommitTime;
    }

    @Data
    public static class RemoteInfo {
        private String name;
        private List<String> uris = new ArrayList<>();
    }

    @Data
    public static class CommitInfo {

        private String commitId;
        private String shortId;

        private String authorName;
        private String authorEmail;
        private Instant authorTime;

        private String committerName;
        private String committerEmail;
        private Instant committerTime;

        private String messageShort;
        private String messageFull;

        private List<String> parents = new ArrayList<>();

        private DiffStats diffStats = new DiffStats();
        private List<FileChange> changes = new ArrayList<>();

        private String patchSnippet;

        @Data
        public static class DiffStats {
            private int filesChanged;
            private int linesAdded;
            private int linesDeleted;
            private int linesTotal;
        }

        @Data
        public static class FileChange {
            private String type;
            private String oldPath;
            private String newPath;
            private int linesAdded;
            private int linesDeleted;
        }
    }

    @Data
    public static class FileStats {
        private int commits;
        private int linesAdded;
        private int linesDeleted;
    }
}

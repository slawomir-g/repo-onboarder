package com.jlabs.repo.onboarder.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitReport {


    public Instant generatedAt = Instant.now();

    public RepoInfo repo = new RepoInfo();
    public List<RemoteInfo> remotes = new ArrayList<>();
    public List<String> branches = new ArrayList<>();
    public List<String> tags = new ArrayList<>();

    public List<String> allFilesAtHead = new ArrayList<>();
    public List<CommitInfo> commits = new ArrayList<>();

    public Map<String, FileStats> fileStats = new HashMap<>();

    public static class RepoInfo {
        public String url;
        public String branch;
        public String workdir;

        public String headCommit;
        public String headShortMessage;
        public Instant headCommitTime;
    }

    public static class RemoteInfo {
        public String name;
        public List<String> uris = new ArrayList<>();
    }

    public static class CommitInfo {

        public String commitId;
        public String shortId;

        public String authorName;
        public String authorEmail;
        public Instant authorTime;

        public String committerName;
        public String committerEmail;
        public Instant committerTime;

        public String messageShort;
        public String messageFull;

        public List<String> parents = new ArrayList<>();

        public DiffStats diffStats = new DiffStats();
        public List<FileChange> changes = new ArrayList<>();

        public String patchSnippet;

        public static class DiffStats {
            public int filesChanged;
            public int linesAdded;
            public int linesDeleted;
            public int linesTotal;
        }

        public static class FileChange {
            public String type;
            public String oldPath;
            public String newPath;
            public int linesAdded;
            public int linesDeleted;
        }
    }

    public static class FileStats {
        public int commits;
        public int linesAdded;
        public int linesDeleted;
    }
}

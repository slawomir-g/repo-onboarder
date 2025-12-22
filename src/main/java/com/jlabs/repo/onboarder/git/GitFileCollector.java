package com.jlabs.repo.onboarder.git;

import com.jlabs.repo.onboarder.model.GitReport;
import org.springframework.stereotype.Service;


import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GitFileCollector {

    public void collect(Repository repo, GitReport report, boolean withTest) throws Exception {
        List<String> files = new ArrayList<>();

        try (TreeWalk treeWalk = new TreeWalk(repo)) {
            treeWalk.addTree(repo.resolve("HEAD^{tree}"));
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (!withTest) {
                    if (!path.endsWith("Test.java")) {
                        files.add(path);
                    }
                } else {
                    files.add(path);
                }
            }
        }

        Collections.sort(files);
        report.allFilesAtHead = files;
    }
}

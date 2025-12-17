package com.jlabs.repo.onboarder.model;

import java.util.Map;
import java.util.TreeMap;

public class DirNode {
    String name;
    boolean directory;
    Map<String, DirNode> children = new TreeMap<>();

    DirNode(String name, boolean directory) {
        this.name = name;
        this.directory = directory;
    }
}

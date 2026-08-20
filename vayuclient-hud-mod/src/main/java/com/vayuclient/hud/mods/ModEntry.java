package com.vayuclient.hud.mods;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModEntry {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> authors;
    private final String homepage;
    private final String sources;
    private final String issues;
    private final Path jarPath;
    private final boolean hasConfig;
    private final String category; // "user", "performance", "visual", "utility", "library"
    private final String loader; // "Fabric", "Quilt", "NeoForge", "Builtin"

    public ModEntry(
            String id,
            String name,
            String version,
            String description,
            List<String> authors,
            String homepage,
            String sources,
            String issues,
            Path jarPath,
            boolean hasConfig,
            String category,
            String loader
    ) {
        this.id = id != null ? id : "unknown";
        this.name = name != null && !name.isBlank() ? name : this.id;
        this.version = version != null ? version : "1.0.0";
        this.description = description != null ? description.trim() : "No description provided.";
        this.authors = authors != null ? authors : new ArrayList<>();
        this.homepage = homepage;
        this.sources = sources;
        this.issues = issues;
        this.jarPath = jarPath;
        this.hasConfig = hasConfig;
        this.category = category != null ? category : "user";
        this.loader = loader != null ? loader : "Fabric";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public String getAuthorsString() {
        if (authors.isEmpty()) return "Unknown";
        return String.join(", ", authors);
    }

    public String getHomepage() {
        return homepage;
    }

    public String getSources() {
        return sources;
    }

    public String getIssues() {
        return issues;
    }

    public Path getJarPath() {
        return jarPath;
    }

    public boolean hasConfig() {
        return hasConfig;
    }

    public String getCategory() {
        return category;
    }

    public String getLoader() {
        return loader;
    }

    public String getFileName() {
        if (jarPath != null) {
            return jarPath.getFileName().toString();
        }
        return id + "-" + version + ".jar";
    }
}

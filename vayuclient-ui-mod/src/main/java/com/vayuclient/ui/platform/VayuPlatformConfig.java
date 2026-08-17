package com.vayuclient.ui.platform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VayuPlatformConfig {
    private static boolean firstLaunchCompleted = false;
    private static boolean loaded = false;
    private static final Object LOCK = new Object();

    private static Path getConfigPath() {
        return Path.of(".vayuclient_state.json");
    }

    public static boolean isFirstLaunchCompleted() {
        synchronized (LOCK) {
            if (!loaded) {
                load();
            }
            return firstLaunchCompleted;
        }
    }

    public static void setFirstLaunchCompleted(boolean value) {
        synchronized (LOCK) {
            firstLaunchCompleted = value;
            save();
        }
    }

    public static void load() {
        synchronized (LOCK) {
            loaded = true;
            try {
                Path p = getConfigPath();
                if (Files.exists(p)) {
                    String content = Files.readString(p);
                    firstLaunchCompleted = content.contains("\"firstLaunchCompleted\":true") || content.contains("\"firstLaunchCompleted\": true");
                } else {
                    firstLaunchCompleted = false;
                }
            } catch (Throwable t) {
                firstLaunchCompleted = true; // Safe fallback
            }
        }
    }

    public static void save() {
        synchronized (LOCK) {
            try {
                Path p = getConfigPath();
                String json = "{\n  \"firstLaunchCompleted\": " + firstLaunchCompleted + ",\n  \"version\": \"1.6.0\"\n}\n";
                Files.writeString(p, json);
            } catch (Throwable ignored) {}
        }
    }
}

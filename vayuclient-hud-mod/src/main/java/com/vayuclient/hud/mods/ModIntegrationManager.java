package com.vayuclient.hud.mods;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

public class ModIntegrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("VayuModIntegration");
    private static ModIntegrationManager INSTANCE;

    private final List<ModEntry> installedMods = new ArrayList<>();
    private final Map<String, Object> configFactories = new HashMap<>();
    private boolean initialized = false;

    public static synchronized ModIntegrationManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModIntegrationManager();
        }
        return INSTANCE;
    }

    public synchronized void reload() {
        installedMods.clear();
        configFactories.clear();
        initialized = false;
        init();
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            discoverModMenuApi();
        } catch (Throwable t) {
            LOGGER.debug("ModMenuApi discovery skipped: {}", t.getMessage());
        }

        try {
            discoverFabricMods();
        } catch (Throwable t) {
            LOGGER.error("Error discovering Fabric mods: {}", t.getMessage(), t);
        }

        // Sort by name (A-Z) by default
        installedMods.sort(Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT)));
    }

    private void discoverModMenuApi() {
        try {
            // Scan modmenu entrypoints directly from FabricLoader
            List<EntrypointContainer<Object>> entrypoints = FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class);
            for (EntrypointContainer<Object> container : entrypoints) {
                try {
                    Object apiInstance = container.getEntrypoint();
                    ModContainer modContainer = container.getProvider();
                    String modId = modContainer.getMetadata().getId();

                    // 1. Check getModConfigScreenFactory()
                    try {
                        Method factoryMethod = apiInstance.getClass().getMethod("getModConfigScreenFactory");
                        Object factory = factoryMethod.invoke(apiInstance);
                        if (factory != null) {
                            configFactories.put(modId, factory);
                        }
                    } catch (Throwable ignored) {}

                    // 2. Check getProvidedConfigScreenFactories()
                    try {
                        Method providedMethod = apiInstance.getClass().getMethod("getProvidedConfigScreenFactories");
                        Object mapObj = providedMethod.invoke(apiInstance);
                        if (mapObj instanceof Map<?, ?> map) {
                            for (Map.Entry<?, ?> entry : map.entrySet()) {
                                if (entry.getKey() instanceof String subId && entry.getValue() != null) {
                                    configFactories.put(subId, entry.getValue());
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void discoverFabricMods() {
        Collection<ModContainer> allMods = FabricLoader.getInstance().getAllMods();
        for (ModContainer mod : allMods) {
            try {
                ModMetadata meta = mod.getMetadata();
                String id = meta.getId();

                // Skip internal synthetic submodules if needed or keep top-level
                String name = meta.getName();
                String version = meta.getVersion().getFriendlyString();
                String desc = meta.getDescription();

                List<String> authors = new ArrayList<>();
                for (Person author : meta.getAuthors()) {
                    authors.add(author.getName());
                }

                ContactInformation contact = meta.getContact();
                String homepage = contact.get("homepage").orElse(null);
                String sources = contact.get("sources").orElse(null);
                String issues = contact.get("issues").orElse(null);

                Path jarPath = null;
                try {
                    List<Path> paths = mod.getOrigin().getPaths();
                    if (paths != null && !paths.isEmpty()) {
                        jarPath = paths.get(0);
                    }
                } catch (Throwable ignored) {}

                boolean hasConfig = configFactories.containsKey(id) || hasCustomConfigScreen(id);
                String category = categorizeMod(id, name, desc);
                String loader = "Fabric";

                installedMods.add(new ModEntry(
                        id,
                        name,
                        version,
                        desc,
                        authors,
                        homepage,
                        sources,
                        issues,
                        jarPath,
                        hasConfig,
                        category,
                        loader
                ));
            } catch (Throwable t) {
                LOGGER.warn("Failed reading mod metadata: {}", t.getMessage());
            }
        }
    }

    private String categorizeMod(String id, String name, String desc) {
        String combined = (id + " " + name + " " + desc).toLowerCase(Locale.ROOT);
        if (id.equals("minecraft") || id.startsWith("fabric") || id.startsWith("quilt") || id.equals("java")) {
            return "library";
        }
        if (combined.contains("sodium") || combined.contains("iris") || combined.contains("lithium") ||
            combined.contains("ferritecore") || combined.contains("fps") || combined.contains("c2me") ||
            combined.contains("entityculling") || combined.contains("immediatelyfast") || combined.contains("krypton") ||
            combined.contains("modernfix") || combined.contains("memory") || combined.contains("optimize")) {
            return "performance";
        }
        if (combined.contains("shader") || combined.contains("render") || combined.contains("visual") ||
            combined.contains("blur") || combined.contains("hud") || combined.contains("gui") ||
            combined.contains("capes") || combined.contains("animation") || combined.contains("cosmetic")) {
            return "visual";
        }
        if (combined.contains("voice") || combined.contains("chat") || combined.contains("map") ||
            combined.contains("replay") || combined.contains("flashback") || combined.contains("schematic") ||
            combined.contains("litematica") || combined.contains("tweak") || combined.contains("util")) {
            return "utility";
        }
        if (combined.contains("api") || combined.contains("lib") || combined.contains("cloth-config") ||
            combined.contains("architectury") || combined.contains("kotlin") || combined.contains("cloth")) {
            return "library";
        }
        return "user";
    }

    public List<ModEntry> getInstalledMods() {
        if (!initialized) init();
        return installedMods;
    }

    public boolean hasCustomConfigScreen(String modId) {
        return switch (modId) {
            case "sodium", "iris", "modmenu", "cloth-config", "vayuclient-hud", "voicechat", "flashback" -> true;
            default -> false;
        };
    }

    public Screen createConfigScreen(String modId, Screen parent) {
        // 1. Try registered ModMenu config factory
        Object factory = configFactories.get(modId);
        if (factory != null) {
            try {
                Method createMethod = factory.getClass().getMethod("create", Screen.class);
                Object screen = createMethod.invoke(factory, parent);
                if (screen instanceof Screen s) {
                    return s;
                }
            } catch (Throwable t) {
                LOGGER.error("Failed invoking config factory for {}: {}", modId, t.getMessage());
            }
        }

        // 2. Try ModMenu.getConfigScreen if ModMenu class is present
        try {
            Class<?> mmClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
            Method getScreenMethod = mmClass.getMethod("getConfigScreen", String.class, Screen.class);
            Object screen = getScreenMethod.invoke(null, modId, parent);
            if (screen instanceof Screen s) {
                return s;
            }
        } catch (Throwable ignored) {}

        // 3. Fallback for specific prominent mods
        try {
            if ("sodium".equals(modId)) {
                Class<?> cls = Class.forName("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI");
                return (Screen) cls.getConstructor(Screen.class).newInstance(parent);
            }
            if ("iris".equals(modId)) {
                Class<?> cls = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
                return (Screen) cls.getConstructor(Screen.class).newInstance(parent);
            }
            if ("vayuclient-hud".equals(modId)) {
                return new com.vayuclient.hud.gui.screens.ClickGUIScreen();
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public void openModsFolder() {
        try {
            File modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toFile();
            if (!modsDir.exists()) {
                modsDir.mkdirs();
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(modsDir);
            } else {
                new ProcessBuilder("explorer.exe", modsDir.getAbsolutePath()).start();
            }
        } catch (Throwable t) {
            LOGGER.error("Failed opening mods folder: {}", t.getMessage());
        }
    }

    public void openInFolder(Path jarPath) {
        if (jarPath == null) {
            openModsFolder();
            return;
        }
        try {
            File file = jarPath.toFile();
            if (file.exists()) {
                new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
            } else {
                openModsFolder();
            }
        } catch (Throwable t) {
            openModsFolder();
        }
    }

    public void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                new ProcessBuilder("cmd", "/c", "start", url).start();
            }
        } catch (Throwable t) {
            LOGGER.error("Failed opening URL {}: {}", url, t.getMessage());
        }
    }
}

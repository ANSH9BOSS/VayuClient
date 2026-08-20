/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.reflect.TypeToken
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 */
package net.fastclient.hud.modules.impl.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class WaypointsModule
extends Module {
    private static final Type WAYPOINT_LIST = new TypeToken<List<Waypoint>>(){}.getType();
    private final BooleanSetting showDistance = this.register(new BooleanSetting("show_distance", "Show the distance to each waypoint", true));
    private final BooleanSetting showCoordinates = this.register(new BooleanSetting("show_coordinates", "Show waypoint coordinates in the navigation marker", false));
    private final NumberSetting maxVisible = this.register(new NumberSetting("max_visible", "Maximum number of navigation markers", 5.0, 1.0, 12.0, 1.0));
    private final List<Waypoint> waypoints = new ArrayList<Waypoint>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("fast-client-hud").resolve("waypoints.json");

    public WaypointsModule() {
        super("Waypoints", "Create persistent, colored navigation markers for every dimension", Category.RENDER);
        this.setKeyBinding(78);
        this.load();
    }

    public List<Waypoint> getWaypoints() {
        return List.copyOf(this.waypoints);
    }

    public void upsert(Waypoint waypoint) {
        for (int i = 0; i < this.waypoints.size(); ++i) {
            if (!this.waypoints.get((int)i).id.equals(waypoint.id)) continue;
            if (waypoint.world == null) {
                waypoint.world = this.waypoints.get((int)i).world;
            }
            this.waypoints.set(i, waypoint);
            this.save();
            return;
        }
        this.waypoints.add(waypoint);
        this.save();
    }

    public void remove(String id) {
        this.waypoints.removeIf(waypoint -> waypoint.id.equals(id));
        this.save();
    }

    public void setWaypointEnabled(String id, boolean enabled) {
        for (Waypoint waypoint : this.waypoints) {
            if (!waypoint.id.equals(id)) continue;
            waypoint.enabled = enabled;
            this.save();
            return;
        }
    }

    public Waypoint create(String name, double x, double y, double z, String dimension, int color) {
        return new Waypoint(UUID.randomUUID().toString(), WaypointsModule.cleanName(name), x, y, z, WaypointsModule.normalizeDimension(dimension), this.currentWorld(), color | 0xFF000000, true);
    }

    public String currentDimension() {
        return WaypointsModule.mc.level == null ? "overworld" : WaypointsModule.mc.level.dimension().identifier().getPath();
    }

    public String currentWorld() {
        if (mc.getCurrentServer() != null) {
            return "server:" + WaypointsModule.mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        }
        if (mc.getSingleplayerServer() != null) {
            return "singleplayer:" + mc.getSingleplayerServer().getWorldData().getLevelName().toLowerCase(Locale.ROOT);
        }
        return "unknown";
    }

    private void load() {
        if (!Files.exists(this.configPath, new LinkOption[0])) {
            return;
        }
        try {
            List loaded = (List)this.gson.fromJson(Files.readString(this.configPath), WAYPOINT_LIST);
            if (loaded != null) {
                loaded.removeIf(waypoint -> waypoint == null || waypoint.id == null || waypoint.name == null);
                this.waypoints.addAll(loaded);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public void save() {
        try {
            Files.createDirectories(this.configPath.getParent(), new FileAttribute[0]);
            Files.writeString(this.configPath, (CharSequence)this.gson.toJson(this.waypoints, WAYPOINT_LIST), new OpenOption[0]);
        }
        catch (Exception ignored) {
            this.sendMessage("Could not save waypoints.");
        }
    }

    public List<Waypoint> getVisibleWorldWaypoints() {
        if (!this.isInGame()) {
            return List.of();
        }
        String dimension = this.currentDimension();
        return this.waypoints.stream().filter(waypoint -> waypoint.enabled && (waypoint.world == null || waypoint.world.equals(this.currentWorld())) && WaypointsModule.normalizeDimension(waypoint.dimension).equals(dimension)).sorted(Comparator.comparingDouble(waypoint -> WaypointsModule.distanceSquared(waypoint, WaypointsModule.mc.player.getX(), WaypointsModule.mc.player.getY(), WaypointsModule.mc.player.getZ()))).limit(((Double)this.maxVisible.getValue()).longValue()).toList();
    }

    public String getWorldLabel(Waypoint waypoint) {
        StringBuilder label = new StringBuilder(waypoint.name);
        if (((Boolean)this.showDistance.getValue()).booleanValue()) {
            label.append("  ").append(Math.round(this.distanceToPlayer(waypoint))).append("m");
        }
        if (((Boolean)this.showCoordinates.getValue()).booleanValue()) {
            label.append(String.format(Locale.ROOT, "  [%.0f, %.0f, %.0f]", waypoint.x, waypoint.y, waypoint.z));
        }
        return label.toString();
    }

    public double distanceToPlayer(Waypoint waypoint) {
        if (WaypointsModule.mc.player == null) {
            return 0.0;
        }
        return Math.sqrt(WaypointsModule.distanceSquared(waypoint, WaypointsModule.mc.player.getX(), WaypointsModule.mc.player.getY(), WaypointsModule.mc.player.getZ()));
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
    }

    @Override
    public boolean isHudVisible() {
        return false;
    }

    private static double distanceSquared(Waypoint waypoint, double x, double y, double z) {
        double dx = waypoint.x - x;
        double dy = waypoint.y - y;
        double dz = waypoint.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static String cleanName(String name) {
        String cleaned = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    public static String normalizeDimension(String dimension) {
        String cleaned = dimension == null ? "" : dimension.trim().toLowerCase(Locale.ROOT);
        int separator = cleaned.lastIndexOf(58);
        String path = separator >= 0 ? cleaned.substring(separator + 1) : cleaned;
        return "dimension".equals(path) ? "overworld" : path;
    }

    public static final class Waypoint {
        public String id;
        public String name;
        public double x;
        public double y;
        public double z;
        public String dimension;
        public String world;
        public int color;
        public boolean enabled;

        public Waypoint(String id, String name, double x, double y, double z, String dimension, int color, boolean enabled) {
            this(id, name, x, y, z, dimension, null, color, enabled);
        }

        public Waypoint(String id, String name, double x, double y, double z, String dimension, String world, int color, boolean enabled) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.world = world;
            this.color = color;
            this.enabled = enabled;
        }
    }
}


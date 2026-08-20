package com.vayuclient.hud.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordPresenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger("VayuDiscordRPC");
    public static final String CLIENT_ID = "1538504622652661830";
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    private static DiscordPresenceService instance;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "VayuClient-DiscordRPC");
        t.setDaemon(true);
        return t;
    });

    private RandomAccessFile pipe;
    private volatile boolean connected = false;
    private final long startTimestamp = System.currentTimeMillis();
    private Thread readerThread;

    public static synchronized DiscordPresenceService getInstance() {
        if (instance == null) {
            instance = new DiscordPresenceService();
        }
        return instance;
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 1, 15, TimeUnit.SECONDS);
    }

    private synchronized void tick() {
        try {
            if (!connected || pipe == null) {
                tryConnect();
            } else {
                updatePresence();
            }
        } catch (Throwable t) {
            closePipe();
        }
    }

    private void tryConnect() {
        for (int i = 0; i < 10; i++) {
            String pipeName = "\\\\.\\pipe\\discord-ipc-" + i;
            try {
                pipe = new RandomAccessFile(pipeName, "rw");

                JsonObject handshake = new JsonObject();
                handshake.addProperty("v", 1);
                handshake.addProperty("client_id", CLIENT_ID);

                sendPacket(OP_HANDSHAKE, handshake.toString());

                // Read handshake reply
                byte[] header = new byte[8];
                pipe.readFully(header);
                ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                int op = bb.getInt();
                int len = bb.getInt();

                if (len > 0) {
                    byte[] data = new byte[len];
                    pipe.readFully(data);
                    connected = true;
                    LOGGER.info("[DiscordRPC] Connected successfully to Discord IPC on pipe {}", i);
                    
                    // Start background reader thread to keep pipe drained
                    startReaderThread();
                    
                    updatePresence();
                    return;
                }
            } catch (Throwable ignored) {
                closePipe();
            }
        }
    }

    private void startReaderThread() {
        if (readerThread != null && readerThread.isAlive()) {
            return;
        }
        readerThread = new Thread(() -> {
            byte[] header = new byte[8];
            while (connected && pipe != null) {
                try {
                    pipe.readFully(header);
                    ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                    int op = bb.getInt();
                    int len = bb.getInt();
                    if (len > 0) {
                        byte[] data = new byte[len];
                        pipe.readFully(data);
                    }
                } catch (Throwable t) {
                    closePipe();
                    break;
                }
            }
        }, "VayuClient-DiscordRPC-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public synchronized void updatePresence() {
        if (!connected || pipe == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            String details = "VayuClient • Modern PvP Client";
            String state = "Main Menu";

            if (mc != null && mc.level != null) {
                ServerData server = mc.getCurrentServer();
                if (server != null) {
                    details = "Playing Multiplayer";
                    state = "Server: " + server.ip;
                } else if (mc.hasSingleplayerServer()) {
                    details = "Playing Singleplayer";
                    state = "Exploring World";
                } else {
                    details = "Playing Minecraft";
                    state = "In-Game Session";
                }
            } else {
                details = "In Main Menu";
                state = "Ready to Play";
            }

            JsonObject activity = new JsonObject();
            activity.addProperty("details", details);
            activity.addProperty("state", state);
            activity.addProperty("type", 0);

            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", startTimestamp);
            activity.add("timestamps", timestamps);

            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", "vayu_logo");
            assets.addProperty("large_text", "VayuClient v1.8.0");
            assets.addProperty("small_image", "vayu_logo");
            assets.addProperty("small_text", "Developer: ANSH9BOSS");
            activity.add("assets", assets);

            JsonArray buttons = new JsonArray();
            JsonObject discordBtn = new JsonObject();
            discordBtn.addProperty("label", "Join Discord");
            discordBtn.addProperty("url", "https://discord.gg/RGzATq3v7J");
            buttons.add(discordBtn);
            activity.add("buttons", buttons);

            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            JsonObject args = new JsonObject();
            args.addProperty("pid", (int)ProcessHandle.current().pid());
            args.add("activity", activity);
            root.add("args", args);
            root.addProperty("nonce", UUID.randomUUID().toString());

            sendPacket(OP_FRAME, root.toString());
        } catch (Throwable t) {
            closePipe();
        }
    }

    private synchronized void sendPacket(int op, String json) throws Exception {
        if (pipe == null) return;
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(op);
        bb.putInt(payload.length);
        bb.put(payload);
        pipe.write(bb.array());
    }

    private synchronized void closePipe() {
        connected = false;
        if (pipe != null) {
            try {
                pipe.close();
            } catch (Throwable ignored) {}
            pipe = null;
        }
    }

    public void shutdown() {
        try {
            if (connected && pipe != null) {
                sendPacket(OP_CLOSE, "{}");
            }
        } catch (Throwable ignored) {}
        closePipe();
        scheduler.shutdownNow();
    }
}

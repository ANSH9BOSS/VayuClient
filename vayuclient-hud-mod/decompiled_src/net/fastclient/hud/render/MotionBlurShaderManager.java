/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.buffers.GpuBuffer
 *  com.mojang.blaze3d.buffers.GpuBufferSlice$MappedView
 *  com.mojang.blaze3d.buffers.Std140Builder
 *  com.mojang.blaze3d.resource.GraphicsResourceAllocator
 *  com.mojang.blaze3d.systems.GpuDevice
 *  com.mojang.blaze3d.systems.RenderSystem
 *  net.minecraft.client.CameraType
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.LevelTargetBundle
 *  net.minecraft.client.renderer.PostChain
 *  net.minecraft.client.renderer.PostPass
 *  net.minecraft.client.renderer.ShaderManager$CompilationCache
 *  net.minecraft.resources.Identifier
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.lwjgl.PointerBuffer
 *  org.lwjgl.glfw.GLFW
 *  org.lwjgl.glfw.GLFWVidMode
 */
package net.fastclient.hud.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fastclient.hud.core.ModuleManager;
import net.fastclient.hud.mixin.client.PostChainAccessor;
import net.fastclient.hud.mixin.client.PostPassAccessor;
import net.fastclient.hud.mixin.client.ShaderManagerAccessor;
import net.fastclient.hud.modules.impl.render.MotionBlurModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public class MotionBlurShaderManager {
    private static long lastNano;
    private static float currentFPS;
    private static int sampleAmount;
    private static final Matrix4f tempMvInverse;
    private static final Matrix4f tempProjInverse;
    private static final Matrix4f tempPrevModelView;
    private static final Matrix4f tempPrevProjection;
    private static float camDX;
    private static float camDY;
    private static float camDZ;
    private static final Matrix4f scratchMatrix;
    private static GpuBuffer motionBlurUBO;
    private static final int UBO_SIZE = 304;
    private static boolean loadErrorLogged;
    private static PostChain cachedProcessor;
    private static Method createBufferMethod;
    private static GraphicsResourceAllocator frameAllocator;
    private static long lastMonitorHandle;
    private static int lastRefreshRate;
    private static long lastCheckTime;
    private static final long CHECK_INTERVAL_NS = 1000000000L;

    public static void captureAllocator(GraphicsResourceAllocator allocator) {
        frameAllocator = allocator;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void applyMotionBlur() {
        try {
            long now = System.nanoTime();
            float deltaTime = (float)(now - lastNano) / 1.0E9f;
            lastNano = now;
            currentFPS = deltaTime > 0.0f && deltaTime < 1.0f ? 1.0f / deltaTime : 0.0f;
            MotionBlurModule module = MotionBlurShaderManager.getModule();
            if (module == null || !module.isEnabled() || module.getStrength() == 0.0f) {
                return;
            }
            MotionBlurShaderManager.applyMotionBlurInternal(module);
        }
        finally {
            frameAllocator = null;
        }
    }

    private static MotionBlurModule getModule() {
        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            return null;
        }
        return mm.getModule(MotionBlurModule.class);
    }

    private static void applyMotionBlurInternal(MotionBlurModule module) {
        float baseStrength;
        Minecraft client = Minecraft.getInstance();
        MotionBlurShaderManager.updateDisplayInfo();
        int displayRefreshRate = lastRefreshRate;
        float scaledStrength = baseStrength = module.getStrength();
        sampleAmount = 100;
        if (module.isRefreshRateScaling()) {
            float fpsOverRefresh;
            float f = fpsOverRefresh = displayRefreshRate > 0 ? currentFPS / (float)displayRefreshRate : 1.0f;
            if (fpsOverRefresh < 1.0f) {
                fpsOverRefresh = 1.0f;
            }
            scaledStrength = baseStrength * fpsOverRefresh;
            if (fpsOverRefresh > 1.0f) {
                sampleAmount = (int)(100.0f * fpsOverRefresh);
            }
        }
        if (frameAllocator == null) {
            return;
        }
        PostChain processor = MotionBlurShaderManager.getProcessor(client);
        if (processor == null) {
            return;
        }
        MotionBlurShaderManager.replaceUniformBuffer(processor, scaledStrength, client.gameRenderer.mainRenderTarget().width, client.gameRenderer.mainRenderTarget().height, module.getBlurAlgorithmOrdinal());
        processor.process(client.gameRenderer.mainRenderTarget(), frameAllocator);
    }

    private static PostChain getProcessor(Minecraft client) {
        try {
            ShaderManager.CompilationCache cache = ((ShaderManagerAccessor)client.getShaderManager()).getCompilationCache();
            if (cache == null) {
                cachedProcessor = null;
                return null;
            }
            PostChain processor = cache.getOrLoadPostChain(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"motion_blur"), LevelTargetBundle.MAIN_TARGETS);
            if (processor != cachedProcessor) {
                cachedProcessor = processor;
                motionBlurUBO = null;
            }
            loadErrorLogged = false;
            return cachedProcessor;
        }
        catch (Exception e) {
            if (!loadErrorLogged) {
                System.err.println("[FastClientHUD] Failed to load motion blur shader: " + e.getMessage());
                loadErrorLogged = true;
            }
            cachedProcessor = null;
            return null;
        }
    }

    private static void replaceUniformBuffer(PostChain processor, float blendFactor, float viewW, float viewH, int blurAlgorithm) {
        GpuBuffer old;
        List<PostPass> passes = ((PostChainAccessor)processor).getPasses();
        if (passes.isEmpty()) {
            return;
        }
        Map<String, GpuBuffer> uniformBuffers = ((PostPassAccessor)passes.getFirst()).getCustomUniforms();
        if (!uniformBuffers.containsKey("MotionBlurUniforms")) {
            return;
        }
        if (motionBlurUBO == null) {
            motionBlurUBO = MotionBlurShaderManager.createBufferCompat();
        }
        if ((old = uniformBuffers.put("MotionBlurUniforms", motionBlurUBO)) != null && old != motionBlurUBO) {
            old.close();
        }
        try (GpuBufferSlice.MappedView view = motionBlurUBO.map(false, true);){
            Std140Builder builder = Std140Builder.intoBuffer((ByteBuffer)view.data());
            builder.putMat4f((Matrix4fc)tempMvInverse);
            builder.putMat4f((Matrix4fc)tempProjInverse);
            builder.putMat4f((Matrix4fc)tempPrevModelView);
            builder.putMat4f((Matrix4fc)tempPrevProjection);
            builder.putVec3(camDX, camDY, camDZ);
            builder.putVec2(viewW, viewH);
            builder.putFloat(blendFactor);
            builder.putInt(sampleAmount);
            builder.putInt(blurAlgorithm);
            builder.putInt(1);
        }
    }

    private static GpuBuffer createBufferCompat() {
        GpuDevice device = RenderSystem.getDevice();
        Supplier<String> name = () -> "fastclient-hud:MotionBlurUniforms";
        try {
            if (createBufferMethod == null) {
                createBufferMethod = device.getClass().getMethod("createBuffer", Supplier.class, Integer.TYPE, Long.TYPE);
            }
            return (GpuBuffer)createBufferMethod.invoke((Object)device, name, 130, 304L);
        }
        catch (NoSuchMethodException noSuchMethodException) {
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException("[FastClientHUD] createBuffer failed", e);
        }
        throw new RuntimeException("[FastClientHUD] No compatible createBuffer found on " + String.valueOf(device.getClass()));
    }

    public static void setFrameMotionBlur(Matrix4f modelView, Matrix4f prevModelView, Matrix4f projection, Matrix4f prevProjection, float dx, float dy, float dz) {
        tempMvInverse.set((Matrix4fc)scratchMatrix.set((Matrix4fc)modelView).invert());
        tempProjInverse.set((Matrix4fc)scratchMatrix.set((Matrix4fc)projection).invert());
        tempPrevModelView.set((Matrix4fc)prevModelView);
        tempPrevProjection.set((Matrix4fc)prevProjection);
        camDX = dx;
        camDY = dy;
        camDZ = dz;
    }

    public static boolean shouldExcludeEntities() {
        String setting;
        MotionBlurModule module = MotionBlurShaderManager.getModule();
        if (module == null || !module.isEnabled()) {
            return false;
        }
        return switch (setting = module.getExcludeEntities()) {
            case "Always" -> true;
            case "Third Person" -> {
                if (Minecraft.getInstance().options.getCameraType() != CameraType.FIRST_PERSON) {
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private static void updateDisplayInfo() {
        long now = System.nanoTime();
        if (now - lastCheckTime < 1000000000L) {
            return;
        }
        lastCheckTime = now;
        Minecraft client = Minecraft.getInstance();
        long window = client.getWindow().handle();
        long monitor = GLFW.glfwGetWindowMonitor((long)window);
        if (monitor == 0L) {
            monitor = MotionBlurShaderManager.getMonitorFromWindowPosition(window, client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight());
        }
        if (monitor != lastMonitorHandle) {
            GLFWVidMode vidMode = GLFW.glfwGetVideoMode((long)monitor);
            lastRefreshRate = vidMode != null ? vidMode.refreshRate() : 60;
            lastMonitorHandle = monitor;
        }
    }

    private static long getMonitorFromWindowPosition(long window, int windowWidth, int windowHeight) {
        int[] winX = new int[1];
        int[] winY = new int[1];
        GLFW.glfwGetWindowPos((long)window, (int[])winX, (int[])winY);
        int windowCenterX = winX[0] + windowWidth / 2;
        int windowCenterY = winY[0] + windowHeight / 2;
        long monitorResult = GLFW.glfwGetPrimaryMonitor();
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors != null) {
            for (int i = 0; i < monitors.limit(); ++i) {
                long m = monitors.get(i);
                int[] mx = new int[1];
                int[] my = new int[1];
                GLFW.glfwGetMonitorPos((long)m, (int[])mx, (int[])my);
                GLFWVidMode mode = GLFW.glfwGetVideoMode((long)m);
                if (mode == null) continue;
                int mw = mode.width();
                int mh = mode.height();
                if (windowCenterX < mx[0] || windowCenterX >= mx[0] + mw || windowCenterY < my[0] || windowCenterY >= my[0] + mh) continue;
                monitorResult = m;
                break;
            }
        }
        return monitorResult;
    }

    static {
        sampleAmount = 100;
        tempMvInverse = new Matrix4f();
        tempProjInverse = new Matrix4f();
        tempPrevModelView = new Matrix4f();
        tempPrevProjection = new Matrix4f();
        scratchMatrix = new Matrix4f();
        lastRefreshRate = 60;
    }
}


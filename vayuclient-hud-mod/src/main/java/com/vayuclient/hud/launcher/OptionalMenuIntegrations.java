/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.toasts.SystemToast
 *  net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId
 *  net.minecraft.client.gui.components.toasts.ToastManager
 *  net.minecraft.client.gui.screens.AlertScreen
 *  net.minecraft.client.gui.screens.ConfirmScreen
 *  net.minecraft.client.gui.screens.PauseScreen
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.resources.Identifier
 */
package com.vayuclient.hud.launcher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import com.vayuclient.hud.VayuHUDClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

public final class OptionalMenuIntegrations {
    private static final String MOD_MENU_ID = "modmenu";
    private static final String FLASHBACK_ID = "flashback";
    private static final String MOD_MENU_API = "com.terraformersmc.modmenu.api.ModMenuApi";
    private static final String FLASHBACK_MAIN = "com.moulberry.flashback.Flashback";
    private static final String FLASHBACK_REPLAY_SCREEN = "com.moulberry.flashback.screen.select_replay.SelectReplayScreen";
    private static final boolean MOD_MENU_LOADED = FabricLoader.getInstance().isModLoaded("modmenu");
    private static final boolean FLASHBACK_LOADED = FabricLoader.getInstance().isModLoaded("flashback");
    private static Boolean modMenuAvailable;
    private static Method modMenuCreateScreenMethod;
    private static Boolean flashbackReplaysAvailable;
    private static Method flashbackIncompatibleModsMethod;
    private static Constructor<?> flashbackReplayScreenConstructor;
    private static Boolean flashbackRecordingAvailable;
    private static Class<?> flashbackClass;
    private static Field flashbackRecorderField;
    private static Method flashbackIsInReplayMethod;
    private static Method flashbackStartRecordingMethod;
    private static Method flashbackFinishRecordingMethod;
    private static Method flashbackPauseRecordingMethod;
    private static Method flashbackCancelRecordingMethod;
    private static Method recorderIsPausedMethod;

    private OptionalMenuIntegrations() {
    }

    public static boolean isModMenuAvailable() {
        if (!MOD_MENU_LOADED) {
            return true; // Fallback to Vayu Mod Menu
        }
        if (modMenuAvailable != null) {
            return modMenuAvailable;
        }
        try {
            try {
                Class<?> modsScreenClass = OptionalMenuIntegrations.loadOptionalClass("com.terraformersmc.modmenu.gui.ModsScreen");
                if (Screen.class.isAssignableFrom(modsScreenClass)) {
                    modMenuAvailable = true;
                    return true;
                }
            } catch (Throwable ignored) {}

            try {
                Class<?> mmClass = OptionalMenuIntegrations.loadOptionalClass("com.terraformersmc.modmenu.ModMenu");
                modMenuCreateScreenMethod = mmClass.getMethod("createModsScreen", Screen.class);
                modMenuAvailable = true;
                return true;
            } catch (Throwable ignored) {}

            modMenuAvailable = true;
        }
        catch (Throwable error) {
            modMenuAvailable = true;
        }
        return true;
    }

    public static boolean isFlashbackReplaysAvailable() {
        if (!FLASHBACK_LOADED) {
            return false;
        }
        if (flashbackReplaysAvailable != null) {
            return flashbackReplaysAvailable;
        }
        try {
            Class<?> mainClass = OptionalMenuIntegrations.flashbackClass();
            flashbackIncompatibleModsMethod = OptionalMenuIntegrations.requireStaticMethod(mainClass, "getReplayIncompatibleMods", List.class, new Class[0]);
            Class<?> replayScreenClass = OptionalMenuIntegrations.loadOptionalClass(FLASHBACK_REPLAY_SCREEN);
            if (!Screen.class.isAssignableFrom(replayScreenClass)) {
                throw new NoSuchMethodException("Flashback replay selector is not a Minecraft screen");
            }
            flashbackReplayScreenConstructor = replayScreenClass.getConstructor(Screen.class);
            OptionalMenuIntegrations.requireFlashbackResource("icon_pixelated.png");
            flashbackReplaysAvailable = true;
        }
        catch (Throwable error) {
            OptionalMenuIntegrations.disableFlashbackReplays("the expected replay API or icon is unavailable", error);
        }
        return Boolean.TRUE.equals(flashbackReplaysAvailable);
    }

    public static boolean isFlashbackRecordingAvailable() {
        if (!FLASHBACK_LOADED) {
            return false;
        }
        if (flashbackRecordingAvailable != null) {
            return flashbackRecordingAvailable;
        }
        try {
            Class<?> mainClass = OptionalMenuIntegrations.flashbackClass();
            flashbackRecorderField = mainClass.getField("RECORDER");
            if (!Modifier.isStatic(flashbackRecorderField.getModifiers())) {
                throw new NoSuchFieldException("Flashback.RECORDER is no longer static");
            }
            recorderIsPausedMethod = OptionalMenuIntegrations.requireInstanceMethod(flashbackRecorderField.getType(), "isPaused", Boolean.TYPE, new Class[0]);
            flashbackIsInReplayMethod = OptionalMenuIntegrations.requireStaticMethod(mainClass, "isInReplay", Boolean.TYPE, new Class[0]);
            flashbackStartRecordingMethod = OptionalMenuIntegrations.requireStaticMethod(mainClass, "startRecordingReplay", Void.TYPE, new Class[0]);
            flashbackFinishRecordingMethod = OptionalMenuIntegrations.requireStaticMethod(mainClass, "finishRecordingReplay", Void.TYPE, new Class[0]);
            flashbackPauseRecordingMethod = OptionalMenuIntegrations.requireStaticMethod(mainClass, "pauseRecordingReplay", Void.TYPE, Boolean.TYPE);
            flashbackCancelRecordingMethod = OptionalMenuIntegrations.requireStaticMethod(mainClass, "cancelRecordingReplay", Void.TYPE, new Class[0]);
            OptionalMenuIntegrations.requireFlashbackResource("icon_pixelated_start.png");
            OptionalMenuIntegrations.requireFlashbackResource("icon_pixelated_finish.png");
            OptionalMenuIntegrations.requireFlashbackResource("icon_pixelated_pause.png");
            OptionalMenuIntegrations.requireFlashbackResource("icon_pixelated_cancel.png");
            flashbackRecordingAvailable = true;
        }
        catch (Throwable error) {
            OptionalMenuIntegrations.disableFlashbackRecording("the expected recording API or icons are unavailable", error);
        }
        return Boolean.TRUE.equals(flashbackRecordingAvailable);
    }

    public static boolean openModMenu(Screen parent) {
        try {
            Minecraft.getInstance().gui.setScreen(new com.vayuclient.hud.gui.screens.VayuModsScreen(parent));
            return true;
        }
        catch (Throwable error) {
            VayuHUDClient.LOGGER.error("Failed opening mod menu: {}", error.getMessage());
            try {
                Minecraft.getInstance().gui.setScreen(new com.vayuclient.hud.gui.screens.ClickGUIScreen());
            } catch (Throwable ignored) {}
            return true;
        }
    }

    public static boolean openFlashbackReplays(Screen parent) {
        if (!OptionalMenuIntegrations.isFlashbackReplaysAvailable()) {
            return false;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            List incompatibleMods = (List)flashbackIncompatibleModsMethod.invoke(null, new Object[0]);
            if (!minecraft.hasShiftDown() && incompatibleMods != null && !incompatibleMods.isEmpty()) {
                String mods = String.join((CharSequence)", ", incompatibleMods);
                MutableComponent description = Component.translatable((String)"flashback.incompatible_with_viewing_description").append((Component)Component.literal((String)mods).withStyle(ChatFormatting.RED));
                minecraft.gui.setScreen((Screen)new AlertScreen(() -> minecraft.gui.setScreen(parent), (Component)Component.translatable((String)"flashback.incompatible_with_viewing"), (Component)description));
                return true;
            }
            Object result = flashbackReplayScreenConstructor.newInstance(parent);
            if (!(result instanceof Screen)) {
                throw new IllegalStateException("Flashback returned a non-screen replay selector");
            }
            Screen screen = (Screen)result;
            minecraft.gui.setScreen(screen);
            return true;
        }
        catch (Throwable error) {
            OptionalMenuIntegrations.disableFlashbackReplays("opening the replay selector failed", error);
            OptionalMenuIntegrations.reportFailure("Flashback", "The incompatible Replays button was disabled", error);
            return false;
        }
    }

    public static FlashbackRecordingState getFlashbackRecordingState() {
        if (!OptionalMenuIntegrations.isFlashbackRecordingAvailable()) {
            return FlashbackRecordingState.HIDDEN;
        }
        try {
            if (((Boolean)flashbackIsInReplayMethod.invoke(null, new Object[0])).booleanValue()) {
                return FlashbackRecordingState.HIDDEN;
            }
            Object recorder = flashbackRecorderField.get(null);
            if (recorder == null) {
                return FlashbackRecordingState.READY;
            }
            Method isPaused = recorderIsPausedMethod;
            if (!isPaused.getDeclaringClass().isInstance(recorder)) {
                recorderIsPausedMethod = isPaused = OptionalMenuIntegrations.requireInstanceMethod(recorder.getClass(), "isPaused", Boolean.TYPE, new Class[0]);
            }
            return (Boolean)isPaused.invoke(recorder, new Object[0]) != false ? FlashbackRecordingState.PAUSED : FlashbackRecordingState.RECORDING;
        }
        catch (Throwable error) {
            OptionalMenuIntegrations.disableFlashbackRecording("reading the recording state failed", error);
            return FlashbackRecordingState.HIDDEN;
        }
    }

    public static boolean startFlashbackRecording() {
        if (!OptionalMenuIntegrations.isFlashbackRecordingAvailable()) {
            return false;
        }
        return OptionalMenuIntegrations.invokeFlashbackAndClosePause(flashbackStartRecordingMethod, new Object[0]);
    }

    public static boolean finishFlashbackRecording() {
        if (!OptionalMenuIntegrations.isFlashbackRecordingAvailable()) {
            return false;
        }
        return OptionalMenuIntegrations.invokeFlashbackAndClosePause(flashbackFinishRecordingMethod, new Object[0]);
    }

    public static boolean pauseFlashbackRecording(boolean paused) {
        if (!OptionalMenuIntegrations.isFlashbackRecordingAvailable()) {
            return false;
        }
        return OptionalMenuIntegrations.invokeFlashbackAndClosePause(flashbackPauseRecordingMethod, paused);
    }

    public static boolean confirmCancelFlashbackRecording() {
        if (!OptionalMenuIntegrations.isFlashbackRecordingAvailable()) {
            return false;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gui.setScreen((Screen)new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    if (OptionalMenuIntegrations.invokeFlashback(flashbackCancelRecordingMethod, new Object[0])) {
                        minecraft.gui.setScreen(null);
                    }
                } else {
                    minecraft.gui.setScreen((Screen)new PauseScreen(true));
                }
            }, (Component)Component.translatable((String)"flashback.confirm_cancel_recording"), (Component)Component.translatable((String)"flashback.confirm_cancel_recording_description")));
            return true;
        }
        catch (Throwable error) {
            OptionalMenuIntegrations.disableFlashbackRecording("opening the cancel confirmation failed", error);
            OptionalMenuIntegrations.reportFailure("Flashback", "The incompatible recording buttons were disabled", error);
            return false;
        }
    }

    private static boolean invokeFlashbackAndClosePause(Method method, Object ... arguments) {
        if (!OptionalMenuIntegrations.invokeFlashback(method, arguments)) {
            return false;
        }
        Minecraft.getInstance().gui.setScreen(null);
        return true;
    }

    private static boolean invokeFlashback(Method method, Object ... arguments) {
        if (!OptionalMenuIntegrations.isFlashbackRecordingAvailable()) {
            return false;
        }
        try {
            method.invoke(null, arguments);
            return true;
        }
        catch (Throwable error) {
            OptionalMenuIntegrations.disableFlashbackRecording("performing a recording action failed", error);
            OptionalMenuIntegrations.reportFailure("Flashback", "The incompatible recording buttons were disabled", error);
            return false;
        }
    }

    private static Class<?> flashbackClass() throws ClassNotFoundException {
        if (flashbackClass == null) {
            flashbackClass = OptionalMenuIntegrations.loadOptionalClass(FLASHBACK_MAIN);
        }
        return flashbackClass;
    }

    private static Class<?> loadOptionalClass(String className) throws ClassNotFoundException {
        return Class.forName(className, false, OptionalMenuIntegrations.class.getClassLoader());
    }

    private static Method requireStaticMethod(Class<?> owner, String name, Class<?> returnType, Class<?> ... parameterTypes) throws ReflectiveOperationException {
        Method method = owner.getMethod(name, parameterTypes);
        if (!Modifier.isStatic(method.getModifiers()) || !returnType.isAssignableFrom(method.getReturnType())) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " has an incompatible signature");
        }
        return method;
    }

    private static Method requireInstanceMethod(Class<?> owner, String name, Class<?> returnType, Class<?> ... parameterTypes) throws ReflectiveOperationException {
        Method method = owner.getMethod(name, parameterTypes);
        if (Modifier.isStatic(method.getModifiers()) || !returnType.isAssignableFrom(method.getReturnType())) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " has an incompatible signature");
        }
        return method;
    }

    private static void requireFlashbackResource(String path) {
        Identifier id = Identifier.fromNamespaceAndPath((String)FLASHBACK_ID, (String)path);
        if (Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()) {
            throw new IllegalStateException("Missing Flashback resource " + String.valueOf(id));
        }
    }

    private static void disableModMenu(String reason, Throwable error) {
        modMenuAvailable = false;
        OptionalMenuIntegrations.logDisabled("Mod Menu", reason, error);
    }

    private static void disableFlashbackReplays(String reason, Throwable error) {
        flashbackReplaysAvailable = false;
        OptionalMenuIntegrations.logDisabled("Flashback replay", reason, error);
    }

    private static void disableFlashbackRecording(String reason, Throwable error) {
        flashbackRecordingAvailable = false;
        OptionalMenuIntegrations.logDisabled("Flashback recording", reason, error);
    }

    private static void logDisabled(String integration, String reason, Throwable error) {
        VayuHUDClient.LOGGER.warn("Disabled optional {} integration because {}", new Object[]{integration, reason, OptionalMenuIntegrations.unwrap(error)});
    }

    private static Throwable unwrap(Throwable error) {
        InvocationTargetException invocation;
        if (error instanceof InvocationTargetException && (invocation = (InvocationTargetException)error).getCause() != null) {
            return invocation.getCause();
        }
        return error;
    }

    private static void reportFailure(String integration, String message, Throwable error) {
        VayuHUDClient.LOGGER.error("{} integration failed: {}", new Object[]{integration, message, OptionalMenuIntegrations.unwrap(error)});
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.add((ToastManager)minecraft.gui.toastManager(), (SystemToast.SystemToastId)SystemToast.SystemToastId.PERIODIC_NOTIFICATION, (Component)Component.literal((String)integration), (Component)Component.literal((String)message));
    }

    public static enum FlashbackRecordingState {
        HIDDEN,
        READY,
        RECORDING,
        PAUSED;

    }
}


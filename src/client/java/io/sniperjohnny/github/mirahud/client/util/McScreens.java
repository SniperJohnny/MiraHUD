package io.sniperjohnny.github.mirahud.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Cross-version access to the current screen and toast manager.
 *
 * <p>In 26.1 the screen state lives on {@link Minecraft} ({@code Minecraft.setScreen}
 * and the {@code screen} field). 26.2 moved it onto {@link Gui} ({@code Gui.setScreen},
 * the {@code screen} field and the {@code screen()} getter), along with the toast
 * manager ({@code Gui.toastManager()} vs {@code Minecraft.getToastManager()}).
 *
 * <p>All lookups go through reflection so this single class compiles and runs on
 * both versions: at runtime we prefer the 26.2 location when it exists and fall
 * back to the 26.1 one otherwise.
 */
public final class McScreens {

    private static Method GUI_SET_SCREEN;
    private static Method MC_SET_SCREEN;
    private static Method GUI_GET_SCREEN;
    private static Field MC_SCREEN_FIELD;
    private static Method GUI_TOAST_MANAGER;
    private static Method MC_TOAST_MANAGER;

    static {
        try {
            GUI_SET_SCREEN = Gui.class.getMethod("setScreen", Screen.class);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            MC_SET_SCREEN = Minecraft.class.getMethod("setScreen", Screen.class);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            GUI_GET_SCREEN = Gui.class.getMethod("screen");
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            MC_SCREEN_FIELD = Minecraft.class.getDeclaredField("screen");
            MC_SCREEN_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            GUI_TOAST_MANAGER = Gui.class.getMethod("toastManager");
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            MC_TOAST_MANAGER = Minecraft.class.getMethod("getToastManager");
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private McScreens() {
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        if (GUI_SET_SCREEN != null) {
            try {
                GUI_SET_SCREEN.invoke(mc.gui, screen);
                return;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to open screen on " + GUI_SET_SCREEN, e);
            }
        }
        try {
            MC_SET_SCREEN.invoke(mc, screen);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to open screen on " + MC_SET_SCREEN, e);
        }
    }

    public static Screen getScreen(Minecraft mc) {
        if (GUI_GET_SCREEN != null) {
            try {
                return (Screen) GUI_GET_SCREEN.invoke(mc.gui);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to read current screen from " + GUI_GET_SCREEN, e);
            }
        }
        try {
            return (Screen) MC_SCREEN_FIELD.get(mc);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read current screen from " + MC_SCREEN_FIELD, e);
        }
    }

    public static ToastManager getToastManager(Minecraft mc) {
        if (GUI_TOAST_MANAGER != null) {
            try {
                return (ToastManager) GUI_TOAST_MANAGER.invoke(mc.gui);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to get toast manager from " + GUI_TOAST_MANAGER, e);
            }
        }
        try {
            return (ToastManager) MC_TOAST_MANAGER.invoke(mc);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to get toast manager from " + MC_TOAST_MANAGER, e);
        }
    }
}
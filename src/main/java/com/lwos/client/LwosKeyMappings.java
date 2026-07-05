package com.lwos.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class LwosKeyMappings {
    public static final String CATEGORY = "key.categories.lwos";

    public static final KeyMapping TOGGLE_MODE = new KeyMapping(
            "key.lwos.toggle_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    public static final KeyMapping DELETE_POINT = new KeyMapping(
            "key.lwos.delete_point", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static final KeyMapping CANCEL_PATH = new KeyMapping(
            "key.lwos.cancel_path", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    public static final KeyMapping WIDTH_UP = new KeyMapping(
            "key.lwos.width_up", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);

    public static final KeyMapping WIDTH_DOWN = new KeyMapping(
            "key.lwos.width_down", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);

    public static final KeyMapping COMMIT = new KeyMapping(
            "key.lwos.commit", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY);

    public static final KeyMapping TOGGLE_TERRAIN_MODE = new KeyMapping(
            "key.lwos.toggle_terrain_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    /** Re-reads config/lwos-organic.json so a builder can tune the organic look live (M5 DoD). */
    public static final KeyMapping RELOAD_TUNABLES = new KeyMapping(
            "key.lwos.reload_tunables", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    private LwosKeyMappings() { }
}

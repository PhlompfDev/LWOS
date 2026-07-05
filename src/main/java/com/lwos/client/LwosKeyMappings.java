package com.lwos.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class LwosKeyMappings {
    public static final String CATEGORY = "key.categories.lwos";

    public static final KeyMapping TOGGLE_MODE = new KeyMapping(
            "key.lwos.toggle_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    public static final KeyMapping DELETE_POINT = new KeyMapping(
            "key.lwos.delete_point", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static final KeyMapping REDO_POINT = new KeyMapping(
            "key.lwos.redo_point", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

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

    public static final KeyMapping TOGGLE_STYLE_PANEL = new KeyMapping(
            "key.lwos.toggle_style_panel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    public static final KeyMapping PICK_BLOCK = new KeyMapping(
            "key.lwos.pick_block", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);

    public static final KeyMapping UNDO = new KeyMapping(
            "key.lwos.undo", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static final KeyMapping REDO = new KeyMapping(
            "key.lwos.redo", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    private LwosKeyMappings() { }
}

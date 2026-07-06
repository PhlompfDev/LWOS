package com.lwos;

/**
 * Single source of truth for who may drive the LWOS builder tools. Only the named player is allowed.
 *
 * <p>Pure (no Minecraft types) so it can gate both sides: the client uses it as a UX gate (tools are
 * inert for anyone else) and — authoritatively — every server packet handler re-checks it before
 * mutating the world, so a modified client can't bypass the client gate. Name-based, which is
 * reliable on online-mode (premium-authenticated) servers.
 */
public final class LwosAccess {

    /** The only player permitted to use the builder tools. */
    public static final String ALLOWED_NAME = "Plomph";

    private LwosAccess() { }

    /** True only for the authorized player's account name. */
    public static boolean isAllowed(String playerName) {
        return ALLOWED_NAME.equals(playerName);
    }
}

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

    /** Players permitted to use the builder tools. */
    private static final String[] ALLOWED_NAMES = { "Plomph", "Dev" };

    private LwosAccess() { }

    /** True only for an authorized player's account name. */
    public static boolean isAllowed(String playerName) {
        for (String name : ALLOWED_NAMES) {
            if (name.equals(playerName)) {
                return true;
            }
        }
        return false;
    }
}

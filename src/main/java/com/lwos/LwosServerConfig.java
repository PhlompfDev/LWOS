package com.lwos;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server config (lwos-server.toml, spec §7). survivalMode makes shape commits consume
 * inventory items and drop broken blocks; maxBlocksPerCommit hard-caps every shape
 * commit regardless of mode. Enforcement is entirely server-side.
 */
public final class LwosServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SURVIVAL_MODE;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_COMMIT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("shapes");
        SURVIVAL_MODE = b
                .comment("When true, shape placements consume matching items from the player's",
                         "inventory (whole commit rejected on shortfall) and shape breaks drop items.")
                .define("survivalMode", false);
        MAX_BLOCKS_PER_COMMIT = b
                .comment("Hard cap on blocks a single shape commit may change.")
                .defineInRange("maxBlocksPerCommit", 32768, 1, 1_000_000);
        b.pop();
        SPEC = b.build();
    }

    private LwosServerConfig() { }
}

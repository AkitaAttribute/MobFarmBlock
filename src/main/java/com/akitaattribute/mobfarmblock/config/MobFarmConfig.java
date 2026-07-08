package com.akitaattribute.mobfarmblock.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MobFarmConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_CHAT_MESSAGES;
    public static final ModConfigSpec.BooleanValue DEBUG_COBBLEMON_JSON_DUMP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DEBUG_CHAT_MESSAGES = builder
                .comment("Send player-facing debug messages for capture, insertion, processing, and breeding actions.")
                .translation("mob_farm_block.configuration.debugChatMessages")
                .define("debugChatMessages", true);
        DEBUG_COBBLEMON_JSON_DUMP = builder
                .comment("Write JSON Cobblemon entity debug dumps when capturing or inserting cobblemon:pokemon.")
                .translation("mob_farm_block.configuration.debugCobblemonJsonDump")
                .define("debugCobblemonJsonDump", true);
        COMMON_SPEC = builder.build();
    }

    private MobFarmConfig() {}
}

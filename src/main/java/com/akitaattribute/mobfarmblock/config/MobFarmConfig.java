package com.akitaattribute.mobfarmblock.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MobFarmConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_CHAT_MESSAGES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DEBUG_CHAT_MESSAGES = builder
                .comment("Send player-facing debug messages for capture, insertion, and breeding actions.")
                .define("debugChatMessages", true);
        COMMON_SPEC = builder.build();
    }

    private MobFarmConfig() {}
}

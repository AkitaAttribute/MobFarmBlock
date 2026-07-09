package com.akitaattribute.mobfarmblock.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MobFarmConfig {
    public enum LookUiStyle {
        NAMETAG
    }

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_CHAT_MESSAGES;
    public static final ModConfigSpec.BooleanValue DEBUG_COBBLEMON_JSON_DUMP;
    public static final ModConfigSpec.EnumValue<LookUiStyle> LOOK_UI_STYLE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DEBUG_CHAT_MESSAGES = builder
                .comment("Send player-facing debug messages for capture, insertion, processing, and breeding actions.")
                .translation("mob_farm_block.configuration.debugChatMessages")
                .define("debugChatMessages", false);
        DEBUG_COBBLEMON_JSON_DUMP = builder
                .comment("Write JSON Cobblemon entity debug dumps when capturing or inserting cobblemon:pokemon.")
                .translation("mob_farm_block.configuration.debugCobblemonJsonDump")
                .define("debugCobblemonJsonDump", true);
        LOOK_UI_STYLE = builder
                .comment("Look UI Style. NAMETAG is the current floating nametag-style debug overlay.")
                .translation("mob_farm_block.configuration.lookUiStyle")
                .defineEnum("lookUiStyle", LookUiStyle.NAMETAG);
        COMMON_SPEC = builder.build();
    }

    private MobFarmConfig() {}
}

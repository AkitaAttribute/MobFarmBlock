package com.akitaattribute.mobfarmblock.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MobFarmConfig {
    public enum LookUiStyle {
        NAMETAG
    }

    public enum PixelmonRenderReplayMode {
        HYBRID_ALL,
        ENTITY_PAYLOAD_ONLY,
        ENTITY_PAYLOAD_SIZE_AFTER_LOAD,
        POKEMON_FACTORY_SIZE_BEFORE_ENTITY,
        POKEMON_FACTORY_SIZE_AFTER_ENTITY
    }

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_CHAT_MESSAGES;
    public static final ModConfigSpec.BooleanValue DEBUG_COBBLEMON_JSON_DUMP;
    public static final ModConfigSpec.BooleanValue PIXELMON_ENTITY_TRACKING_LOG;
    public static final ModConfigSpec.BooleanValue PIXELMON_NPC_REMOVAL_ENABLED;
    public static final ModConfigSpec.BooleanValue PENS_ALWAYS_SHOW_SMALL;
    public static final ModConfigSpec.EnumValue<PixelmonRenderReplayMode> PIXELMON_RENDER_REPLAY_MODE;
    public static final ModConfigSpec.EnumValue<LookUiStyle> LOOK_UI_STYLE;
    public static final ModConfigSpec.DoubleValue PIXELMON_CAPTURE_TOOL_DROP_CHANCE;

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
        PIXELMON_ENTITY_TRACKING_LOG = builder
                .comment("Write short JSONL logs for Pixelmon NPC tracking and protected NPC identification.")
                .translation("mob_farm_block.configuration.pixelmonEntityTrackingLog")
                .define("pixelmonEntityTrackingLog", true);
        PIXELMON_NPC_REMOVAL_ENABLED = builder
                .comment("Remove unprotected Pixelmon NPCs after this mod has observed them for five minutes. Protected titled NPCs are not removed.")
                .translation("mob_farm_block.configuration.pixelmonNpcRemovalEnabled")
                .define("pixelmonNpcRemovalEnabled", true);
        PENS_ALWAYS_SHOW_SMALL = builder
                .comment("Always render stored mobs in their small pen preview size instead of only shrinking oversized previews while looking at the pen.")
                .translation("mob_farm_block.configuration.pensAlwaysShowSmall")
                .define("pensAlwaysShowSmall", true);
        PIXELMON_RENDER_REPLAY_MODE = builder
                .comment("Pixelmon preview reconstruction strategy. HYBRID_ALL tries saved entity payload first, then Pokemon factory reconstruction, and applies captured size to Pokemon/entity/delegate before and after refresh.")
                .translation("mob_farm_block.configuration.pixelmonRenderReplayMode")
                .defineEnum("pixelmonRenderReplayMode", PixelmonRenderReplayMode.HYBRID_ALL);
        LOOK_UI_STYLE = builder
                .comment("Look UI Style. NAMETAG is the current floating nametag-style debug overlay.")
                .translation("mob_farm_block.configuration.lookUiStyle")
                .defineEnum("lookUiStyle", LookUiStyle.NAMETAG);
        PIXELMON_CAPTURE_TOOL_DROP_CHANCE = builder
                .comment("Chance for defeated Pixelmon loot UIs to include a filled Mob Farm Capture Tool. 1.0 = 100%, 0.01 = 1%, 0.0 = disabled.")
                .translation("mob_farm_block.configuration.pixelmonCaptureToolDropChance")
                .defineInRange("pixelmonCaptureToolDropChance", 0.01D, 0.0D, 1.0D);
        COMMON_SPEC = builder.build();
    }

    private MobFarmConfig() {}
}

package com.akitaattribute.mobfarmblock.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PixelmonEntityTracker {
    private static final long SCAN_INTERVAL_TICKS = 200L;
    private static final long UPDATE_LOG_INTERVAL_TICKS = 1200L;
    private static final Path LOG_FILE = Path.of("config", "mob_farm_block", "debug", "pixelmon_entity_tracker", "pixelmon_entities.jsonl");
    private static final Map<UUID, Long> LAST_LOGGED_AGE_TICKS = new HashMap<>();
    private static long nextScanTick = 0L;

    private PixelmonEntityTracker() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!MobFarmConfig.PIXELMON_ENTITY_TRACKING_LOG.get() || event.getLevel().isClientSide()) return;
        track(event.getEntity(), "join");
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!MobFarmConfig.PIXELMON_ENTITY_TRACKING_LOG.get()) return;
        MinecraftServer server = event.getServer();
        long tick = server.overworld().getGameTime();
        if (tick < nextScanTick) return;
        nextScanTick = tick + SCAN_INTERVAL_TICKS;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                track(entity, "scan");
            }
        }
    }

    private static void track(Entity entity, String source) {
        if (!isTrackedPixelmonEntity(entity)) return;
        UUID uuid = entity.getUUID();
        long ageTicks = Math.max(0L, entity.tickCount);
        Long lastLogged = LAST_LOGGED_AGE_TICKS.get(uuid);
        String action;
        if (lastLogged == null) action = "identified";
        else {
            if (ageTicks - lastLogged < UPDATE_LOG_INTERVAL_TICKS) return;
            action = "age_update";
        }
        LAST_LOGGED_AGE_TICKS.put(uuid, ageTicks);
        writeLog(entity, source, action, ageTicks);
    }

    private static boolean isTrackedPixelmonEntity(Entity entity) {
        if (entity == null) return false;
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String typeText = typeId == null ? "" : typeId.toString().toLowerCase(Locale.ROOT);
        String path = typeId == null ? "" : typeId.getPath().toLowerCase(Locale.ROOT);
        String className = entity.getClass().getName().toLowerCase(Locale.ROOT);
        boolean pixelmonOwned = typeText.startsWith("pixelmon:") || className.contains("pixelmon");
        if (!pixelmonOwned) return false;
        if ("pixelmon:pixelmon".equals(typeText) || className.endsWith(".pixelmonentity")) return false;
        if (path.contains("trainer") || path.contains("npc") || className.contains("trainer") || className.contains("npc")) return true;
        return typeText.startsWith("pixelmon:") && !className.contains("entities.pixelmon.pixelmonentity");
    }

    private static String classification(Entity entity) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String path = typeId == null ? "" : typeId.getPath().toLowerCase(Locale.ROOT);
        String className = entity.getClass().getName().toLowerCase(Locale.ROOT);
        if (path.contains("trainer") || className.contains("trainer")) return "trainer";
        if (path.contains("npc") || className.contains("npc")) return "npc";
        return "pixelmon_non_pokemon";
    }

    private static void writeLog(Entity entity, String source, String action, long ageTicks) {
        try {
            Files.createDirectories(LOG_FILE.toAbsolutePath().getParent());
            String line = jsonLine(entity, source, action, ageTicks);
            Files.writeString(LOG_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write Pixelmon entity tracker log", error);
        }
    }

    private static String jsonLine(Entity entity, String source, String action, long ageTicks) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        StringBuilder out = new StringBuilder("{");
        prop(out, "time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), true);
        prop(out, "source", source, true);
        prop(out, "action", action, true);
        prop(out, "classification", classification(entity), true);
        prop(out, "entityType", typeId == null ? "unknown" : typeId.toString(), true);
        prop(out, "entityClass", entity.getClass().getName(), true);
        prop(out, "uuid", entity.getUUID().toString(), true);
        prop(out, "displayName", entity.getDisplayName().getString(), true);
        prop(out, "customName", entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : null, true);
        prop(out, "dimension", entity.level().dimension().location().toString(), true);
        prop(out, "x", round(entity.getX()), true);
        prop(out, "y", round(entity.getY()), true);
        prop(out, "z", round(entity.getZ()), true);
        prop(out, "ageTicks", ageTicks, true);
        prop(out, "ageSeconds", round(ageTicks / 20.0D), false);
        out.append('}');
        return out.toString();
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static void prop(StringBuilder out, String key, String value, boolean comma) {
        out.append(quote(key)).append(':').append(value == null ? "null" : quote(value));
        if (comma) out.append(',');
    }

    private static void prop(StringBuilder out, String key, long value, boolean comma) {
        out.append(quote(key)).append(':').append(value);
        if (comma) out.append(',');
    }

    private static void prop(StringBuilder out, String key, double value, boolean comma) {
        out.append(quote(key)).append(':').append(String.format(Locale.ROOT, "%.2f", value));
        if (comma) out.append(',');
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

package com.akitaattribute.mobfarmblock.integration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PixelmonEntityTracker {
    private static final long SCAN_INTERVAL_TICKS = 200L;
    private static final long UPDATE_LOG_INTERVAL_TICKS = 1200L;
    private static final int MAX_PROBE_VALUES = 80;
    private static final int MAX_PROBE_DEPTH = 3;
    private static final Path LOG_FILE = Path.of("config", "mob_farm_block", "debug", "pixelmon_entity_tracker", "pixelmon_entities.jsonl");
    private static final Path PROTECTED_LOG_FILE = Path.of("config", "mob_farm_block", "debug", "pixelmon_entity_tracker", "protected_pixelmon_npcs.jsonl");
    private static final Map<UUID, Long> FIRST_SEEN_GAME_TIME = new HashMap<>();
    private static final Map<UUID, Long> LAST_LOGGED_OBSERVED_TICKS = new HashMap<>();
    private static final Set<UUID> PROTECTED_LOGGED = new HashSet<>();
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
        long now = entity.level().getGameTime();
        long firstSeen = FIRST_SEEN_GAME_TIME.computeIfAbsent(uuid, ignored -> now);
        long observedTicks = Math.max(0L, now - firstSeen);
        Long lastLogged = LAST_LOGGED_OBSERVED_TICKS.get(uuid);
        String action;
        if (lastLogged == null) action = "identified";
        else {
            if (observedTicks - lastLogged < UPDATE_LOG_INTERVAL_TICKS) return;
            action = "observed_age_update";
        }
        LAST_LOGGED_OBSERVED_TICKS.put(uuid, observedTicks);
        writeLog(entity, source, action, now, firstSeen, observedTicks);
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

    private static String classification(Entity entity, ProtectionInfo protection) {
        if (protection.protectedNpc()) {
            if ("nurse".equals(protection.roleKey())) return "protected_nurse";
            if ("shopkeeper".equals(protection.roleKey())) return "protected_shopkeeper";
            if ("fixed_trainer".equals(protection.roleKey())) return "protected_fixed_trainer";
            return "protected_npc";
        }
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String path = typeId == null ? "" : typeId.getPath().toLowerCase(Locale.ROOT);
        String className = entity.getClass().getName().toLowerCase(Locale.ROOT);
        if (path.contains("trainer") || className.contains("trainer")) return "trainer";
        if (path.contains("npc") || className.contains("npc")) return "npc";
        return "pixelmon_non_pokemon";
    }

    private static ProtectionInfo protectionInfo(Map<String, String> probe) {
        for (Map.Entry<String, String> entry : probe.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            String value = entry.getValue() == null ? "" : entry.getValue().toLowerCase(Locale.ROOT);
            String combined = key + " " + value;
            if (combined.contains("nurse") || combined.contains("healer")) {
                return new ProtectionInfo(true, "nurse", "Nurse", "role/title probe contains nurse/healer", entry.getKey(), entry.getValue());
            }
            if (combined.contains("shopkeeper") || combined.contains("shop_keeper") || combined.contains("shop keeper") || combined.contains("merchant") || combined.contains("seller")) {
                return new ProtectionInfo(true, "shopkeeper", "Shopkeeper", "role/title probe contains shopkeeper/merchant/seller", entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : probe.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            String value = entry.getValue() == null ? "" : entry.getValue().toLowerCase(Locale.ROOT);
            if (titleLikeProbe(key) && trainerTitleValue(value)) {
                return new ProtectionInfo(true, "fixed_trainer", protectedTrainerRole(entry.getValue()), "role/title probe contains fixed trainer title", entry.getKey(), entry.getValue());
            }
        }
        return ProtectionInfo.NONE;
    }

    private static boolean titleLikeProbe(String key) {
        return key.contains("title")
                || key.contains("role")
                || key.contains("profession")
                || key.contains("occupation")
                || key.contains("job");
    }

    private static boolean trainerTitleValue(String value) {
        String normalized = value.replace('_', ' ').replace('-', ' ').trim();
        return normalized.equals("trainer")
                || normalized.endsWith(" trainer")
                || normalized.contains(" trainer ")
                || normalized.startsWith("trainer ")
                || normalized.contains("gym leader")
                || normalized.contains("move tutor")
                || normalized.contains("tutor")
                || normalized.contains("professor");
    }

    private static String protectedTrainerRole(String value) {
        if (value == null || value.isBlank()) return "Fixed Trainer";
        return prettyRole(value);
    }

    private static String prettyRole(String value) {
        String cleaned = value.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) return value;
        StringBuilder out = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.toString();
    }

    private static void writeLog(Entity entity, String source, String action, long gameTime, long firstSeenGameTime, long observedTicks) {
        try {
            Files.createDirectories(LOG_FILE.toAbsolutePath().getParent());
            Map<String, String> probe = npcProbe(entity);
            ProtectionInfo protection = protectionInfo(probe);
            String line = jsonLine(entity, source, action, gameTime, firstSeenGameTime, observedTicks, probe, protection);
            Files.writeString(LOG_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            if (protection.protectedNpc() && PROTECTED_LOGGED.add(entity.getUUID())) writeProtectedLog(entity, protection);
        } catch (IOException error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write Pixelmon entity tracker log", error);
        }
    }

    private static void writeProtectedLog(Entity entity, ProtectionInfo protection) {
        try {
            Files.createDirectories(PROTECTED_LOG_FILE.toAbsolutePath().getParent());
            Files.writeString(PROTECTED_LOG_FILE, protectedJsonLine(entity, protection) + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write protected Pixelmon NPC log", error);
        }
    }

    private static String jsonLine(Entity entity, String source, String action, long gameTime, long firstSeenGameTime, long observedTicks, Map<String, String> probe, ProtectionInfo protection) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        StringBuilder out = new StringBuilder("{");
        prop(out, "time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), true);
        prop(out, "source", source, true);
        prop(out, "action", action, true);
        prop(out, "classification", classification(entity, protection), true);
        prop(out, "protected", protection.protectedNpc(), true);
        prop(out, "protectedRole", protection.role(), true);
        prop(out, "protectedWhy", protection.why(), true);
        prop(out, "entityType", typeId == null ? "unknown" : typeId.toString(), true);
        prop(out, "entityClass", entity.getClass().getName(), true);
        prop(out, "uuid", entity.getUUID().toString(), true);
        prop(out, "displayName", entity.getDisplayName().getString(), true);
        prop(out, "customName", entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : null, true);
        prop(out, "dimension", entity.level().dimension().location().toString(), true);
        prop(out, "x", round(entity.getX()), true);
        prop(out, "y", round(entity.getY()), true);
        prop(out, "z", round(entity.getZ()), true);
        prop(out, "gameTime", gameTime, true);
        prop(out, "firstSeenGameTime", firstSeenGameTime, true);
        prop(out, "observedAgeTicks", observedTicks, true);
        prop(out, "observedAgeSeconds", round(observedTicks / 20.0D), true);
        prop(out, "minecraftEntityTickCount", Math.max(0L, entity.tickCount), true);
        prop(out, "minecraftEntityTickCountSeconds", round(Math.max(0L, entity.tickCount) / 20.0D), true);
        out.append(quote("npcProbe")).append(':').append(mapJson(probe)).append(',');
        out.append(quote("entityNbtSummary")).append(':').append(nbtSummary(entity));
        out.append('}');
        return out.toString();
    }

    private static String protectedJsonLine(Entity entity, ProtectionInfo protection) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        StringBuilder out = new StringBuilder("{");
        prop(out, "time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), true);
        prop(out, "name", entity.getDisplayName().getString(), true);
        prop(out, "role", protection.role(), true);
        prop(out, "whyProtected", protection.why(), true);
        prop(out, "evidencePath", protection.evidencePath(), true);
        prop(out, "evidenceValue", protection.evidenceValue(), true);
        prop(out, "uuid", entity.getUUID().toString(), true);
        prop(out, "entityType", typeId == null ? "unknown" : typeId.toString(), true);
        prop(out, "dimension", entity.level().dimension().location().toString(), true);
        prop(out, "x", round(entity.getX()), true);
        prop(out, "y", round(entity.getY()), true);
        prop(out, "z", round(entity.getZ()), false);
        out.append('}');
        return out.toString();
    }

    private static Map<String, String> npcProbe(Entity entity) {
        Map<String, String> out = new HashMap<>();
        collectProbe(entity, "entity", out, new IdentityHashMap<>(), 0);
        return out.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(MAX_PROBE_VALUES)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static void collectProbe(Object value, String path, Map<String, String> out, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (value == null || depth > MAX_PROBE_DEPTH || out.size() >= MAX_PROBE_VALUES) return;
        if (simple(value)) {
            if (interestingProbePath(path) || interestingProbeValue(String.valueOf(value))) out.put(path, safeString(value));
            return;
        }
        if (seen.put(value, Boolean.TRUE) != null) return;
        Class<?> type = value.getClass();
        if (interestingProbePath(path) || interestingProbeValue(String.valueOf(value))) out.put(path + ".toString", safeString(value));

        List<Method> methods = new ArrayList<>(List.of(type.getMethods()));
        methods.sort(Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (out.size() >= MAX_PROBE_VALUES) return;
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) continue;
            if (!safeAccessor(method.getName())) continue;
            if (!interestingProbeName(method.getName())) continue;
            try {
                method.setAccessible(true);
                Object result = method.invoke(value);
                collectProbe(result, path + "." + method.getName() + "()", out, seen, depth + 1);
            } catch (Throwable error) {
                out.put(path + "." + method.getName() + "()", "ERROR:" + error.getClass().getSimpleName());
            }
        }

        List<Field> fields = allFields(type);
        fields.sort(Comparator.comparing(Field::getName));
        for (Field field : fields) {
            if (out.size() >= MAX_PROBE_VALUES) return;
            if (!interestingProbeName(field.getName())) continue;
            try {
                field.setAccessible(true);
                Object result = field.get(value);
                collectProbe(result, path + "." + field.getName(), out, seen, depth + 1);
            } catch (Throwable error) {
                out.put(path + "." + field.getName(), "ERROR:" + error.getClass().getSimpleName());
            }
        }
    }

    private static boolean safeAccessor(String name) {
        return name.startsWith("get") || name.startsWith("is") || name.startsWith("has") || name.equals("toString");
    }

    private static boolean interestingProbeName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("title")
                || lower.contains("role")
                || lower.contains("profession")
                || lower.contains("occupation")
                || lower.contains("job")
                || lower.contains("type")
                || lower.contains("npc")
                || lower.contains("trainer")
                || lower.contains("shop")
                || lower.contains("merchant")
                || lower.contains("seller")
                || lower.contains("nurse")
                || lower.contains("healer")
                || lower.contains("name");
    }

    private static boolean interestingProbePath(String path) {
        return interestingProbeName(path);
    }

    private static boolean interestingProbeValue(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("nurse")
                || lower.contains("shopkeeper")
                || lower.contains("shop_keeper")
                || lower.contains("shop keeper")
                || lower.contains("merchant")
                || lower.contains("seller")
                || lower.contains("trainer")
                || lower.contains("tutor")
                || lower.contains("professor")
                || lower.contains("gym leader")
                || lower.contains("npc")
                || lower.contains("healer");
    }

    private static String nbtSummary(Entity entity) {
        try {
            CompoundTag tag = new CompoundTag();
            entity.saveWithoutId(tag);
            StringBuilder out = new StringBuilder("{");
            int count = 0;
            for (String key : tag.getAllKeys().stream().sorted().toList()) {
                if (!interestingProbeName(key) && !interestingProbeValue(String.valueOf(tag.get(key)))) continue;
                if (count++ > 0) out.append(',');
                out.append(quote(key)).append(':').append(quote(String.valueOf(tag.get(key))));
                if (count >= MAX_PROBE_VALUES) break;
            }
            return out.append('}').toString();
        } catch (Throwable error) {
            return quote("ERROR:" + error.getClass().getSimpleName());
        }
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(List.of(current.getDeclaredFields()));
        }
        return fields;
    }

    private static boolean simple(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Character || value instanceof Enum<?> || value instanceof ResourceLocation;
    }

    private static String safeString(Object value) {
        if (value == null) return "null";
        String text = String.valueOf(value);
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }

    private static String mapJson(Map<String, String> map) {
        StringBuilder out = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (index++ > 0) out.append(',');
            out.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        return out.append('}').toString();
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

    private static void prop(StringBuilder out, String key, boolean value, boolean comma) {
        out.append(quote(key)).append(':').append(value);
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

    private record ProtectionInfo(boolean protectedNpc, String roleKey, String role, String why, String evidencePath, String evidenceValue) {
        private static final ProtectionInfo NONE = new ProtectionInfo(false, "", null, null, null, null);
    }
}

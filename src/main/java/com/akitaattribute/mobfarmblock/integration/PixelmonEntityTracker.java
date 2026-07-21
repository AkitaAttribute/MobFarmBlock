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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PixelmonEntityTracker {
    private static final long CHECK_INTERVAL_TICKS = 200L;
    private static final long UPDATE_LOG_INTERVAL_TICKS = 1200L;
    private static final long REMOVAL_AGE_TICKS = 6000L;
    private static final int MAX_PROBE_VALUES = 80;
    private static final int MAX_PROBE_DEPTH = 3;
    private static final Pattern TRANSLATION_KEY = Pattern.compile("key='([^']+)'");
    private static final Path LOG_FILE = Path.of("config", "mob_farm_block", "debug", "pixelmon_entity_tracker", "pixelmon_entities.jsonl");
    private static final Path PROTECTED_LOG_FILE = Path.of("config", "mob_farm_block", "debug", "pixelmon_entity_tracker", "protected_pixelmon_npcs.jsonl");
    private static final Map<UUID, TrackedNpc> TRACKED_NPCS = new HashMap<>();
    private static final Set<UUID> PROTECTED_LOGGED = new HashSet<>();
    private static long nextCheckTick = 0L;

    private PixelmonEntityTracker() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!shouldRun() || event.getLevel().isClientSide()) return;
        trackLoadedEntity(event.getEntity(), "join");
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!shouldRun() || TRACKED_NPCS.isEmpty()) return;
        long tick = event.getServer().overworld().getGameTime();
        if (tick < nextCheckTick) return;
        nextCheckTick = tick + CHECK_INTERVAL_TICKS;
        for (TrackedNpc tracked : new ArrayList<>(TRACKED_NPCS.values())) {
            checkTrackedNpc(tracked, "loaded_age_check", tick);
        }
    }

    private static boolean shouldRun() {
        return MobFarmConfig.PIXELMON_ENTITY_TRACKING_LOG.get() || MobFarmConfig.PIXELMON_NPC_REMOVAL_ENABLED.get();
    }

    private static void trackLoadedEntity(Entity entity, String source) {
        if (!isTrackedPixelmonEntity(entity) || entity.isRemoved()) return;
        long now = entity.level().getGameTime();
        TrackedNpc tracked = TRACKED_NPCS.computeIfAbsent(entity.getUUID(), ignored -> new TrackedNpc(entity, now));
        tracked.entity = entity;
        checkTrackedNpc(tracked, source, now);
    }

    private static void checkTrackedNpc(TrackedNpc tracked, String source, long now) {
        Entity entity = tracked.entity;
        if (entity == null || entity.isRemoved() || !isTrackedPixelmonEntity(entity)) {
            TRACKED_NPCS.remove(tracked.uuid());
            return;
        }
        long observedTicks = Math.max(0L, now - tracked.firstSeenGameTime);
        Map<String, String> probe = npcProbe(entity);
        ProtectionInfo protection = protectionInfo(probe);
        boolean removable = MobFarmConfig.PIXELMON_NPC_REMOVAL_ENABLED.get() && !protection.protectedNpc() && observedTicks >= REMOVAL_AGE_TICKS;

        if (removable) {
            logIfEnabled(entity, source, "removed", observedTicks, protection);
            entity.discard();
            TRACKED_NPCS.remove(tracked.uuid());
            return;
        }

        if (!MobFarmConfig.PIXELMON_ENTITY_TRACKING_LOG.get()) return;
        String action;
        if (tracked.lastLoggedObservedTicks < 0L) action = "identified";
        else {
            if (observedTicks - tracked.lastLoggedObservedTicks < UPDATE_LOG_INTERVAL_TICKS) return;
            action = "observed_age_update";
        }
        tracked.lastLoggedObservedTicks = observedTicks;
        writeLogs(entity, source, action, observedTicks, protection);
    }

    private static boolean isTrackedPixelmonEntity(Entity entity) {
        if (entity == null) return false;
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String typeText = typeId == null ? "" : typeId.toString().toLowerCase(Locale.ROOT);
        return "pixelmon:npc".equals(typeText);
    }

    private static String classification(ProtectionInfo protection) {
        if (protection.protectedNpc()) {
            if ("nurse".equals(protection.roleKey())) return "protected_nurse";
            if ("shopkeeper".equals(protection.roleKey())) return "protected_shopkeeper";
            if ("titled_npc".equals(protection.roleKey())) return "protected_titled_npc";
            return "protected_npc";
        }
        return "npc";
    }

    private static ProtectionInfo protectionInfo(Map<String, String> probe) {
        for (Map.Entry<String, String> entry : probe.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            String value = entry.getValue() == null ? "" : entry.getValue().toLowerCase(Locale.ROOT);
            String combined = key + " " + value;
            if (combined.contains("nurse") || combined.contains("healer") || combined.contains("doctor")) {
                return new ProtectionInfo(true, "nurse", "Nurse", "title contains nurse/healer/doctor", entry.getKey(), entry.getValue());
            }
            if (combined.contains("shopkeeper") || combined.contains("shop_keeper") || combined.contains("shop keeper") || combined.contains("merchant") || combined.contains("seller")) {
                return new ProtectionInfo(true, "shopkeeper", "Shopkeeper", "title contains shopkeeper/merchant/seller", entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : probe.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (titleProbe(key) && meaningfulTitleValue(value)) {
                return new ProtectionInfo(true, "titled_npc", prettyRole(value), "non-empty Pixelmon NPC title", entry.getKey(), entry.getValue());
            }
        }
        return ProtectionInfo.NONE;
    }

    private static boolean titleProbe(String key) {
        return key.contains("title");
    }

    private static boolean meaningfulTitleValue(String value) {
        if (value == null) return false;
        String normalized = value.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return false;
        if (normalized.equals("empty") || normalized.equals("null") || normalized.equals("none") || normalized.equals("unknown")) return false;
        if (normalized.equals("true") || normalized.equals("false")) return false;
        if (normalized.startsWith("error:")) return false;
        if (normalized.contains("minecraft:") || normalized.contains("pixelmon:npc") || normalized.contains("pixelmon:pixelmon")) return false;
        return normalized.contains("translation{key='pixelmon.npc.dialogue.") || !normalized.contains("translation{key=");
    }

    private static String prettyRole(String value) {
        String titleKey = translationKey(value);
        if (titleKey != null) {
            if (titleKey.contains("shopkeeper")) return "Shopkeeper";
            if (titleKey.contains("doctor") || titleKey.contains("nurse") || titleKey.contains("healer")) return "Nurse";
            String[] parts = titleKey.split("\\.");
            List<String> meaningfulParts = new ArrayList<>();
            for (String part : parts) {
                if (part.isBlank() || part.matches("\\d+") || part.equals("pixelmon") || part.equals("npc") || part.equals("dialogue") || part.equals("battle") || part.equals("plate")) continue;
                meaningfulParts.add(part);
            }
            if (!meaningfulParts.isEmpty()) return prettyWords(String.join(" ", meaningfulParts.subList(Math.max(0, meaningfulParts.size() - 2), meaningfulParts.size())));
        }
        String cleaned = value == null ? "" : value.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) return "Titled NPC";
        return prettyWords(cleaned);
    }

    private static String translationKey(String value) {
        if (value == null) return null;
        Matcher matcher = TRANSLATION_KEY.matcher(value);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static String prettyWords(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.split("\\s+")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.isEmpty() ? "Titled NPC" : out.toString();
    }

    private static void logIfEnabled(Entity entity, String source, String action, long observedTicks, ProtectionInfo protection) {
        if (!MobFarmConfig.PIXELMON_ENTITY_TRACKING_LOG.get()) return;
        writeLogs(entity, source, action, observedTicks, protection);
    }

    private static void writeLogs(Entity entity, String source, String action, long observedTicks, ProtectionInfo protection) {
        try {
            Files.createDirectories(LOG_FILE.toAbsolutePath().getParent());
            String line = jsonLine(entity, source, action, observedTicks, protection);
            Files.writeString(LOG_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            if (protection.protectedNpc() && PROTECTED_LOGGED.add(entity.getUUID())) writeProtectedLog(entity, protection, observedTicks);
        } catch (IOException error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write Pixelmon NPC tracker log", error);
        }
    }

    private static void writeProtectedLog(Entity entity, ProtectionInfo protection, long observedTicks) {
        try {
            Files.createDirectories(PROTECTED_LOG_FILE.toAbsolutePath().getParent());
            Files.writeString(PROTECTED_LOG_FILE, jsonLine(entity, "protected", "identified", observedTicks, protection) + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write protected Pixelmon NPC log", error);
        }
    }

    private static String jsonLine(Entity entity, String source, String action, long observedTicks, ProtectionInfo protection) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        StringBuilder out = new StringBuilder("{");
        prop(out, "time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), true);
        prop(out, "source", source, true);
        prop(out, "action", action, true);
        prop(out, "classification", classification(protection), true);
        prop(out, "protected", protection.protectedNpc(), true);
        prop(out, "name", entity.getDisplayName().getString(), true);
        prop(out, "role", protection.role(), true);
        prop(out, "whyProtected", protection.why(), true);
        prop(out, "uuid", entity.getUUID().toString(), true);
        prop(out, "entityType", typeId == null ? "unknown" : typeId.toString(), true);
        prop(out, "dimension", entity.level().dimension().location().toString(), true);
        prop(out, "x", round(entity.getX()), true);
        prop(out, "y", round(entity.getY()), true);
        prop(out, "z", round(entity.getZ()), true);
        prop(out, "observedAgeSeconds", round(observedTicks / 20.0D), false);
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
                || lower.contains("doctor")
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
                || lower.contains("doctor")
                || lower.contains("trainer")
                || lower.contains("tutor")
                || lower.contains("professor")
                || lower.contains("gym leader")
                || lower.contains("npc")
                || lower.contains("healer");
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

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static void prop(StringBuilder out, String key, String value, boolean comma) {
        out.append(quote(key)).append(':').append(value == null ? "null" : quote(value));
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

    private static final class TrackedNpc {
        private final UUID uuid;
        private final long firstSeenGameTime;
        private Entity entity;
        private long lastLoggedObservedTicks = -1L;

        private TrackedNpc(Entity entity, long firstSeenGameTime) {
            this.uuid = entity.getUUID();
            this.entity = entity;
            this.firstSeenGameTime = firstSeenGameTime;
        }

        private UUID uuid() {
            return uuid;
        }
    }

    private record ProtectionInfo(boolean protectedNpc, String roleKey, String role, String why, String evidencePath, String evidenceValue) {
        private static final ProtectionInfo NONE = new ProtectionInfo(false, "", null, null, null, null);
    }
}

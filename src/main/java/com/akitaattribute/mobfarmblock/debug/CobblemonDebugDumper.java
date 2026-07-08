package com.akitaattribute.mobfarmblock.debug;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.Map;

import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class CobblemonDebugDumper {
    private static final int MAX_DEPTH = 6;
    private static final int MAX_MEMBERS = 100;

    public static void writeEntityDump(Player player, Entity entity, StoredMob stored, String action) {
        if (player == null || stored == null || !MobFarmConfig.DEBUG_CHAT_MESSAGES.get() || !MobFarmConfig.DEBUG_COBBLEMON_JSON_DUMP.get()) return;
        if (!"cobblemon:pokemon".equals(stored.mobId.toString()) && (entity == null || !"cobblemon:pokemon".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()))) return;
        try {
            Path dir = Path.of("config", "mob_farm_block", "debug", "cobblemon").toAbsolutePath();
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
            String entityType = (entity == null ? stored.mobId : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())).toString().replace(':', '_');
            Path file = dir.resolve(stamp + "_" + action + "_" + entityType + "_" + player.getUUID() + ".json");
            Files.writeString(file, jsonRoot(player, entity, stored, action), StandardCharsets.UTF_8);
            player.displayClientMessage(Component.literal("Mob Farm Block Debug:\nCobblemon debug dump written:\n" + file), false);
        } catch (Exception error) {
            player.displayClientMessage(Component.literal("Mob Farm Block Debug:\nFailed to write Cobblemon debug dump:\n" + error.getMessage()), false);
        }
    }

    private static String jsonRoot(Player player, Entity entity, StoredMob stored, String action) throws IOException {
        StringBuilder out = new StringBuilder("{\n");
        prop(out, "action", action, true); prop(out, "gameTime", player.level().getGameTime(), true);
        prop(out, "playerName", player.getGameProfile().getName(), true); prop(out, "playerUuid", player.getUUID().toString(), true);
        prop(out, "entityType", entity == null ? stored.mobId.toString() : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), true); prop(out, "entityClass", entity == null ? "unavailable" : entity.getClass().getName(), true);
        prop(out, "detectedKind", String.valueOf(stored.kind), true); prop(out, "storedMobId", stored.mobId.toString(), true);
        prop(out, "storedSpeciesId", stored.speciesId == null ? null : stored.speciesId.toString(), true); prop(out, "storedDisplay", stored.display.toString(), true);
        prop(out, "storedDropProfileSource", stored.dropProfileSource, true); prop(out, "storedDropRuleCount", stored.dropProfile.drops().size(), true);
        out.append("  \"knownCobblemonPaths\": ").append(entity == null ? "null" : knownPaths(entity)).append(",\n");
        out.append("  \"entityBreakdown\": ").append(entity == null ? "null" : breakdown(entity, 0, new IdentityHashMap<>())).append('\n').append('}').append('\n');
        return out.toString();
    }

    private static String breakdown(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) return "null";
        if (simple(value)) return quote(String.valueOf(value));
        if (depth >= MAX_DEPTH) return quote("max-depth:" + value.getClass().getName());
        if (seen.put(value, Boolean.TRUE) != null) return quote("cycle:" + value.getClass().getName());
        StringBuilder out = new StringBuilder("{\"class\":").append(quote(value.getClass().getName())).append(",\"toString\":").append(quote(String.valueOf(value)));
        out.append(",\"methods\":{");
        int n = 0;
        for (Method m : value.getClass().getMethods()) {
            if (n >= MAX_MEMBERS) break;
            if (m.getParameterCount() != 0 || m.getReturnType() == Void.TYPE) continue;
            try { Object v = m.invoke(value); if (isInteresting(m.getName(), v)) { if (n++ > 0) out.append(','); out.append(quote(m.getName())).append(':').append(breakdown(v, depth + 1, seen)); } }
            catch (Throwable error) { if (isInterestingName(m.getName())) { if (n++ > 0) out.append(','); out.append(quote(m.getName())).append(':').append(quote("ERROR: " + error)); } }
        }
        out.append("},\"fields\":{"); n = 0;
        for (Field f : value.getClass().getDeclaredFields()) {
            if (n >= MAX_MEMBERS) break; if (!isInterestingName(f.getName())) continue;
            try { f.setAccessible(true); if (n++ > 0) out.append(','); out.append(quote(f.getName())).append(':').append(breakdown(f.get(value), depth + 1, seen)); }
            catch (Throwable error) { if (n++ > 0) out.append(','); out.append(quote(f.getName())).append(':').append(quote("ERROR: " + error)); }
        }
        return out.append("}}").toString();
    }

    private static String knownPaths(Entity entity) {
        StringBuilder out = new StringBuilder("{");
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        Object pokemon = readPath(out, "entity.pokemon", entity, "getPokemon", "pokemon");
        if (pokemon != null) {
            Object species = readPath(out, "pokemon.getSpecies", pokemon, "getSpecies", "species");
            Object form = readPath(out, "pokemon.getForm", pokemon, "getForm", "form");
            readPath(out, "pokemon.getAspects", pokemon, "getAspects", "aspects");
            Object renderable = readPath(out, "pokemon.asRenderablePokemon", pokemon, "asRenderablePokemon");
            if (species != null) { readPath(out, "species.getResourceIdentifier", species, "getResourceIdentifier", "resourceIdentifier"); readPath(out, "species.drops", species, "getDrops", "drops"); }
            if (form != null) { readPath(out, "form.drops", form, "getDrops", "drops", "_drops"); readPath(out, "form.getBaseScale", form, "getBaseScale", "baseScale"); readPath(out, "form.showdownId", form, "showdownId", "getShowdownId", "formOnlyShowdownId"); }
            if (renderable != null) { readPath(out, "renderablePokemon.getSpecies", renderable, "getSpecies", "species"); readPath(out, "renderablePokemon.getAspects", renderable, "getAspects", "aspects"); }
        }
        return out.append("}").toString();
    }

    private static Object readPath(StringBuilder out, String label, Object target, String... names) {
        for (String name : names) {
            try {
                Object value = invokeNoArg(target, name);
                appendJsonMember(out, label, value);
                return value;
            } catch (Throwable ignored) {
                try {
                    Field field = target.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(target);
                    appendJsonMember(out, label, value);
                    return value;
                } catch (Throwable error) {
                    appendJsonMember(out, label + "." + name + ".error", error.toString());
                }
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void appendJsonMember(StringBuilder out, String name, Object value) {
        if (out.length() > 1) out.append(',');
        out.append(quote(name)).append(':').append(breakdown(value, 0, new IdentityHashMap<>()));
    }

    private static boolean isInteresting(String name, Object value) { return simple(value) || isInterestingName(name); }
    private static boolean isInterestingName(String name) {
        String n = name.toLowerCase();
        return n.contains("pokemon") || n.contains("species") || n.contains("form") || n.contains("aspect") || n.contains("variant") || n.contains("id") || n.contains("texture")
                || n.contains("drop") || n.contains("loot") || n.contains("table") || n.contains("entry") || n.contains("entries") || n.contains("item") || n.contains("percentage")
                || n.contains("chance") || n.contains("quantity") || n.contains("range") || n.contains("selectable") || n.contains("weight") || n.contains("reward")
                || n.contains("render") || n.contains("renderable") || n.contains("model") || n.contains("scale") || n.contains("showdown");
    }
    private static boolean simple(Object value) { return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof ResourceLocation || value instanceof java.util.UUID; }
    private static void prop(StringBuilder out, String name, Object value, boolean comma) { out.append("  ").append(quote(name)).append(": ").append(value instanceof Number ? value : quote(value == null ? "null" : String.valueOf(value))).append(comma ? ",\n" : "\n"); }
    private static String quote(String text) { return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""; }
    private CobblemonDebugDumper() {}
}

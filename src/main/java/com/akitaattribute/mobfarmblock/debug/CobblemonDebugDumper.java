package com.akitaattribute.mobfarmblock.debug;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    private static final int FULL_MAX_DEPTH = 10;
    private static final int FULL_MAX_MEMBERS = 350;
    private static final int MAX_COLLECTION_VALUES = 120;

    public static void writeProcessingDump(Player player, StoredMob stored, String rollDetails) {
        writeDump(player, null, stored, "process", rollDetails);
    }

    public static void writeEntityDump(Player player, Entity entity, StoredMob stored, String action) {
        writeDump(player, entity, stored, action, null);
    }

    private static void writeDump(Player player, Entity entity, StoredMob stored, String action, String rollDetails) {
        if (player == null || stored == null || !MobFarmConfig.DEBUG_CHAT_MESSAGES.get() || !MobFarmConfig.DEBUG_COBBLEMON_JSON_DUMP.get()) return;
        ResourceLocation entityType = entity == null ? stored.mobId : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!isPokemonDebugType(stored.mobId) && !isPokemonDebugType(entityType)) return;
        try {
            Path dir = Path.of("config", "mob_farm_block", "debug", entityType.getNamespace()).toAbsolutePath();
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
            String entityTypeText = entityType.toString().replace(':', '_');
            Path file = dir.resolve(stamp + "_" + action + "_" + entityTypeText + "_" + player.getUUID() + ".json");
            Files.writeString(file, jsonRoot(player, entity, stored, action, rollDetails), StandardCharsets.UTF_8);
            player.displayClientMessage(Component.literal("Mob Farm Block Debug:\nPokemon debug dump written:\n" + file), false);
        } catch (Exception error) {
            player.displayClientMessage(Component.literal("Mob Farm Block Debug:\nFailed to write Pokemon debug dump:\n" + error.getMessage()), false);
        }
    }

    private static boolean isPokemonDebugType(ResourceLocation id) {
        if (id == null) return false;
        String text = id.toString();
        return "cobblemon:pokemon".equals(text) || "pixelmon:pixelmon".equals(text);
    }

    private static String jsonRoot(Player player, Entity entity, StoredMob stored, String action, String rollDetails) throws IOException {
        StringBuilder out = new StringBuilder("{\n");
        prop(out, "action", action, true); prop(out, "gameTime", player.level().getGameTime(), true);
        prop(out, "playerName", player.getGameProfile().getName(), true); prop(out, "playerUuid", player.getUUID().toString(), true);
        prop(out, "entityType", entity == null ? stored.mobId.toString() : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), true); prop(out, "entityClass", entity == null ? "unavailable" : entity.getClass().getName(), true);
        prop(out, "detectedKind", String.valueOf(stored.kind), true); prop(out, "storedMobId", stored.mobId.toString(), true);
        prop(out, "storedSpeciesId", stored.speciesId == null ? null : stored.speciesId.toString(), true); prop(out, "storedDisplay", stored.display.toString(), true);
        prop(out, "storedDropProfileSource", stored.dropProfileSource, true); prop(out, "storedDropRuleCount", stored.dropProfile.drops().size(), true);
        prop(out, "storedPixelmonRenderPayloadFormat", stored.pixelmonRenderSnapshot == null ? null : stored.pixelmonRenderSnapshot.payloadFormat(), true);
        prop(out, "storedPixelmonRenderPayloadLength", stored.pixelmonRenderSnapshot == null || stored.pixelmonRenderSnapshot.payload() == null ? 0 : stored.pixelmonRenderSnapshot.payload().length(), true);
        out.append("  \"resolvedDropRules\": ").append(dropRulesJson(stored)).append(",\n");
        out.append("  \"processingRollDetails\": ").append(rollDetails == null ? "null" : quote(rollDetails)).append(",\n");
        out.append("  \"knownPokemonPaths\": ").append(entity == null ? "null" : knownPaths(entity)).append(",\n");
        out.append("  \"entityBreakdown\": ").append(entity == null ? "null" : breakdown(entity, 0, new IdentityHashMap<>())).append(",\n");
        out.append("  \"fullReflectionDump\": ").append(entity == null ? "null" : fullDumpRoot(entity)).append('\n').append('}').append('\n');
        return out.toString();
    }

    private static String dropRulesJson(StoredMob stored) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < stored.dropProfile.drops().size(); i++) {
            var rule = stored.dropProfile.drops().get(i);
            if (i > 0) out.append(',');
            out.append('{')
                    .append(quote("index")).append(':').append(i).append(',')
                    .append(quote("itemId")).append(':').append(quote(rule.itemId().toString())).append(',')
                    .append(quote("chance")).append(':').append(rule.chance()).append(',')
                    .append(quote("chancePercent")).append(':').append(quote(chancePercent(rule.chance()))).append(',')
                    .append(quote("minCount")).append(':').append(rule.minCount()).append(',')
                    .append(quote("maxCount")).append(':').append(rule.maxCount()).append(',')
                    .append(quote("affectedByLooting")).append(':').append(rule.affectedByLooting()).append(',')
                    .append(quote("lootingChanceBonus")).append(':').append(rule.lootingChanceBonus()).append(',')
                    .append(quote("lootingMaxBonus")).append(':').append(rule.lootingMaxBonus())
                    .append('}');
        }
        return out.append(']').toString();
    }

    private static String chancePercent(double chance) {
        double percent = Math.max(0.0D, Math.min(1.0D, chance)) * 100.0D;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001D) return Long.toString(Math.round(percent)) + "%";
        return String.format(java.util.Locale.ROOT, "%.2f", percent).replaceAll("0+$", "").replaceAll("\\.$", "") + "%";
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
            if (m.getParameterCount() != 0 || m.getReturnType() == Void.TYPE || !isSafeAccessorName(m.getName())) continue;
            try { Object v = m.invoke(value); if (isInteresting(m.getName(), v)) { if (n++ > 0) out.append(','); out.append(quote(m.getName())).append(':').append(breakdown(v, depth + 1, seen)); } }
            catch (Throwable error) { if (isInterestingName(m.getName())) { if (n++ > 0) out.append(','); out.append(quote(m.getName())).append(':').append(quote("ERROR: " + error)); } }
        }
        out.append("},\"fields\":{"); n = 0;
        for (Field f : allFields(value.getClass())) {
            if (n >= MAX_MEMBERS) break; if (!isInterestingName(f.getName())) continue;
            try { f.setAccessible(true); if (n++ > 0) out.append(','); out.append(quote(fieldLabel(f))).append(':').append(breakdown(f.get(value), depth + 1, seen)); }
            catch (Throwable error) { if (n++ > 0) out.append(','); out.append(quote(fieldLabel(f))).append(':').append(quote("ERROR: " + error)); }
        }
        seen.remove(value);
        return out.append("}}").toString();
    }

    private static String fullDumpRoot(Entity entity) {
        StringBuilder out = new StringBuilder("{");
        appendFullMember(out, "entity", entity);
        Object pokemon = firstPath(entity, "getPokemon", "pokemon");
        appendFullMember(out, "entity.pokemon", pokemon);
        if (pokemon != null) {
            Object species = firstPath(pokemon, "getSpecies", "species", "getSpeciesValue", "speciesValue");
            Object form = firstPath(pokemon, "getForm", "form");
            Object palette = firstPath(pokemon, "getPalette", "palette");
            Object renderable = firstPath(pokemon, "asRenderablePokemon");
            appendFullMember(out, "pokemon.species", species);
            appendFullMember(out, "pokemon.form", form);
            appendFullMember(out, "pokemon.palette", palette);
            appendFullMember(out, "pokemon.renderable", renderable);
        }
        return out.append('}').toString();
    }

    private static void appendFullMember(StringBuilder out, String name, Object value) {
        if (out.length() > 1) out.append(',');
        out.append(quote(name)).append(':').append(fullBreakdown(value, 0, new IdentityHashMap<>()));
    }

    private static String fullBreakdown(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) return "null";
        if (simple(value)) return quote(String.valueOf(value));
        if (value instanceof CharSequence text) return quote(text.toString());
        if (depth >= FULL_MAX_DEPTH) return quote("max-depth:" + value.getClass().getName());
        if (seen.put(value, Boolean.TRUE) != null) return quote("cycle:" + value.getClass().getName());
        StringBuilder out = new StringBuilder("{\"class\":").append(quote(value.getClass().getName()))
                .append(",\"toString\":").append(quote(String.valueOf(value)))
                .append(",\"methodSignatures\":").append(methodSignaturesJson(value.getClass()))
                .append(",\"safeNoArgMethodValues\":{");
        int n = 0;
        for (Method method : allMethods(value.getClass())) {
            if (n >= FULL_MAX_MEMBERS) break;
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE || !isSafeAccessorName(method.getName())) continue;
            try {
                method.setAccessible(true);
                Object result = method.invoke(value);
                if (n++ > 0) out.append(',');
                out.append(quote(methodLabel(method))).append(':').append(fullBreakdownValue(result, depth + 1, seen));
            } catch (Throwable error) {
                if (n++ > 0) out.append(',');
                out.append(quote(methodLabel(method))).append(':').append(quote("ERROR: " + error));
            }
        }
        out.append("},\"fields\":{");
        n = 0;
        for (Field field : allFields(value.getClass())) {
            if (n >= FULL_MAX_MEMBERS) break;
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                if (n++ > 0) out.append(',');
                out.append(quote(fieldLabel(field))).append(':').append(fullBreakdownValue(field.get(value), depth + 1, seen));
            } catch (Throwable error) {
                if (n++ > 0) out.append(',');
                out.append(quote(fieldLabel(field))).append(':').append(quote("ERROR: " + error));
            }
        }
        seen.remove(value);
        return out.append("}}").toString();
    }

    private static String fullBreakdownValue(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null || simple(value)) return value == null ? "null" : quote(String.valueOf(value));
        if (value instanceof Iterable<?> iterable) {
            StringBuilder out = new StringBuilder("[");
            int n = 0;
            for (Object item : iterable) {
                if (n >= MAX_COLLECTION_VALUES) { if (n > 0) out.append(','); out.append(quote("truncated")); break; }
                if (n++ > 0) out.append(',');
                out.append(fullBreakdown(item, depth, seen));
            }
            return out.append(']').toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            int n = 0;
            for (var entry : map.entrySet()) {
                if (n >= MAX_COLLECTION_VALUES) { if (n > 0) out.append(','); out.append(quote("truncated")).append(':').append(quote("true")); break; }
                if (n++ > 0) out.append(',');
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(fullBreakdown(entry.getValue(), depth, seen));
            }
            return out.append('}').toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder out = new StringBuilder("[");
            int length = Math.min(java.lang.reflect.Array.getLength(value), MAX_COLLECTION_VALUES);
            for (int i = 0; i < length; i++) {
                if (i > 0) out.append(',');
                out.append(fullBreakdown(java.lang.reflect.Array.get(value, i), depth, seen));
            }
            if (java.lang.reflect.Array.getLength(value) > MAX_COLLECTION_VALUES) out.append(',').append(quote("truncated"));
            return out.append(']').toString();
        }
        return fullBreakdown(value, depth, seen);
    }

    private static String methodSignaturesJson(Class<?> type) {
        StringBuilder out = new StringBuilder("[");
        int n = 0;
        for (Method method : allMethods(type)) {
            if (n >= FULL_MAX_MEMBERS) { if (n > 0) out.append(','); out.append(quote("truncated")); break; }
            if (n++ > 0) out.append(',');
            out.append(quote(methodLabel(method)));
        }
        return out.append(']').toString();
    }

    private static java.util.List<Method> allMethods(Class<?> type) {
        java.util.LinkedHashMap<String, Method> methods = new java.util.LinkedHashMap<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) methods.putIfAbsent(methodLabel(m), m);
        }
        for (Method m : type.getMethods()) methods.putIfAbsent(methodLabel(m), m);
        return new java.util.ArrayList<>(methods.values());
    }

    private static java.util.List<Field> allFields(Class<?> type) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) fields.add(f);
        return fields;
    }

    private static String methodLabel(Method method) {
        StringBuilder out = new StringBuilder(method.getDeclaringClass().getName()).append('#').append(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) { if (i > 0) out.append(','); out.append(params[i].getName()); }
        return out.append("):").append(method.getReturnType().getName()).toString();
    }

    private static String fieldLabel(Field field) {
        return field.getDeclaringClass().getName() + "#" + field.getName() + ":" + field.getType().getName();
    }

    private static String knownPaths(Entity entity) {
        StringBuilder out = new StringBuilder("{");
        Object pokemon = readPath(out, "entity.pokemon", entity, "getPokemon", "pokemon");
        if (pokemon != null) {
            Object species = readPath(out, "pokemon.getSpecies", pokemon, "getSpecies", "species", "getSpeciesValue", "speciesValue");
            Object form = readPath(out, "pokemon.getForm", pokemon, "getForm", "form");
            readPath(out, "pokemon.getAspects", pokemon, "getAspects", "aspects");
            readPath(out, "pokemon.getPalette", pokemon, "getPalette", "palette");
            readPath(out, "pokemon.isShiny", pokemon, "isShiny", "getShiny", "shiny");
            readPath(out, "pokemon.getGender", pokemon, "getGender", "gender");
            Object renderable = readPath(out, "pokemon.asRenderablePokemon", pokemon, "asRenderablePokemon");
            if (species != null) { readPath(out, "species.getRegistryValue", species, "getRegistryValue", "getRegistryName", "getResourceLocation", "getResourceIdentifier", "resourceIdentifier", "getName", "name"); readPath(out, "species.drops", species, "getDrops", "drops"); }
            if (form != null) { readPath(out, "form.drops", form, "getDrops", "drops", "_drops"); readPath(out, "form.getBaseScale", form, "getBaseScale", "baseScale"); readPath(out, "form.showdownId", form, "showdownId", "getShowdownId", "formOnlyShowdownId"); }
            if (renderable != null) { readPath(out, "renderablePokemon.getSpecies", renderable, "getSpecies", "species"); readPath(out, "renderablePokemon.getAspects", renderable, "getAspects", "aspects"); }
        }
        return out.append("}").toString();
    }

    private static Object firstPath(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try { return invokeNoArg(target, name); }
            catch (Throwable ignored) {
                try {
                    Field field = target.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Throwable ignored2) {}
            }
        }
        return null;
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
        if (!isSafeAccessorName(name)) throw new IllegalArgumentException("Refusing to invoke non-accessor method " + name);
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void appendJsonMember(StringBuilder out, String name, Object value) {
        if (out.length() > 1) out.append(',');
        out.append(quote(name)).append(':').append(breakdown(value, 0, new IdentityHashMap<>()));
    }

    private static boolean isSafeAccessorName(String name) {
        String n = name.toLowerCase();
        if (n.startsWith("remove") || n.equals("clear") || n.startsWith("add") || n.startsWith("set") || n.startsWith("put")
                || n.startsWith("poll") || n.startsWith("pop") || n.startsWith("push") || n.startsWith("delete") || n.startsWith("destroy")
                || n.startsWith("discard") || n.startsWith("shrink") || n.startsWith("grow") || n.startsWith("tick") || n.startsWith("update")
                || n.startsWith("refresh") || n.startsWith("init") || n.startsWith("load") || n.startsWith("save")) return false;
        return name.startsWith("get") || name.startsWith("is") || name.startsWith("has") || name.startsWith("can") || name.startsWith("should")
                || name.equals("toString") || name.equals("hashCode") || name.equals("asRenderablePokemon") || name.equals("pokemon") || name.equals("species") || name.equals("form") || name.equals("aspects") || name.equals("palette") || name.equals("shiny") || name.equals("gender");
    }

    private static boolean isInteresting(String name, Object value) { return simple(value) || isInterestingName(name); }
    private static boolean isInterestingName(String name) {
        String n = name.toLowerCase();
        return n.contains("pokemon") || n.contains("species") || n.contains("form") || n.contains("aspect") || n.contains("variant") || n.contains("id") || n.contains("texture")
                || n.contains("drop") || n.contains("loot") || n.contains("table") || n.contains("entry") || n.contains("entries") || n.contains("item") || n.contains("percentage")
                || n.contains("chance") || n.contains("quantity") || n.contains("range") || n.contains("selectable") || n.contains("weight") || n.contains("reward")
                || n.contains("render") || n.contains("renderable") || n.contains("model") || n.contains("scale") || n.contains("showdown") || n.contains("palette") || n.contains("shiny") || n.contains("gender") || n.contains("growth");
    }
    private static boolean simple(Object value) { return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof ResourceLocation || value instanceof java.util.UUID; }
    private static void prop(StringBuilder out, String name, Object value, boolean comma) { out.append("  ").append(quote(name)).append(": ").append(value instanceof Number ? value : quote(value == null ? "null" : String.valueOf(value))).append(comma ? ",\n" : "\n"); }
    private static String quote(String text) { return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""; }
    private CobblemonDebugDumper() {}
}

package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.mob.DropRule;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Non-destructive Pixelmon drop introspection.
 *
 * This intentionally does not call action methods such as dropItems or
 * dropNormalItems. It only calls no-arg accessor-like methods and records what
 * was found so process dumps can point at the next target when a static preview
 * cannot be resolved.
 */
public final class PixelmonNativeDropInspector {
    private static final int MAX_DEPTH = 4;
    private static final int MAX_NODES = 96;
    private static final int MAX_DIAGNOSTIC_CHARS = 12000;

    public record Result(List<DropRule> rules, String diagnostics) {}

    public static Result inspect(Entity entity) {
        Inspector inspector = new Inspector();
        inspector.inspectEntity(entity);
        return new Result(List.copyOf(inspector.dedupedRules()), inspector.diagnostics());
    }

    private static final class Inspector {
        private final StringBuilder diagnostics = new StringBuilder();
        private final List<DropRule> rules = new ArrayList<>();
        private int nodes;

        void inspectEntity(Entity entity) {
            if (entity == null) {
                line("entity=null");
                return;
            }
            line("entity=" + entity.getClass().getName());
            inspectAnchor("entity", entity);
            Object pokemon = firstValue(entity, "getPokemon", "pokemon", "getPixelmon", "pokemonData").orElse(null);
            inspectAnchor("entity.pokemon", pokemon);
            if (pokemon != null) {
                Object species = firstValue(pokemon, "getSpecies", "species", "getSpeciesValue", "speciesValue").orElse(null);
                Object form = firstValue(pokemon, "getForm", "form").orElse(null);
                Object palette = firstValue(pokemon, "getPalette", "palette").orElse(null);
                Object renderable = firstValue(pokemon, "asRenderablePokemon").orElse(null);
                inspectAnchor("pokemon.species", species);
                inspectAnchor("pokemon.form", form);
                inspectAnchor("pokemon.palette", palette);
                inspectAnchor("pokemon.renderable", renderable);
            }
        }

        private void inspectAnchor(String label, Object value) {
            if (value == null) {
                line(label + "=null");
                return;
            }
            line(label + " class=" + value.getClass().getName() + " value=" + safeText(value));
            scanMembers(label, value);
            collectRules(label, value, 0, new IdentityHashMap<>());
        }

        private void scanMembers(String label, Object value) {
            int interestingMethods = 0;
            for (Method method : allMethods(value.getClass())) {
                String name = method.getName();
                if (!dropRelated(name)) continue;
                if (++interestingMethods <= 40) line(label + ".method " + methodLabel(method));
                if (method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE && safeAccessor(name)) {
                    try {
                        method.setAccessible(true);
                        Object result = method.invoke(value);
                        line(label + "." + name + " -> " + summarize(result));
                        collectRules(label + "." + name, result, 0, new IdentityHashMap<>());
                    } catch (Throwable error) {
                        line(label + "." + name + " ERROR " + error.getClass().getSimpleName() + ": " + safeMessage(error));
                    }
                }
            }
            if (interestingMethods > 40) line(label + ".method truncated count=" + interestingMethods);

            int interestingFields = 0;
            for (Field field : allFields(value.getClass())) {
                if (!dropRelated(field.getName())) continue;
                if (++interestingFields <= 40) line(label + ".field " + fieldLabel(field));
                try {
                    field.setAccessible(true);
                    Object result = field.get(value);
                    line(label + "." + field.getName() + " -> " + summarize(result));
                    collectRules(label + "." + field.getName(), result, 0, new IdentityHashMap<>());
                } catch (Throwable error) {
                    line(label + "." + field.getName() + " ERROR " + error.getClass().getSimpleName() + ": " + safeMessage(error));
                }
            }
            if (interestingFields > 40) line(label + ".field truncated count=" + interestingFields);
        }

        private void collectRules(String label, Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
            if (value == null || depth > MAX_DEPTH || nodes++ > MAX_NODES) return;
            if (!simple(value) && seen.put(value, Boolean.TRUE) != null) return;
            dropRuleFromEntry(value).ifPresent(rule -> {
                rules.add(rule);
                line(label + " RULE item=" + rule.itemId() + " chance=" + rule.chance() + " count=" + rule.minCount() + "-" + rule.maxCount());
            });
            if (simple(value)) return;

            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    collectRules(label + ".mapKey", entry.getKey(), depth + 1, seen);
                    collectRules(label + ".mapValue", entry.getValue(), depth + 1, seen);
                }
                return;
            }
            if (value instanceof Iterable<?> iterable) {
                int i = 0;
                for (Object item : iterable) {
                    if (i >= 32) break;
                    collectRules(label + "[" + i++ + "]", item, depth + 1, seen);
                }
                return;
            }
            if (value.getClass().isArray()) {
                int length = Math.min(32, Array.getLength(value));
                for (int i = 0; i < length; i++) collectRules(label + "[" + i + "]", Array.get(value, i), depth + 1, seen);
                return;
            }

            for (String name : new String[] {"getEntries", "entries", "getDrops", "drops", "getDropItems", "dropItems", "getRewards", "rewards", "getItems", "items", "getItem", "item", "getStack", "stack", "getItemStack", "itemStack", "getLootTable", "lootTable", "getDropTable", "dropTable"}) {
                Optional<Object> child = firstValue(value, name);
                child.ifPresent(object -> collectRules(label + "." + name, object, depth + 1, seen));
            }
        }

        private List<DropRule> dedupedRules() {
            Map<String, DropRule> deduped = new LinkedHashMap<>();
            for (DropRule rule : rules) deduped.put(rule.itemId() + "|" + rule.chance() + "|" + rule.minCount() + "|" + rule.maxCount(), rule);
            return new ArrayList<>(deduped.values());
        }

        private String diagnostics() {
            return diagnostics.toString();
        }

        private void line(String text) {
            if (diagnostics.length() >= MAX_DIAGNOSTIC_CHARS) return;
            if (!diagnostics.isEmpty()) diagnostics.append("\n");
            diagnostics.append(text);
        }
    }

    private static Optional<DropRule> dropRuleFromEntry(Object entry) {
        if (entry == null) return Optional.empty();
        Optional<ResourceLocation> item = rawItem(entry).flatMap(PixelmonNativeDropInspector::coerceItemId);
        if (item.isEmpty()) return Optional.empty();
        double chance = firstValue(entry, "getPercentage", "percentage", "getChance", "chance", "getProbability", "probability", "getDropChance", "dropChance")
                .flatMap(PixelmonNativeDropInspector::coerceDouble)
                .orElse(100.0D);
        if (chance > 1.0D) chance /= 100.0D;
        int min = firstValue(entry, "getMin", "min", "getMinCount", "minCount", "minimum", "getQuantity", "quantity", "getCount", "count", "getAmount", "amount")
                .flatMap(PixelmonNativeDropInspector::coerceInt)
                .orElse(1);
        int max = firstValue(entry, "getMax", "max", "getMaxCount", "maxCount", "maximum")
                .flatMap(PixelmonNativeDropInspector::coerceInt)
                .orElse(min);
        return Optional.of(new DropRule(item.get(), Math.max(0.0D, Math.min(1.0D, chance)), Math.max(0, min), Math.max(Math.max(0, min), max), false, 0.0D, 0));
    }

    private static Optional<Object> rawItem(Object value) {
        if (value instanceof ItemStack || value instanceof Item || value instanceof ResourceLocation) return Optional.of(value);
        if (value instanceof CharSequence text && String.valueOf(text).contains(":")) return Optional.of(value);
        return firstValue(value, "getItemStack", "itemStack", "getStack", "stack", "getItem", "item", "getItemId", "itemId", "getItemID", "itemID", "getIdentifier", "identifier", "getResourceLocation", "resourceLocation", "getRegistryName", "registryName");
    }

    private static Optional<ResourceLocation> coerceItemId(Object value) {
        if (value == null) return Optional.empty();
        ResourceLocation id = null;
        if (value instanceof ItemStack stack) {
            if (stack.isEmpty() || stack.is(Items.AIR)) return Optional.empty();
            id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        } else if (value instanceof Item item) {
            if (item == Items.AIR) return Optional.empty();
            id = BuiltInRegistries.ITEM.getKey(item);
        } else if (value instanceof ResourceLocation resourceLocation) {
            id = resourceLocation;
        } else {
            String text = String.valueOf(value).trim();
            if (!text.contains(":") || text.contains("@") && text.contains(".")) return Optional.empty();
            try { id = ResourceLocation.parse(text.replace(" ", "_")); }
            catch (Throwable ignored) { return Optional.empty(); }
        }
        if (id == null || id.equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) return Optional.empty();
        return BuiltInRegistries.ITEM.getOptional(id).isPresent() ? Optional.of(id) : Optional.empty();
    }

    private static Optional<Object> firstValue(Object target, String... names) {
        if (target == null) return Optional.empty();
        for (String name : names) {
            Optional<Object> method = invokeNoArg(target, name);
            if (method.isPresent()) return method;
            Optional<Object> field = readField(target, name);
            if (field.isPresent()) return field;
        }
        return Optional.empty();
    }

    private static Optional<Object> invokeNoArg(Object target, String name) {
        try {
            Method method = findNoArgMethod(target.getClass(), name);
            if (method == null) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> readField(Object target, String name) {
        try {
            Field field = findField(target.getClass(), name);
            if (field == null) return Optional.empty();
            field.setAccessible(true);
            return Optional.ofNullable(field.get(target));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == 0) return method;
        }
        for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterCount() == 0) return method;
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static List<Method> allMethods(Class<?> type) {
        Map<String, Method> methods = new LinkedHashMap<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Method method : c.getDeclaredMethods()) methods.putIfAbsent(methodLabel(method), method);
        for (Method method : type.getMethods()) methods.putIfAbsent(methodLabel(method), method);
        return new ArrayList<>(methods.values());
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Field field : c.getDeclaredFields()) fields.add(field);
        return fields;
    }

    private static boolean dropRelated(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("drop") || n.contains("loot") || n.contains("reward") || n.contains("table") || n.contains("entry") || n.contains("entries");
    }

    private static boolean safeAccessor(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.startsWith("drop") || n.startsWith("remove") || n.startsWith("clear") || n.startsWith("add") || n.startsWith("set") || n.startsWith("put") || n.startsWith("load") || n.startsWith("save") || n.startsWith("update") || n.startsWith("tick")) return false;
        return n.startsWith("get") || n.startsWith("is") || n.startsWith("has") || n.startsWith("can") || n.startsWith("should") || n.startsWith("as");
    }

    private static boolean simple(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof ResourceLocation || value instanceof java.util.UUID;
    }

    private static Optional<Integer> coerceInt(Object value) {
        if (value instanceof Number number) return Optional.of(number.intValue());
        try { return Optional.of(Integer.parseInt(String.valueOf(value).replaceAll("[^0-9-]", ""))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Double> coerceDouble(Object value) {
        if (value instanceof Number number) return Optional.of(number.doubleValue());
        try { return Optional.of(Double.parseDouble(String.valueOf(value).replace("%", ""))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static String summarize(Object value) {
        if (value == null) return "null";
        return value.getClass().getName() + " " + safeText(value);
    }

    private static String safeText(Object value) {
        String text;
        try { text = String.valueOf(value); }
        catch (Throwable error) { text = "toString ERROR " + error.getClass().getSimpleName(); }
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? "" : message;
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

    private PixelmonNativeDropInspector() {}
}

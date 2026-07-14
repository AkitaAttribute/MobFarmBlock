package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * This does not call action methods such as dropItems/dropNormalItems. It drills
 * into the rehydrated Pixelmon object graph and records the native drop classes,
 * class members, and item-like accessors so process dumps can point at the next
 * target when a static preview cannot be resolved.
 */
public final class PixelmonNativeDropInspector {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_NODES = 192;
    private static final int MAX_DIAGNOSTIC_CHARS = 32000;
    private static final int MAX_METHODS_PER_CLASS = 120;
    private static final int MAX_FIELDS_PER_CLASS = 120;

    public record Result(List<DropRule> rules, String diagnostics) {}

    public static Result inspect(Entity entity) {
        Inspector inspector = new Inspector();
        inspector.inspectEntity(entity);
        return new Result(List.copyOf(inspector.dedupedRules()), inspector.diagnostics());
    }

    private static final class Inspector {
        private final StringBuilder diagnostics = new StringBuilder();
        private final List<DropRule> rules = new ArrayList<>();
        private final java.util.Set<Class<?>> classInventoryDumped = new java.util.HashSet<>();
        private int nodes;

        void inspectEntity(Entity entity) {
            if (entity == null) {
                line("entity=null");
                return;
            }
            line("entity=" + entity.getClass().getName());
            inspectNativeDropClasses(entity);
            inspectAnchor("entity", entity);
            Object pokemon = firstValue(entity, "getPokemon", "pokemon", "getPixelmon", "pixelmon", "pokemonData").orElse(null);
            inspectAnchor("entity.pokemon", pokemon);
            if (pokemon != null) {
                Object species = firstValue(pokemon, "getSpecies", "species", "getSpeciesValue", "speciesValue").orElse(null);
                Object form = firstValue(pokemon, "getForm", "form").orElse(null);
                Object palette = firstValue(pokemon, "getPalette", "palette").orElse(null);
                Object renderable = firstValue(pokemon, "asRenderablePokemon").orElse(null);
                Object held = firstValue(pokemon, "getHeldItem", "heldItem", "getHeldItemAsItemHeld", "heldItemAsItemHeld").orElse(null);
                inspectAnchor("pokemon.species", species);
                inspectAnchor("pokemon.form", form);
                inspectAnchor("pokemon.palette", palette);
                inspectAnchor("pokemon.renderable", renderable);
                inspectAnchor("pokemon.held", held);
                inspectSpeciesJson("pokemon.speciesJson", species);
                inspectSpeciesJson("pokemon.formSpeciesJson", firstValue(form, "getParentSpecies", "parentSpecies", "parent").orElse(null));
            }
        }

        private void inspectNativeDropClasses(Entity entity) {
            line("nativeDropClassInventory begin");
            for (Method method : allMethods(entity.getClass())) {
                String name = method.getName();
                if (!(name.equals("dropNormalItems") || name.equals("dropItems") || name.equals("dropBossItems") || dropRelated(name))) continue;
                if (!name.toLowerCase(java.util.Locale.ROOT).contains("drop")) continue;
                line("nativeDrop.method " + methodLabel(method));
                dumpClassInventory("nativeDrop.declaringClass", method.getDeclaringClass(), entity);
            }
            line("nativeDropClassInventory end");
        }

        private void inspectAnchor(String label, Object value) {
            if (value == null) {
                line(label + "=null");
                return;
            }
            line(label + " class=" + value.getClass().getName() + " value=" + safeText(value));
            dumpClassInventory(label + ".classInventory", value.getClass(), value);
            scanMembers(label, value);
            collectRules(label, value, 0, new IdentityHashMap<>());
        }

        private void dumpClassInventory(String label, Class<?> type, Object instance) {
            if (type == null || !classInventoryDumped.add(type)) return;
            line(label + " class=" + type.getName());
            int methods = 0;
            for (Method method : allMethods(type)) {
                if (methods >= MAX_METHODS_PER_CLASS) { line(label + ".methods truncated"); break; }
                if (!isUsefulSignature(method)) continue;
                methods++;
                line(label + ".method " + methodLabel(method));
            }
            int fields = 0;
            for (Field field : allFields(type)) {
                if (fields >= MAX_FIELDS_PER_CLASS) { line(label + ".fields truncated"); break; }
                if (!interestingName(field.getName()) && !isPixelmonOwned(field.getDeclaringClass())) continue;
                fields++;
                String prefix = label + ".field " + fieldLabel(field);
                Object target = instance != null && field.getDeclaringClass().isAssignableFrom(instance.getClass()) ? instance : null;
                if (target == null || Modifier.isStatic(field.getModifiers())) {
                    line(prefix);
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    line(prefix + " -> " + summarize(value));
                    if (interestingName(field.getName())) collectRules(label + "." + field.getName(), value, 0, new IdentityHashMap<>());
                } catch (Throwable error) {
                    line(prefix + " ERROR " + error.getClass().getSimpleName() + ": " + safeMessage(error));
                }
            }
        }

        private void scanMembers(String label, Object value) {
            int interestingMethods = 0;
            for (Method method : allMethods(value.getClass())) {
                String name = method.getName();
                if (!interestingName(name)) continue;
                if (++interestingMethods <= 80) line(label + ".method " + methodLabel(method));
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
            if (interestingMethods > 80) line(label + ".method truncated count=" + interestingMethods);

            int interestingFields = 0;
            for (Field field : allFields(value.getClass())) {
                if (!interestingName(field.getName())) continue;
                if (++interestingFields <= 80) line(label + ".field " + fieldLabel(field));
                try {
                    field.setAccessible(true);
                    Object result = field.get(value);
                    line(label + "." + field.getName() + " -> " + summarize(result));
                    collectRules(label + "." + field.getName(), result, 0, new IdentityHashMap<>());
                } catch (Throwable error) {
                    line(label + "." + field.getName() + " ERROR " + error.getClass().getSimpleName() + ": " + safeMessage(error));
                }
            }
            if (interestingFields > 80) line(label + ".field truncated count=" + interestingFields);
        }

        private void inspectSpeciesJson(String label, Object species) {
            if (species == null) return;
            Optional<Object> json = firstValue(species, "getJson", "json");
            if (json.isEmpty()) return;
            String text = String.valueOf(json.get());
            line(label + " length=" + text.length() + " containsDrop=" + containsAny(text, "drop", "loot", "reward", "item_drop", "drops"));
            for (String needle : List.of("\"drops\"", "\"drop\"", "\"loot\"", "\"rewards\"", "\"baseExp\"", "\"models\"")) {
                int index = text.indexOf(needle);
                if (index >= 0) line(label + " snippet " + needle + " " + text.substring(Math.max(0, index - 120), Math.min(text.length(), index + 320)).replace('\n', ' '));
            }
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
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (i++ >= 48) break;
                    collectRules(label + ".mapKey", entry.getKey(), depth + 1, seen);
                    collectRules(label + ".mapValue", entry.getValue(), depth + 1, seen);
                }
                return;
            }
            if (value instanceof Iterable<?> iterable) {
                int i = 0;
                for (Object item : iterable) {
                    if (i >= 48) break;
                    collectRules(label + "[" + i++ + "]", item, depth + 1, seen);
                }
                return;
            }
            if (value.getClass().isArray()) {
                int length = Math.min(48, Array.getLength(value));
                for (int i = 0; i < length; i++) collectRules(label + "[" + i + "]", Array.get(value, i), depth + 1, seen);
                return;
            }

            for (String name : CHILD_ACCESSORS) {
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

    private static final String[] CHILD_ACCESSORS = new String[] {
            "getEntries", "entries", "getEntry", "entry", "getDrops", "drops", "getDrop", "drop", "getDropItems", "dropItems",
            "getRewards", "rewards", "getReward", "reward", "getItems", "items", "getItem", "item", "getHeldItem", "heldItem",
            "getHeldItemAsItemHeld", "heldItemAsItemHeld", "getStack", "stack", "getItemStack", "itemStack", "getLootTable", "lootTable",
            "getDropTable", "dropTable", "getTable", "table", "getPools", "pools", "getPool", "pool", "getRolls", "rolls",
            "getQuantity", "quantity", "getQuantityRange", "quantityRange", "getPercentage", "percentage", "getChance", "chance"
    };

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
        return firstValue(value, "getItemStack", "itemStack", "getStack", "stack", "getItem", "item", "getItemId", "itemId", "getItemID", "itemID", "getIdentifier", "identifier", "getResourceLocation", "resourceLocation", "getRegistryName", "registryName", "getKey", "key");
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
            if (method == null || !safeAccessor(name)) return Optional.empty();
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

    private static boolean isUsefulSignature(Method method) {
        String name = method.getName();
        return interestingName(name) || isPixelmonOwned(method.getDeclaringClass()) && method.getDeclaringClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("holdsitems");
    }

    private static boolean interestingName(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("drop") || n.contains("loot") || n.contains("reward") || n.contains("table") || n.contains("entry") || n.contains("entries")
                || n.contains("item") || n.contains("held") || n.contains("boss") || n.contains("battle") || n.contains("defeat") || n.contains("capture")
                || n.contains("screen") || n.contains("gui") || n.contains("container") || n.contains("select") || n.contains("claim") || n.contains("pokemon");
    }

    private static boolean dropRelated(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("drop") || n.contains("loot") || n.contains("reward") || n.contains("table") || n.contains("entry") || n.contains("entries");
    }

    private static boolean safeAccessor(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.startsWith("drop") || n.startsWith("remove") || n.startsWith("clear") || n.startsWith("add") || n.startsWith("set") || n.startsWith("put") || n.startsWith("load") || n.startsWith("save") || n.startsWith("update") || n.startsWith("tick") || n.startsWith("open") || n.startsWith("send") || n.startsWith("give") || n.startsWith("claim")) return false;
        return n.startsWith("get") || n.startsWith("is") || n.startsWith("has") || n.startsWith("can") || n.startsWith("should") || n.startsWith("as") || n.equals("pokemon") || n.equals("species") || n.equals("form") || n.equals("palette") || n.equals("drops") || n.equals("items") || n.equals("rewards") || n.equals("entries");
    }

    private static boolean isPixelmonOwned(Class<?> type) {
        return type != null && type.getName().startsWith("com.pixelmonmod.pixelmon");
    }

    private static boolean simple(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof ResourceLocation || value instanceof java.util.UUID;
    }

    private static Optional<Integer> coerceInt(Object value) {
        if (value instanceof Number number) return Optional.of(number.intValue());
        try { return Optional.of(Integer.parseInt(String.valueOf(value).replaceAll("[^0-9-]", ""))); }
        catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Double> coerceDouble(Object value) {
        if (value instanceof Number number) return Optional.of(number.doubleValue());
        try { return Optional.of(Double.parseDouble(String.valueOf(value).replace("%", ""))); }
        catch (Throwable ignored) { return Optional.empty(); }
    }

    private static boolean containsAny(String text, String... needles) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String needle : needles) if (lower.contains(needle.toLowerCase(java.util.Locale.ROOT))) return true;
        return false;
    }

    private static String summarize(Object value) {
        if (value == null) return "null";
        if (value instanceof Collection<?> collection) return value.getClass().getName() + " size=" + collection.size() + " value=" + safeText(collection);
        if (value instanceof Map<?, ?> map) return value.getClass().getName() + " size=" + map.size() + " value=" + safeText(map);
        if (value.getClass().isArray()) return value.getClass().getName() + " length=" + Array.getLength(value) + " value=" + safeText(value);
        return value.getClass().getName() + " " + safeText(value);
    }

    private static String methodLabel(Method method) {
        StringBuilder out = new StringBuilder(method.getDeclaringClass().getName()).append('#').append(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) out.append(',');
            out.append(params[i].getName());
        }
        return out.append("):").append(method.getReturnType().getName()).toString();
    }

    private static String fieldLabel(Field field) {
        return field.getDeclaringClass().getName() + "#" + field.getName() + ":" + field.getType().getName();
    }

    private static String safeText(Object value) {
        String text;
        try { text = String.valueOf(value); }
        catch (Throwable error) { text = "<toString failed " + error.getClass().getSimpleName() + ">"; }
        text = text.replace('\n', ' ').replace('\r', ' ');
        return text.length() > 360 ? text.substring(0, 360) + "..." : text;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.toString() : message;
    }

    private PixelmonNativeDropInspector() {}
}

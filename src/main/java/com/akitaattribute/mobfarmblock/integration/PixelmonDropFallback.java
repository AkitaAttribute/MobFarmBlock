package com.akitaattribute.mobfarmblock.integration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DropProfile;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.XpProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Extra Pixelmon drop discovery that probes the live entity and a cached data-resource index. */
public final class PixelmonDropFallback {
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();
    private static final Pattern BASE_EXP = Pattern.compile("\\\"baseExp\\\"\\s*:\\s*(\\d+)");
    private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*(?:-|\\.\\.)\\s*(\\d+)");
    private static final Map<Integer, DropIndex> DROP_INDEX_CACHE = new ConcurrentHashMap<>();

    public static Optional<DropProfile> resolve(Entity entity) {
        if (!PixelmonIntegration.isPokemonEntity(entity)) return Optional.empty();
        Optional<Object> pokemon = PixelmonIntegration.getPokemonObject(entity);
        List<DropRule> rules = new ArrayList<>();
        rules.addAll(rulesFromDropSources(entity));
        pokemon.ifPresent(value -> {
            rules.addAll(rulesFromDropSources(value));
            value(value, "getForm", "form").ifPresent(form -> rules.addAll(rulesFromDropSources(form)));
            value(value, "getSpecies", "species", "speciesValue").ifPresent(species -> rules.addAll(rulesFromDropSources(species)));
        });
        if (rules.isEmpty()) rules.addAll(rulesFromResourceIndex(entity));
        int xp = pokemon.flatMap(PixelmonDropFallback::baseExp).orElse(0);
        if (rules.isEmpty() && xp <= 0) return Optional.empty();
        return Optional.of(new DropProfile(List.copyOf(rules), xp > 0 ? new XpProfile(xp, xp) : XpProfile.NONE));
    }

    private static List<DropRule> rulesFromResourceIndex(Entity entity) {
        if (entity.level().getServer() == null) return List.of();
        String species = PixelmonIntegration.getSpeciesId(entity).map(ResourceLocation::getPath).orElse("");
        if (species.isBlank()) return List.of();
        ResourceManager manager = entity.level().getServer().getResourceManager();
        DropIndex index = DROP_INDEX_CACHE.computeIfAbsent(System.identityHashCode(manager), ignored -> buildDropIndex(manager));
        List<DropRule> rules = index.bySpecies().getOrDefault(normalizeSpeciesKey(species), List.of());
        if (rules.isEmpty()) MobFarmBlockMod.LOGGER.debug("Pixelmon drop index had no rules for {} after scanning {} resources", species, index.resourceCount());
        return rules;
    }

    private static DropIndex buildDropIndex(ResourceManager manager) {
        Map<String, List<DropRule>> bySpecies = new java.util.HashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        int[] scanned = new int[] {0};
        for (String root : List.of("drops", "drop_tables", "pokemon_drops", "battle_drops", "loot_tables", "loot_table", "")) {
            try {
                var resources = manager.listResources(root, id -> id.getNamespace().equals("pixelmon")
                        && id.getPath().endsWith(".json")
                        && (id.getPath().contains("drop") || id.getPath().contains("loot") || id.getPath().contains("reward")));
                for (var entry : resources.entrySet()) {
                    String key = entry.getKey().toString();
                    if (!seen.add(key)) continue;
                    scanned[0]++;
                    ResourceLocation filenameItem = itemFromFileName(entry.getKey()).orElse(null);
                    for (SpeciesRule rule : speciesRulesFromJson(readResource(entry.getValue()), filenameItem)) {
                        bySpecies.computeIfAbsent(rule.species(), ignored -> new ArrayList<>()).add(rule.rule());
                    }
                }
            } catch (Throwable error) {
                warnOnce("Pixelmon resource drop index scan failed: " + root, error);
            }
        }
        bySpecies.replaceAll((species, rules) -> List.copyOf(dedupeRules(rules)));
        MobFarmBlockMod.LOGGER.info("Indexed {} Pixelmon drop resources for {} species", scanned[0], bySpecies.size());
        return new DropIndex(Map.copyOf(bySpecies), scanned[0]);
    }

    private static List<DropRule> dedupeRules(List<DropRule> rules) {
        Map<String, DropRule> unique = new java.util.LinkedHashMap<>();
        for (DropRule rule : rules) unique.put(rule.itemId() + "|" + rule.chance() + "|" + rule.minCount() + "|" + rule.maxCount(), rule);
        return List.copyOf(unique.values());
    }

    private static String readResource(Resource resource) throws java.io.IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            return out.toString();
        }
    }

    private static List<SpeciesRule> speciesRulesFromJson(String json, ResourceLocation filenameItem) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonElement root = JsonParser.parseString(json);
            List<SpeciesRule> rules = new ArrayList<>();
            collectJsonRules(root, new JsonContext(filenameItem, null, null, null, false), rules);
            return rules;
        } catch (Throwable error) {
            warnOnce("Pixelmon drop JSON parse failed", error);
            return List.of();
        }
    }

    private static void collectJsonRules(JsonElement element, JsonContext inherited, List<SpeciesRule> out) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) collectJsonRules(child, inherited, out);
            return;
        }
        if (!element.isJsonObject()) return;

        JsonObject object = element.getAsJsonObject();
        JsonContext context = inherited.merge(object);
        Set<String> species = speciesNames(object);
        if (context.item() != null && !species.isEmpty()) {
            for (String name : species) out.add(new SpeciesRule(normalizeSpeciesKey(name), context.toRule()));
        }

        for (var entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            JsonContext childContext = context;
            Optional<ResourceLocation> keyedItem = coerceItemId(key);
            if (keyedItem.isPresent()) childContext = childContext.withItem(keyedItem.get());
            if (context.item() != null && looksLikeSpeciesKey(key) && value != null && value.isJsonObject()) {
                JsonContext rowContext = childContext.merge(value.getAsJsonObject());
                out.add(new SpeciesRule(normalizeSpeciesKey(key), rowContext.toRule()));
            }
            collectJsonRules(value, childContext, out);
        }
    }

    private static Set<String> speciesNames(JsonObject object) {
        Set<String> names = new LinkedHashSet<>();
        for (var entry : object.entrySet()) {
            String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if (key.contains("pokemon") || key.contains("species") || key.equals("entity") || key.equals("entities")) collectNames(entry.getValue(), names);
        }
        return names;
    }

    private static void collectNames(JsonElement element, Set<String> names) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) nameFromText(primitive.getAsString()).ifPresent(names::add);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectNames(child, names);
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : List.of("name", "pokemon", "species", "id", "value")) if (object.has(key)) collectNames(object.get(key), names);
        }
    }

    private static Optional<String> nameFromText(String text) {
        if (text == null) return Optional.empty();
        String value = text.trim();
        if (value.isBlank()) return Optional.empty();
        if (value.contains(":")) value = value.substring(value.lastIndexOf(':') + 1);
        value = value.replace("pixelmon.", "");
        if (value.contains(".")) return Optional.empty();
        return Optional.of(normalizeSpeciesKey(value));
    }

    private static boolean looksLikeSpeciesKey(String key) {
        if (key == null || key.isBlank()) return false;
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains(":")) return false;
        if (List.of("item", "items", "drops", "entries", "pokemon", "species", "chance", "probability", "percentage", "quantity", "count", "min", "max", "amount", "weight").contains(lower)) return false;
        return lower.matches("[a-z0-9_.-]+");
    }

    private static Optional<ResourceLocation> itemFromFileName(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
        return coerceItemId(name);
    }

    private static Optional<Integer> baseExp(Object pokemon) {
        Optional<String> json = value(pokemon, "getSpecies", "species", "speciesValue")
                .flatMap(species -> PixelmonIntegration.reflectNoArg(species, "getJson").map(String::valueOf));
        if (json.isEmpty()) json = value(pokemon, "getForm", "form")
                .flatMap(form -> PixelmonIntegration.reflectNoArg(form, "getJson").map(String::valueOf));
        if (json.isEmpty()) return Optional.empty();
        Matcher matcher = BASE_EXP.matcher(json.get());
        if (!matcher.find()) return Optional.empty();
        try { return Optional.of(Integer.parseInt(matcher.group(1))); }
        catch (Throwable ignored) { return Optional.empty(); }
    }

    private static List<DropRule> rulesFromDropSources(Object target) {
        if (target == null) return List.of();
        List<DropRule> rules = new ArrayList<>();
        for (String name : new String[] {"getDrops", "drops", "getDropItems", "dropItems", "getDropTable", "dropTable", "getLootTable", "lootTable", "getRewards", "rewards"}) {
            value(target, name).ifPresent(table -> rules.addAll(rulesFromDropTable(table)));
        }
        for (Method method : target.getClass().getMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(name.contains("drop") || name.contains("loot") || name.contains("reward"))) continue;
            if (method.getReturnType() == Void.TYPE || method.getParameterCount() != 0) continue;
            if (!(method.getName().startsWith("get") || method.getName().startsWith("is") || method.getName().startsWith("has"))) continue;
            try {
                method.setAccessible(true);
                Object result = method.invoke(target);
                if (result != null && result != target) rules.addAll(rulesFromDropTable(result));
            } catch (Throwable error) {
                warnOnce("Pixelmon fallback drop method failed: " + target.getClass().getName() + "." + method.getName(), error);
            }
        }
        return rules;
    }

    private static List<DropRule> rulesFromDropTable(Object table) {
        List<DropRule> rules = new ArrayList<>();
        for (Object entry : extractDropEntries(table)) dropRuleFromEntry(entry).ifPresent(rules::add);
        return rules;
    }

    private static List<?> extractDropEntries(Object table) {
        if (table == null) return List.of();
        if (table instanceof Collection<?> collection) return List.copyOf(collection);
        if (table.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            int length = Array.getLength(table);
            for (int i = 0; i < length; i++) values.add(Array.get(table, i));
            return values;
        }
        for (String name : new String[] {"getEntries", "entries", "getDrops", "drops", "getRewards", "rewards", "getItems", "items"}) {
            Optional<Object> value = value(table, name);
            if (value.isPresent()) {
                Object result = value.get();
                if (result instanceof Collection<?> collection) return List.copyOf(collection);
                if (result.getClass().isArray()) {
                    List<Object> values = new ArrayList<>();
                    int length = Array.getLength(result);
                    for (int i = 0; i < length; i++) values.add(Array.get(result, i));
                    return values;
                }
            }
        }
        return List.of(table);
    }

    private static Optional<DropRule> dropRuleFromEntry(Object entry) {
        if (entry == null) return Optional.empty();
        Optional<Object> rawItem = Optional.<Object>empty();
        if (entry instanceof ItemStack || entry instanceof Item || entry instanceof ResourceLocation || entry instanceof CharSequence) rawItem = Optional.of(entry);
        rawItem = rawItem.or(() -> value(entry, "getItemStack", "itemStack", "getStack", "stack", "getItem", "item", "getItemId", "itemId", "getItemID", "itemID", "getIdentifier", "identifier", "getResourceLocation", "resourceLocation"));
        Optional<ResourceLocation> item = rawItem.flatMap(PixelmonDropFallback::coerceItemId);
        if (item.isEmpty()) return Optional.empty();
        double chance = value(entry, "getPercentage", "percentage", "getChance", "chance", "getProbability", "probability", "getDropChance", "dropChance").flatMap(PixelmonDropFallback::coerceDouble).orElse(100.0D);
        if (chance > 1.0D) chance /= 100.0D;
        int min = value(entry, "getMin", "min", "getMinCount", "minCount", "minimum", "getQuantity", "quantity", "getCount", "count", "getAmount", "amount").flatMap(PixelmonDropFallback::coerceInt).orElse(1);
        int max = value(entry, "getMax", "max", "getMaxCount", "maxCount", "maximum").flatMap(PixelmonDropFallback::coerceInt).orElse(min);
        return Optional.of(new DropRule(item.get(), Math.max(0.0D, Math.min(1.0D, chance)), Math.max(0, min), Math.max(Math.max(0, min), max), false, 0.0D, 0));
    }

    private static Optional<ResourceLocation> coerceItemId(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ItemStack stack) return stack.isEmpty() ? Optional.empty() : Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (value instanceof Item item) return Optional.of(BuiltInRegistries.ITEM.getKey(item));
        String text = String.valueOf(value).trim();
        if (text.isBlank() || text.contains("@") && text.contains(".")) return Optional.empty();
        if (text.contains("#")) text = text.substring(text.lastIndexOf('#') + 1);
        Optional<ResourceLocation> direct = parseItemId(text);
        if (direct.isPresent()) return direct;
        String path = text.toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_/.-]", "");
        return firstPresent(parseItemId("minecraft:" + path), parseItemId("pixelmon:" + path));
    }

    private static Optional<ResourceLocation> parseItemId(String text) {
        try {
            ResourceLocation id = text.contains(":") ? ResourceLocation.parse(text) : ResourceLocation.withDefaultNamespace(text);
            return BuiltInRegistries.ITEM.getOptional(id).isPresent() ? Optional.of(id) : Optional.empty();
        } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Object> value(Object target, String... names) {
        for (String name : names) {
            Optional<Object> result = PixelmonIntegration.reflectNoArg(target, name).or(() -> PixelmonIntegration.readField(target, name));
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    private static Optional<Integer> coerceInt(Object value) {
        if (value instanceof Number number) return Optional.of(number.intValue());
        int[] range = coerceRange(value);
        if (range != null) return Optional.of(range[0]);
        try { return Optional.of(Integer.parseInt(String.valueOf(value).replaceAll("[^0-9-]", ""))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Double> coerceDouble(Object value) {
        if (value instanceof Number number) return Optional.of(number.doubleValue());
        try { return Optional.of(Double.parseDouble(String.valueOf(value).replace("%", ""))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static int[] coerceRange(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return new int[] {number.intValue(), number.intValue()};
        String text = String.valueOf(value);
        Matcher matcher = RANGE.matcher(text);
        if (matcher.find()) {
            try { return orderedRange(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))); }
            catch (Throwable ignored) {}
        }
        return null;
    }

    private static String normalizeSpeciesKey(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (text.contains(":")) text = text.substring(text.lastIndexOf(':') + 1);
        return text.replace(' ', '_').replaceAll("[^a-z0-9_.-]", "");
    }

    private static int[] orderedRange(int a, int b) { return new int[] {Math.min(a, b), Math.max(a, b)}; }

    @SafeVarargs
    private static <T> Optional<T> firstPresent(Optional<T>... options) { for (Optional<T> option : options) if (option.isPresent()) return option; return Optional.empty(); }

    private static void warnOnce(String key, Throwable error) { if (WARNED.add(key)) MobFarmBlockMod.LOGGER.debug("Pixelmon fallback drops failed for {}: {}", key, error.toString()); }

    private record JsonContext(ResourceLocation item, Double chance, Integer min, Integer max, boolean hasQuantity) {
        JsonContext merge(JsonObject object) {
            ResourceLocation nextItem = itemFromObject(object).orElse(item);
            Double nextChance = number(object, "chance", "probability", "percentage", "dropChance").orElse(chance);
            Integer nextMin = number(object, "min", "minCount", "minimum").map(Double::intValue).orElse(min);
            Integer nextMax = number(object, "max", "maxCount", "maximum").map(Double::intValue).orElse(max);
            Optional<JsonElement> quantity = element(object, "quantity", "count", "amount");
            boolean nextHasQuantity = hasQuantity || quantity.isPresent() || nextMin != null || nextMax != null;
            if (quantity.isPresent()) {
                int[] range = coerceRangeFromJson(quantity.get());
                if (range != null) {
                    nextMin = range[0];
                    nextMax = range[1];
                } else if (nextMin == null) {
                    nextMin = numberFromJson(quantity.get()).map(Double::intValue).orElse(null);
                }
            }
            return new JsonContext(nextItem, nextChance, nextMin, nextMax, nextHasQuantity);
        }

        JsonContext withItem(ResourceLocation item) { return new JsonContext(item, chance, min, max, hasQuantity); }

        DropRule toRule() {
            double normalizedChance = chance == null ? 1.0D : chance;
            if (normalizedChance > 1.0D) normalizedChance /= 100.0D;
            int minCount = min == null ? 1 : min;
            int maxCount = max == null ? minCount : max;
            return new DropRule(item, Math.max(0.0D, Math.min(1.0D, normalizedChance)), Math.max(0, minCount), Math.max(Math.max(0, minCount), maxCount), false, 0.0D, 0);
        }

        private static Optional<ResourceLocation> itemFromObject(JsonObject object) {
            for (String key : List.of("item", "itemId", "itemID", "drop", "identifier", "resourceLocation")) {
                if (!object.has(key)) continue;
                JsonElement value = object.get(key);
                if (value.isJsonPrimitive()) {
                    Optional<ResourceLocation> item = coerceItemId(value.getAsString());
                    if (item.isPresent()) return item;
                }
            }
            return Optional.empty();
        }
    }

    private static Optional<Double> number(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key)) continue;
            Optional<Double> value = numberFromJson(object.get(key));
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    private static Optional<JsonElement> element(JsonObject object, String... keys) {
        for (String key : keys) if (object.has(key)) return Optional.of(object.get(key));
        return Optional.empty();
    }

    private static Optional<Double> numberFromJson(JsonElement element) {
        try {
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return Optional.of(element.getAsDouble());
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return coerceDouble(element.getAsString());
        } catch (Throwable ignored) {}
        return Optional.empty();
    }

    private static int[] coerceRangeFromJson(JsonElement element) {
        try {
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                if (array.size() >= 2) return orderedRange(array.get(0).getAsInt(), array.get(array.size() - 1).getAsInt());
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                Integer min = number(object, "min", "minimum", "start", "first").map(Double::intValue).orElse(null);
                Integer max = number(object, "max", "maximum", "end", "last").map(Double::intValue).orElse(null);
                if (min != null || max != null) return orderedRange(min == null ? max : min, max == null ? min : max);
            }
            if (element.isJsonPrimitive()) return coerceRange(element.getAsString());
        } catch (Throwable ignored) {}
        return null;
    }

    private record SpeciesRule(String species, DropRule rule) {}
    private record DropIndex(Map<String, List<DropRule>> bySpecies, int resourceCount) {}
    private PixelmonDropFallback() {}
}

package com.akitaattribute.mobfarmblock.integration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DropProfile;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.XpProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Extra Pixelmon drop discovery that probes the live entity and data resources. */
public final class PixelmonDropFallback {
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();
    private static final Pattern BASE_EXP = Pattern.compile("\\\"baseExp\\\"\\s*:\\s*(\\d+)");
    private static final Pattern ITEM_ID = Pattern.compile("\\\"(?:item|itemId|itemID|id)\\\"\\s*:\\s*\\\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\\\"");
    private static final Pattern CHANCE = Pattern.compile("\\\"(?:chance|probability|percentage)\\\"\\s*:\\s*([0-9.]+)");
    private static final Pattern MIN = Pattern.compile("\\\"(?:min|minCount|minimum)\\\"\\s*:\\s*(\\d+)");
    private static final Pattern MAX = Pattern.compile("\\\"(?:max|maxCount|maximum)\\\"\\s*:\\s*(\\d+)");

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
        if (rules.isEmpty()) rules.addAll(rulesFromResourceData(entity));
        if (rules.isEmpty()) rules.addAll(wikiFallbackRules(entity));
        int xp = pokemon.flatMap(PixelmonDropFallback::baseExp).orElse(0);
        if (rules.isEmpty() && xp <= 0) return Optional.empty();
        return Optional.of(new DropProfile(List.copyOf(rules), xp > 0 ? new XpProfile(xp, xp) : XpProfile.NONE));
    }

    private static List<DropRule> wikiFallbackRules(Entity entity) {
        String species = PixelmonIntegration.getSpeciesId(entity).map(ResourceLocation::getPath).orElse("");
        String variant = PixelmonIntegration.getDisplayKey(entity).orElse("").toLowerCase(java.util.Locale.ROOT);
        List<DropRule> rules = new ArrayList<>();
        if ("tarountula".equals(species)) {
            addRule(rules, "minecraft:spider_eye", 0.50D, 1, 1);
            addRule(rules, "minecraft:string", 1.00D, 1, 2);
        } else if ("oricorio".equals(species)) {
            addRule(rules, "minecraft:feather", 1.00D, 1, 2);
            if (variant.contains("form=pompom")) addRule(rules, "minecraft:dandelion", 0.50D, 1, 3);
            else if (variant.contains("form=baile")) addRule(rules, "minecraft:poppy", 0.50D, 1, 3);
            else if (variant.contains("form=pau")) addRule(rules, "minecraft:pink_tulip", 0.50D, 1, 3);
            else if (variant.contains("form=sensu")) addRule(rules, "minecraft:allium", 0.50D, 1, 3);
        }
        if (!rules.isEmpty()) MobFarmBlockMod.LOGGER.warn("Using temporary Pixelmon wiki drop fallback for {} with {} rules", species, rules.size());
        return rules;
    }

    private static void addRule(List<DropRule> rules, String item, double chance, int min, int max) {
        ResourceLocation id = ResourceLocation.parse(item);
        if (BuiltInRegistries.ITEM.getOptional(id).isPresent()) rules.add(new DropRule(id, chance, min, max, false, 0.0D, 0));
    }

    private static List<DropRule> rulesFromResourceData(Entity entity) {
        if (entity.level().getServer() == null) return List.of();
        String species = PixelmonIntegration.getSpeciesId(entity).map(ResourceLocation::getPath).orElse("");
        if (species.isBlank()) return List.of();
        List<DropRule> rules = new ArrayList<>();
        for (String root : List.of("drops", "drop_tables", "loot_tables", "loot_table", "pokemon")) {
            try {
                var resources = entity.level().getServer().getResourceManager().listResources(root, id ->
                        id.getNamespace().equals("pixelmon")
                                && id.getPath().toLowerCase(java.util.Locale.ROOT).contains(species.toLowerCase(java.util.Locale.ROOT))
                                && (id.getPath().contains("drop") || id.getPath().contains("loot") || root.equals("pokemon"))
                                && id.getPath().endsWith(".json"));
                for (var entry : resources.entrySet()) rules.addAll(rulesFromJson(readResource(entry.getValue())));
            } catch (Throwable error) {
                warnOnce("Pixelmon resource drop scan failed: " + root, error);
            }
        }
        return rules;
    }

    private static String readResource(Resource resource) throws java.io.IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            return out.toString();
        }
    }

    private static List<DropRule> rulesFromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<DropRule> rules = new ArrayList<>();
        Matcher matcher = ITEM_ID.matcher(json);
        while (matcher.find()) {
            try {
                ResourceLocation item = ResourceLocation.parse(matcher.group(1));
                if (!BuiltInRegistries.ITEM.getOptional(item).isPresent()) continue;
                String tail = json.substring(matcher.start(), Math.min(json.length(), matcher.start() + 500));
                double chance = number(CHANCE, tail).orElse(1.0D);
                if (chance > 1.0D) chance /= 100.0D;
                int min = number(MIN, tail).map(Double::intValue).orElse(1);
                int max = number(MAX, tail).map(Double::intValue).orElse(min);
                rules.add(new DropRule(item, Math.max(0.0D, Math.min(1.0D, chance)), Math.max(0, min), Math.max(Math.max(0, min), max), false, 0.0D, 0));
            } catch (Throwable ignored) {}
        }
        return rules;
    }

    private static Optional<Double> number(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return Optional.empty();
        try { return Optional.of(Double.parseDouble(matcher.group(1))); }
        catch (Throwable ignored) { return Optional.empty(); }
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
        try { return Optional.of(Integer.parseInt(String.valueOf(value).replaceAll("[^0-9-]", ""))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Double> coerceDouble(Object value) {
        if (value instanceof Number number) return Optional.of(number.doubleValue());
        try { return Optional.of(Double.parseDouble(String.valueOf(value).replace("%", ""))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static void warnOnce(String key, Throwable error) { if (WARNED.add(key)) MobFarmBlockMod.LOGGER.debug("Pixelmon fallback drops failed for {}: {}", key, error.toString()); }
    private PixelmonDropFallback() {}
}

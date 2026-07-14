package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Conservative Pixelmon drop fallback.
 *
 * This class intentionally does not scan arbitrary JSON resources and does not
 * hard-code wiki drops. If Pixelmon's live object graph exposes a concrete drop
 * table we can convert it; otherwise we only keep the base XP value.
 */
public final class PixelmonDropFallback {
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();
    private static final Pattern BASE_EXP = Pattern.compile("\\\"baseExp\\\"\\s*:\\s*(\\d+)");

    public static Optional<DropProfile> resolve(Entity entity) {
        if (!PixelmonIntegration.isPokemonEntity(entity)) return Optional.empty();
        Optional<Object> pokemon = PixelmonIntegration.getPokemonObject(entity);
        if (pokemon.isEmpty()) return Optional.empty();

        List<DropRule> rules = new ArrayList<>();
        rules.addAll(rulesFromNativeDropRegistry(entity));
        rules.addAll(rulesFromDropSources(entity));
        rules.addAll(rulesFromDropSources(pokemon.get()));
        value(pokemon.get(), "getForm", "form").ifPresent(form -> rules.addAll(rulesFromDropSources(form)));
        value(pokemon.get(), "getSpecies", "species", "speciesValue").ifPresent(species -> rules.addAll(rulesFromDropSources(species)));

        int xp = baseExp(pokemon.get()).orElse(0);
        if (rules.isEmpty() && xp <= 0) return Optional.empty();
        return Optional.of(new DropProfile(List.copyOf(dedupe(rules)), xp > 0 ? new XpProfile(xp, xp) : XpProfile.NONE));
    }

    private static List<DropRule> rulesFromNativeDropRegistry(Entity entity) {
        Optional<Object> registryDrops = invokeStaticCompatible("com.pixelmonmod.pixelmon.entities.npcs.registry.DropItemRegistry", "getDropsForPokemon", entity);
        if (registryDrops.isEmpty()) return List.of();
        List<DropRule> rules = rulesFromDropTable(registryDrops.get());
        if (rules.isEmpty()) warnOnce("Pixelmon native DropItemRegistry returned no convertible rules: " + registryDrops.get().getClass().getName(), null);
        return rules;
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
        ResourceLocation id;
        if (value instanceof ItemStack stack) {
            if (stack.isEmpty() || stack.is(Items.AIR)) return Optional.empty();
            id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        } else if (value instanceof Item item) {
            if (item == Items.AIR) return Optional.empty();
            id = BuiltInRegistries.ITEM.getKey(item);
        } else {
            String text = String.valueOf(value).trim();
            if (text.isBlank() || text.contains("@") && text.contains(".")) return Optional.empty();
            try { id = text.contains(":") ? ResourceLocation.parse(text) : ResourceLocation.withDefaultNamespace(text); }
            catch (Throwable ignored) { return Optional.empty(); }
        }
        if (id == null || id.equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) return Optional.empty();
        if ("pixelmon".equals(id.getNamespace()) && ("no_item".equals(id.getPath()) || "none".equals(id.getPath()))) return Optional.empty();
        return BuiltInRegistries.ITEM.getOptional(id).isPresent() ? Optional.of(id) : Optional.empty();
    }

    private static Optional<Integer> baseExp(Object pokemon) {
        Optional<String> json = value(pokemon, "getSpecies", "species", "speciesValue").flatMap(species -> PixelmonIntegration.reflectNoArg(species, "getJson").map(String::valueOf));
        if (json.isEmpty()) json = value(pokemon, "getForm", "form").flatMap(form -> PixelmonIntegration.reflectNoArg(form, "getJson").map(String::valueOf));
        if (json.isEmpty()) return Optional.empty();
        Matcher matcher = BASE_EXP.matcher(json.get());
        if (!matcher.find()) return Optional.empty();
        try { return Optional.of(Integer.parseInt(matcher.group(1))); }
        catch (Throwable ignored) { return Optional.empty(); }
    }

    private static List<DropRule> dedupe(List<DropRule> rules) {
        java.util.Map<String, DropRule> deduped = new java.util.LinkedHashMap<>();
        for (DropRule rule : rules) deduped.put(rule.itemId() + "|" + rule.chance() + "|" + rule.minCount() + "|" + rule.maxCount(), rule);
        return new ArrayList<>(deduped.values());
    }

    private static Optional<Object> value(Object target, String... names) {
        for (String name : names) {
            Optional<Object> result = PixelmonIntegration.reflectNoArg(target, name).or(() -> PixelmonIntegration.readField(target, name));
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    private static Optional<Object> invokeStaticCompatible(String className, String methodName, Object argument) {
        try {
            Class<?> type = Class.forName(className);
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) continue;
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(null, argument));
            }
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) continue;
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(null, argument));
            }
        } catch (Throwable error) {
            warnOnce("Pixelmon native DropItemRegistry lookup failed", error);
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

    private static void warnOnce(String key, Throwable error) {
        if (WARNED.add(key)) MobFarmBlockMod.LOGGER.debug("Pixelmon fallback drops failed for {}{}", key, error == null ? "" : ": " + error);
    }
    private PixelmonDropFallback() {}
}

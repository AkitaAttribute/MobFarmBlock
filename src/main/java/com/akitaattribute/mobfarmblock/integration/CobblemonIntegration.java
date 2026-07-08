package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.DropProfile;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.XpProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;

/** Optional Cobblemon integration using guarded reflection only. */
public final class CobblemonIntegration {
    private static final Set<String> WARNED = new HashSet<>();

    public static boolean isLoaded() { return net.neoforged.fml.ModList.get().isLoaded("cobblemon"); }
    public static boolean isPokemonEntity(Entity entity) { return entity != null && entity.getType().builtInRegistryHolder().key().location().getNamespace().equals("cobblemon"); }

    public static Optional<ResourceLocation> getSpeciesId(Entity entity) {
        if (!isPokemonEntity(entity)) return Optional.empty();
        Object pokemon = firstPresent(
                reflectNoArg(entity, "getPokemon"),
                reflectNoArg(entity, "pokemon"),
                readField(entity, "pokemon")
        ).orElse(entity);
        Optional<ResourceLocation> species = extractSpeciesFromPokemonObject(pokemon);
        species.ifPresent(id -> MobFarmBlockMod.LOGGER.debug("Resolved Cobblemon species {} from {}", id, entity.getClass().getName()));
        return species.filter(id -> !id.equals(entity.getType().builtInRegistryHolder().key().location()));
    }

    @SafeVarargs
    private static <T> Optional<T> firstPresent(Optional<T>... options) {
        for (Optional<T> option : options) if (option.isPresent()) return option;
        return Optional.empty();
    }

    public static Optional<Object> reflectNoArg(Object target, String methodName) {
        if (target == null) return Optional.empty();
        try {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null || method.getParameterCount() != 0) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target));
        } catch (Throwable error) {
            warnOnce("method:" + target.getClass().getName() + "." + methodName, error);
            return Optional.empty();
        }
    }

    public static Optional<Object> readField(Object target, String fieldName) {
        if (target == null) return Optional.empty();
        try {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) return Optional.empty();
            field.setAccessible(true);
            return Optional.ofNullable(field.get(target));
        } catch (Throwable error) {
            warnOnce("field:" + target.getClass().getName() + "." + fieldName, error);
            return Optional.empty();
        }
    }

    public static Optional<ResourceLocation> coerceResourceLocation(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ResourceLocation id) return Optional.of(id);
        if (value instanceof CharSequence text) return parse(text.toString());
        Optional<ResourceLocation> nsPath = namespacePath(value);
        if (nsPath.isPresent()) return nsPath;
        for (String method : new String[] {"getIdentifier", "getId", "identifier", "resourceLocation"}) {
            Optional<Object> result = reflectNoArg(value, method);
            if (result.isPresent() && result.get() != value) {
                Optional<ResourceLocation> coerced = coerceResourceLocation(result.get());
                if (coerced.isPresent()) return coerced;
            }
        }
        return parse(String.valueOf(value));
    }

    public static Optional<ResourceLocation> extractSpeciesFromPokemonObject(Object pokemon) {
        if (pokemon == null) return Optional.empty();
        for (String name : new String[] {"getSpecies", "species"}) {
            Optional<ResourceLocation> direct = reflectNoArg(pokemon, name).flatMap(CobblemonIntegration::extractSpeciesFromSpeciesObject);
            if (direct.isPresent()) return direct;
        }
        Optional<ResourceLocation> field = readField(pokemon, "species").flatMap(CobblemonIntegration::extractSpeciesFromSpeciesObject);
        if (field.isPresent()) return field;
        for (String name : new String[] {"getForm", "form", "getAspects", "aspects"}) {
            Optional<ResourceLocation> candidate = reflectNoArg(pokemon, name).flatMap(CobblemonIntegration::extractSpeciesFromSpeciesObject);
            if (candidate.isPresent()) return candidate;
        }
        return extractSpeciesFromSpeciesObject(pokemon);
    }

    public static Optional<ResourceLocation> extractSpeciesFromSpeciesObject(Object species) {
        if (species == null) return Optional.empty();
        Optional<ResourceLocation> direct = coerceResourceLocation(species);
        if (direct.isPresent() && "cobblemon".equals(direct.get().getNamespace())) return direct;
        for (String method : new String[] {"getResourceIdentifier", "resourceIdentifier", "getIdentifier", "identifier", "getId", "id", "resourceLocation"}) {
            Optional<ResourceLocation> candidate = reflectNoArg(species, method).flatMap(CobblemonIntegration::coerceResourceLocation);
            if (candidate.isPresent()) return candidate;
        }
        for (String field : new String[] {"resourceIdentifier", "identifier", "id", "name"}) {
            Optional<ResourceLocation> candidate = readField(species, field).flatMap(CobblemonIntegration::coerceResourceLocation);
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }

    private static Optional<ResourceLocation> namespacePath(Object value) {
        Optional<Object> namespace = reflectNoArg(value, "getNamespace").or(() -> readField(value, "namespace"));
        Optional<Object> path = reflectNoArg(value, "getPath").or(() -> readField(value, "path"));
        if (namespace.isPresent() && path.isPresent()) return parse(namespace.get() + ":" + path.get());
        return Optional.empty();
    }

    private static Optional<ResourceLocation> parse(String text) {
        try { return text != null && text.contains(":") ? Optional.of(ResourceLocation.parse(text)) : Optional.empty(); }
        catch (Throwable error) { warnOnce("parse:" + text, error); return Optional.empty(); }
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
        return null;
    }
    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        return null;
    }
    private static void warnOnce(String key, Throwable error) {
        if (WARNED.add(key)) MobFarmBlockMod.LOGGER.debug("Cobblemon reflection failed for {}: {}", key, error.toString());
    }

    public static Optional<String> getDisplayKey(Entity entity) {
        Optional<Object> pokemon = getPokemonObject(entity);
        if (pokemon.isEmpty()) return getSpeciesId(entity).map(ResourceLocation::toString);
        StringBuilder key = new StringBuilder();
        getSpeciesId(entity).ifPresent(id -> key.append(id));
        getFormObject(pokemon.get()).ifPresent(form -> {
            appendObjectValue(key, "form", value(form, "showdownId", "getShowdownId", "formOnlyShowdownId", "getFormOnlyShowdownId"));
            appendObjectValue(key, "baseScale", value(form, "getBaseScale", "baseScale"));
        });
        appendObjectValue(key, "aspects", value(pokemon.get(), "getAspects", "aspects"));
        return key.isEmpty() ? Optional.empty() : Optional.of(key.toString());
    }

    private static void appendObjectValue(StringBuilder key, String name, Optional<Object> value) {
        value.ifPresent(object -> {
            if (!key.isEmpty()) key.append('|');
            key.append(name).append('=').append(object);
        });
    }

    public static Optional<DropProfile> resolveBattleDropProfile(Entity entity) {
        if (!isPokemonEntity(entity)) return Optional.empty();
        Optional<Object> pokemon = getPokemonObject(entity);
        if (pokemon.isEmpty()) return Optional.of(DropProfile.EMPTY);
        List<DropRule> formRules = pokemon.flatMap(CobblemonIntegration::getFormObject)
                .flatMap(form -> dropTableFrom(form, "getDrops", "drops", "_drops"))
                .map(table -> rulesFromDropTable(table, pokemon.get()))
                .orElse(List.of());
        if (!formRules.isEmpty()) return Optional.of(new DropProfile(List.copyOf(formRules), XpProfile.NONE));
        List<DropRule> speciesRules = extractSpeciesObject(pokemon.get())
                .flatMap(species -> dropTableFrom(species, "getDrops", "drops"))
                .map(table -> rulesFromDropTable(table, pokemon.get()))
                .orElse(List.of());
        return Optional.of(new DropProfile(List.copyOf(speciesRules), XpProfile.NONE));
    }

    public static Optional<Object> getPokemonObject(Entity entity) {
        if (entity == null) return Optional.empty();
        return firstPresent(reflectNoArg(entity, "getPokemon"), reflectNoArg(entity, "pokemon"), readField(entity, "pokemon"));
    }

    public static Optional<Object> getFormObject(Object pokemon) {
        return firstPresent(reflectNoArg(pokemon, "getForm"), reflectNoArg(pokemon, "form"), readField(pokemon, "form"));
    }

    public static Optional<Object> extractSpeciesObject(Object pokemon) {
        return firstPresent(reflectNoArg(pokemon, "getSpecies"), reflectNoArg(pokemon, "species"), readField(pokemon, "species"));
    }

    private static Optional<Object> dropTableFrom(Object target, String... names) {
        for (String name : names) {
            Optional<Object> method = reflectNoArg(target, name);
            if (method.isPresent()) return method;
            Optional<Object> field = readField(target, name);
            if (field.isPresent()) return field;
        }
        return Optional.empty();
    }

    private static List<DropRule> rulesFromDropTable(Object table, Object pokemon) {
        List<DropRule> rules = new ArrayList<>();
        for (Object entry : extractDropEntries(table, pokemon)) {
            dropRuleFromEntry(entry).ifPresent(rules::add);
        }
        return rules;
    }

    private static List<?> extractDropEntries(Object table, Object pokemon) {
        if (table == null) return List.of();
        if (table instanceof Collection<?> collection) return List.copyOf(collection);
        for (String name : new String[] {"getEntries", "entries", "getDrops", "drops"}) {
            Optional<Object> value = reflectNoArg(table, name).or(() -> readField(table, name));
            if (value.isPresent()) {
                Object result = value.get();
                if (result instanceof Collection<?> collection) return List.copyOf(collection);
                if (result.getClass().isArray()) return List.of((Object[]) result);
            }
        }
        for (Method method : table.getClass().getMethods()) {
            if (!"getDrops".equals(method.getName()) || method.getParameterCount() != 2) continue;
            try {
                Object result = method.invoke(table, 1, pokemon);
                if (result instanceof Collection<?> collection) return List.copyOf(collection);
            } catch (Throwable error) {
                warnOnce("drops:" + table.getClass().getName() + ".getDrops", error);
            }
        }
        return List.of();
    }

    private static Optional<DropRule> dropRuleFromEntry(Object entry) {
        Optional<Object> rawItem = value(entry, "getItem", "item").or(() -> value(entry, "getItemId", "itemId")).or(() -> value(entry, "itemStack", "stack"));
        Optional<ResourceLocation> item = rawItem.flatMap(CobblemonIntegration::coerceItemId);
        Optional<Object> rawPercentage = value(entry, "getPercentage", "percentage", "getChance", "chance");
        Optional<Object> rawQuantity = value(entry, "getQuantity", "quantity");
        Optional<Object> rawRange = value(entry, "getQuantityRange", "quantityRange", "range");
        Optional<Object> rawMaxSelectable = value(entry, "getMaxSelectableTimes", "maxSelectableTimes");
        if (item.isEmpty()) {
            MobFarmBlockMod.LOGGER.debug("Cobblemon drop entry could not resolve item: class={} value={}", entry.getClass().getName(), entry);
            return Optional.empty();
        }
        double percentage = rawPercentage.flatMap(CobblemonIntegration::coerceDouble).orElse(100.0D);
        double chance = percentage > 1.0D ? percentage / 100.0D : percentage;
        int[] range = rawRange.map(CobblemonIntegration::coerceRange).orElse(null);
        int min = range == null ? rawQuantity.flatMap(CobblemonIntegration::coerceInt).orElse(1) : range[0];
        int max = range == null ? min : range[1];
        int maxSelectableTimes = rawMaxSelectable.flatMap(CobblemonIntegration::coerceInt).orElse(1);
        if (maxSelectableTimes > 1) max = Math.max(max, max * maxSelectableTimes);
        int normalizedMin = Math.max(0, min);
        int normalizedMax = Math.max(normalizedMin, max);
        DropRule rule = new DropRule(item.get(), Math.max(0.0D, Math.min(1.0D, chance)), normalizedMin, normalizedMax, false, 0.0D, 0);
        MobFarmBlockMod.LOGGER.debug("Cobblemon drop entry converted: entryClass={} entry={} rawItem={} rawChance={} rawQuantity={} rawRange={} rawMaxSelectable={} -> item={} chance={} min={} max={}",
                entry.getClass().getName(), entry, rawItem.map(String::valueOf).orElse("unavailable"), rawPercentage.map(String::valueOf).orElse("unavailable"),
                rawQuantity.map(String::valueOf).orElse("unavailable"), rawRange.map(String::valueOf).orElse("unavailable"), rawMaxSelectable.map(String::valueOf).orElse("unavailable"),
                rule.itemId(), rule.chance(), rule.minCount(), rule.maxCount());
        return Optional.of(rule);
    }

    private static Optional<Object> value(Object target, String... names) {
        for (String name : names) {
            Optional<Object> method = reflectNoArg(target, name);
            if (method.isPresent()) return method;
            Optional<Object> field = readField(target, name);
            if (field.isPresent()) return field;
        }
        return Optional.empty();
    }

    private static Optional<ResourceLocation> coerceItemId(Object value) {
        if (value instanceof ItemStack stack) return Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (value instanceof Item item) return Optional.of(BuiltInRegistries.ITEM.getKey(item));
        return coerceResourceLocation(value);
    }

    private static Optional<Double> coerceDouble(Object value) {
        if (value instanceof Number number) return Optional.of(number.doubleValue());
        try { return Optional.of(Double.parseDouble(String.valueOf(value))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Integer> coerceInt(Object value) {
        if (value instanceof Number number) return Optional.of(number.intValue());
        try { return Optional.of(Integer.parseInt(String.valueOf(value))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static int[] coerceRange(Object value) {
        if (value instanceof Collection<?> collection && !collection.isEmpty()) {
            List<Integer> ints = collection.stream().map(CobblemonIntegration::coerceInt).filter(Optional::isPresent).map(Optional::get).toList();
            if (!ints.isEmpty()) return orderedRange(ints.get(0), ints.get(ints.size() - 1));
        }
        Optional<Integer> min = value(value, "getMin", "min", "getMinimum", "minimum", "getStart", "start", "getFirst", "first").flatMap(CobblemonIntegration::coerceInt);
        Optional<Integer> max = value(value, "getMax", "max", "getMaximum", "maximum", "getEndInclusive", "endInclusive", "getEnd", "end", "getLast", "last").flatMap(CobblemonIntegration::coerceInt);
        if (min.isPresent() || max.isPresent()) return orderedRange(min.orElse(max.orElse(1)), max.orElse(min.orElse(1)));
        String text = String.valueOf(value).replace("..", "-").replace(" ", "");
        String[] parts = text.split("-");
        if (parts.length >= 2) {
            try { return orderedRange(Integer.parseInt(parts[0].replaceAll("\\D", "")), Integer.parseInt(parts[1].replaceAll("\\D", ""))); } catch (Throwable ignored) {}
        }
        return new int[] {1, 1};
    }

    private static int[] orderedRange(int a, int b) {
        return new int[] {Math.min(a, b), Math.max(a, b)};
    }

    public static Optional<DisplaySnapshot> resolveDisplay(Entity entity) { return Optional.empty(); }
    private CobblemonIntegration() {}
}

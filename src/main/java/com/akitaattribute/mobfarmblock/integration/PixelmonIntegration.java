package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.DropProfile;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.XpProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Optional Pixelmon integration using guarded reflection only. */
public final class PixelmonIntegration {
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    public static boolean isLoaded() { return net.neoforged.fml.ModList.get().isLoaded("pixelmon"); }
    public static boolean isPokemonEntity(Entity entity) { return entity != null && isLoaded() && entity.getType().builtInRegistryHolder().key().location().getNamespace().equals("pixelmon"); }

    public static Optional<ResourceLocation> getSpeciesId(Entity entity) {
        if (!isPokemonEntity(entity)) return Optional.empty();
        Optional<Object> pokemon = getPokemonObject(entity);
        Optional<ResourceLocation> species = pokemon.flatMap(PixelmonIntegration::extractSpeciesFromPokemonObject)
                .filter(id -> !id.equals(entity.getType().builtInRegistryHolder().key().location()));
        species.ifPresent(id -> MobFarmBlockMod.LOGGER.debug("Resolved Pixelmon species {} from {}", id, entity.getClass().getName()));
        return species;
    }

    public static Optional<String> getDisplayKey(Entity entity) {
        if (!isPokemonEntity(entity)) return Optional.empty();
        Optional<Object> pokemon = getPokemonObject(entity);
        if (pokemon.isEmpty()) return getSpeciesId(entity).map(ResourceLocation::toString);
        StringBuilder key = new StringBuilder();
        getSpeciesId(entity).ifPresent(id -> key.append(id));
        appendObjectValue(key, "form", cleanNamedValue(value(pokemon.get(), "getForm", "form")));
        appendObjectValue(key, "palette", cleanNamedValue(value(pokemon.get(), "getPalette", "palette")));
        appendObjectValue(key, "gender", value(pokemon.get(), "getGender", "gender"));
        appendObjectValue(key, "shiny", value(pokemon.get(), "isShiny", "getShiny", "shiny"));
        appendObjectValue(key, "growth", cleanNamedValue(value(pokemon.get(), "getGrowth", "growth")));
        appendObjectValue(key, "renderScale", value(pokemon.get(), "getRenderScale", "renderScale"));
        appendObjectValue(key, "size", value(pokemon.get(), "getSize", "size"));
        return key.isEmpty() ? Optional.empty() : Optional.of(key.toString());
    }

    public static Optional<DropProfile> resolveBattleDropProfile(Entity entity) {
        if (!isPokemonEntity(entity)) return Optional.empty();
        Optional<Object> pokemon = getPokemonObject(entity);
        if (pokemon.isEmpty()) return Optional.of(DropProfile.EMPTY);
        List<DropRule> rules = new ArrayList<>();
        rules.addAll(rulesFromDropSources(pokemon.get(), pokemon.get()));
        Object form = value(pokemon.get(), "getForm", "form").orElse(null);
        if (form != null) rules.addAll(rulesFromDropSources(form, pokemon.get()));
        Object species = value(pokemon.get(), "getSpecies", "species", "speciesValue").orElse(null);
        if (species != null) rules.addAll(rulesFromDropSources(species, pokemon.get()));
        DropProfile profile = rules.isEmpty() ? DropProfile.EMPTY : new DropProfile(List.copyOf(rules), XpProfile.NONE);
        MobFarmBlockMod.LOGGER.debug("Resolved Pixelmon drop profile for {} with {} rules", getSpeciesId(entity).map(ResourceLocation::toString).orElse("unknown"), rules.size());
        return Optional.of(profile);
    }

    private static List<DropRule> rulesFromDropSources(Object target, Object pokemon) {
        if (target == null) return List.of();
        List<DropRule> rules = new ArrayList<>();
        for (String name : new String[] {"getDrops", "drops", "getDropItems", "dropItems", "getDropTable", "dropTable", "getLootTable", "lootTable", "getRewards", "rewards"}) {
            value(target, name).ifPresent(table -> rules.addAll(rulesFromDropTable(table, pokemon)));
        }
        for (Method method : target.getClass().getMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(name.contains("drop") || name.contains("loot") || name.contains("reward"))) continue;
            if (method.getReturnType() == Void.TYPE || method.getParameterCount() > 2) continue;
            try {
                method.setAccessible(true);
                Object result = null;
                if (method.getParameterCount() == 0) result = method.invoke(target);
                else if (method.getParameterCount() == 1) result = method.invoke(target, pokemon);
                else {
                    result = tryInvoke(method, target, 1, pokemon)
                            .or(() -> tryInvoke(method, target, pokemon, 1))
                            .orElse(null);
                }
                if (result != null && result != target) rules.addAll(rulesFromDropTable(result, pokemon));
            } catch (Throwable error) {
                warnOnce("Pixelmon drop method failed: " + target.getClass().getName() + "." + method.getName(), error);
            }
        }
        return rules;
    }

    private static Optional<Object> tryInvoke(Method method, Object target, Object... args) {
        try { return Optional.ofNullable(method.invoke(target, args)); }
        catch (Throwable ignored) { return Optional.empty(); }
    }

    private static List<DropRule> rulesFromDropTable(Object table, Object pokemon) {
        List<DropRule> rules = new ArrayList<>();
        for (Object entry : extractDropEntries(table, pokemon)) dropRuleFromEntry(entry).ifPresent(rules::add);
        return rules;
    }

    private static List<?> extractDropEntries(Object table, Object pokemon) {
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
        Optional<ResourceLocation> item = rawItem.flatMap(PixelmonIntegration::coerceItemId);
        if (item.isEmpty()) return Optional.empty();
        Optional<Object> rawPercentage = value(entry, "getPercentage", "percentage", "getChance", "chance", "getProbability", "probability", "getDropChance", "dropChance");
        Optional<Object> rawQuantity = value(entry, "getQuantity", "quantity", "getCount", "count", "getAmount", "amount");
        Optional<Object> rawMin = value(entry, "getMin", "min", "getMinCount", "minCount", "minimum");
        Optional<Object> rawMax = value(entry, "getMax", "max", "getMaxCount", "maxCount", "maximum");
        Optional<Object> rawRange = value(entry, "getQuantityRange", "quantityRange", "range", "countRange");
        double percentage = rawPercentage.flatMap(PixelmonIntegration::coerceDouble).orElse(100.0D);
        double chance = percentage > 1.0D ? percentage / 100.0D : percentage;
        int[] range = rawRange.map(PixelmonIntegration::coerceRange).orElse(null);
        int min = range == null ? rawMin.flatMap(PixelmonIntegration::coerceInt).or(() -> rawQuantity.flatMap(PixelmonIntegration::coerceInt)).orElse(1) : range[0];
        int max = range == null ? rawMax.flatMap(PixelmonIntegration::coerceInt).orElse(min) : range[1];
        return Optional.of(new DropRule(item.get(), Math.max(0.0D, Math.min(1.0D, chance)), Math.max(0, min), Math.max(Math.max(0, min), max), false, 0.0D, 0));
    }

    private static Optional<ResourceLocation> coerceItemId(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ItemStack stack) return stack.isEmpty() ? Optional.empty() : Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (value instanceof Item item) return Optional.of(BuiltInRegistries.ITEM.getKey(item));
        Optional<Object> nested = value(value, "getItem", "item", "getStack", "stack", "getItemStack", "itemStack", "getIdentifier", "identifier", "getResourceLocation", "resourceLocation", "getRegistryName", "registryName");
        if (nested.isPresent() && nested.get() != value) return coerceItemId(nested.get());
        String text = String.valueOf(value).trim();
        if (text.isBlank() || text.contains("@") && text.contains(".")) return Optional.empty();
        int colon = text.lastIndexOf(':');
        try {
            if (colon > 0 && colon < text.length() - 1) return Optional.of(ResourceLocation.parse(sanitizeId(text)));
            String path = sanitizePath(text);
            return path.isBlank() ? Optional.empty() : Optional.of(ResourceLocation.withDefaultNamespace(path));
        } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Integer> coerceInt(Object value) {
        if (value instanceof Number number) return Optional.of(number.intValue());
        try { return Optional.of(Integer.parseInt(String.valueOf(value).replaceAll("[^0-9-]", ""))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Double> coerceDouble(Object value) {
        if (value instanceof Number number) return Optional.of(number.doubleValue());
        try { return Optional.of(Double.parseDouble(String.valueOf(value).replace("%", ""))); } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static int[] coerceRange(Object value) {
        if (value instanceof Collection<?> collection && !collection.isEmpty()) {
            List<Integer> ints = collection.stream().map(PixelmonIntegration::coerceInt).filter(Optional::isPresent).map(Optional::get).toList();
            if (!ints.isEmpty()) return orderedRange(ints.get(0), ints.get(ints.size() - 1));
        }
        Optional<Integer> min = value(value, "getMin", "min", "getMinimum", "minimum", "getStart", "start", "getFirst", "first").flatMap(PixelmonIntegration::coerceInt);
        Optional<Integer> max = value(value, "getMax", "max", "getMaximum", "maximum", "getEndInclusive", "endInclusive", "getEnd", "end", "getLast", "last").flatMap(PixelmonIntegration::coerceInt);
        if (min.isPresent() || max.isPresent()) return orderedRange(min.orElse(max.orElse(1)), max.orElse(min.orElse(1)));
        String text = String.valueOf(value).replace("..", "-").replace(" ", "");
        String[] parts = text.split("-");
        if (parts.length >= 2) {
            try { return orderedRange(Integer.parseInt(parts[0].replaceAll("\\D", "")), Integer.parseInt(parts[1].replaceAll("\\D", ""))); } catch (Throwable ignored) {}
        }
        return new int[] {1, 1};
    }

    private static int[] orderedRange(int a, int b) { return new int[] {Math.min(a, b), Math.max(a, b)}; }

    public static Optional<DisplaySnapshot> resolveDisplay(Entity entity) {
        if (!isPokemonEntity(entity) || !(entity instanceof LivingEntity living)) return Optional.empty();
        ResourceLocation entityType = entity.getType().builtInRegistryHolder().key().location();
        ResourceLocation species = getSpeciesId(entity).orElse(entityType);
        String variant = getDisplayKey(entity).orElse(species.toString());
        float scale = displayScale(entity);
        return Optional.of(new DisplaySnapshot(entityType, ResourceLocation.fromNamespaceAndPath("pixelmon", "textures/entity/pixelmon.png"), variant, "", living.isBaby(), scale));
    }

    public static Optional<Object> getPokemonObject(Entity entity) {
        if (entity == null) return Optional.empty();
        return firstPresent(reflectNoArg(entity, "getPokemon"), reflectNoArg(entity, "pokemon"), readField(entity, "pokemon"));
    }

    public static Optional<ResourceLocation> extractSpeciesFromPokemonObject(Object pokemon) {
        if (pokemon == null) return Optional.empty();
        for (String name : new String[] {"getSpecies", "species", "getSpeciesValue", "speciesValue"}) {
            Optional<ResourceLocation> direct = reflectNoArg(pokemon, name).flatMap(PixelmonIntegration::extractSpeciesFromSpeciesObject);
            if (direct.isPresent()) return direct;
        }
        Optional<ResourceLocation> field = readField(pokemon, "species").flatMap(PixelmonIntegration::extractSpeciesFromSpeciesObject);
        if (field.isPresent()) return field;
        return extractSpeciesFromSpeciesObject(pokemon);
    }

    public static Optional<ResourceLocation> extractSpeciesFromSpeciesObject(Object species) {
        if (species == null) return Optional.empty();

        Optional<ResourceLocation> named = speciesName(species).flatMap(PixelmonIntegration::parseOrPixelmon);
        if (named.isPresent()) return named;

        for (String method : new String[] {"getStrippedName", "getName", "getPokemonName", "getLocalizedName", "name"}) {
            Optional<ResourceLocation> candidate = reflectNoArg(species, method).flatMap(PixelmonIntegration::textName).flatMap(PixelmonIntegration::parseOrPixelmon);
            if (candidate.isPresent()) return candidate;
        }
        for (String field : new String[] {"strippedName", "name", "pokemonName"}) {
            Optional<ResourceLocation> candidate = readField(species, field).flatMap(PixelmonIntegration::textName).flatMap(PixelmonIntegration::parseOrPixelmon);
            if (candidate.isPresent()) return candidate;
        }

        for (String method : new String[] {"getRegistryName", "getResourceLocation", "getResourceIdentifier", "getIdentifier", "getId", "id", "resourceLocation"}) {
            Optional<ResourceLocation> candidate = reflectNoArg(species, method).flatMap(PixelmonIntegration::coerceResourceLocation);
            if (candidate.isPresent() && !looksLikeClassName(candidate.get())) return candidate;
        }
        for (String field : new String[] {"registryName", "resourceLocation", "resourceIdentifier", "identifier", "id"}) {
            Optional<ResourceLocation> candidate = readField(species, field).flatMap(PixelmonIntegration::coerceResourceLocation);
            if (candidate.isPresent() && !looksLikeClassName(candidate.get())) return candidate;
        }

        Optional<ResourceLocation> direct = coerceResourceLocation(species);
        return direct.filter(id -> !looksLikeClassName(id));
    }

    public static Optional<ResourceLocation> coerceResourceLocation(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ResourceLocation id) return Optional.of(normalizePixelmonId(id));
        Optional<ResourceLocation> nsPath = namespacePath(value);
        if (nsPath.isPresent()) return nsPath.filter(id -> !looksLikeClassName(id));
        Optional<String> named = speciesName(value).or(() -> textName(value));
        if (named.isPresent()) return named.flatMap(PixelmonIntegration::parseOrPixelmon);
        if (value instanceof Enum<?> e) return parseOrPixelmon(e.name());
        Optional<Object> nested = value(value, "getRegistryName", "getResourceLocation", "getResourceIdentifier", "getIdentifier", "getId", "id", "getName", "name");
        if (nested.isPresent() && nested.get() != value) return coerceResourceLocation(nested.get());
        return parseOrPixelmon(String.valueOf(value)).filter(id -> !looksLikeClassName(id));
    }

    private static Optional<String> speciesName(Object value) {
        Optional<Object> name = value(value, "getStrippedName", "getName", "getPokemonName", "getLocalizedName", "name");
        Optional<String> direct = name.flatMap(PixelmonIntegration::textName);
        if (direct.isPresent()) return direct;
        return nameFromToString(String.valueOf(value));
    }

    private static Optional<String> textName(Object value) {
        if (value == null) return Optional.empty();
        String text = String.valueOf(value).trim();
        if (text.isBlank()) return Optional.empty();
        if (text.contains("@") && text.contains(".")) return nameFromToString(text);
        if (text.matches("\\d+")) return Optional.empty();
        if (text.contains("{")) return nameFromToString(text);
        return Optional.of(text);
    }

    private static Optional<String> nameFromToString(String text) {
        if (text == null) return Optional.empty();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:name|Name)=([A-Za-z0-9_ .-]+)").matcher(text);
        if (matcher.find()) return Optional.of(matcher.group(1).trim());
        return Optional.empty();
    }

    private static Optional<Object> cleanNamedValue(Optional<Object> value) {
        return value.map(object -> speciesName(object).<Object>map(name -> name).orElse(object));
    }

    private static Optional<ResourceLocation> namespacePath(Object value) {
        Optional<Object> namespace = reflectNoArg(value, "getNamespace").or(() -> readField(value, "namespace"));
        Optional<Object> path = reflectNoArg(value, "getPath").or(() -> readField(value, "path"));
        if (namespace.isPresent() && path.isPresent()) return parseOrPixelmon(namespace.get() + ":" + path.get());
        return Optional.empty();
    }

    private static Optional<ResourceLocation> parseOrPixelmon(String raw) {
        if (raw == null) return Optional.empty();
        String text = raw.trim();
        if (text.isBlank()) return Optional.empty();
        Optional<String> named = nameFromToString(text);
        if (named.isPresent()) text = named.get();
        int bracket = text.indexOf('[');
        if (bracket > 0) text = text.substring(0, bracket).trim();
        int at = text.indexOf('@');
        if (at > 0 && text.contains(".")) text = text.substring(0, at).trim();
        if (text.contains(".")) return Optional.empty();
        int colon = text.lastIndexOf(':');
        try {
            if (colon > 0 && colon < text.length() - 1) return Optional.of(normalizePixelmonId(ResourceLocation.parse(sanitizeId(text))));
            String path = sanitizePath(text);
            if (path.isBlank() || path.matches("\\d+")) return Optional.empty();
            return Optional.of(ResourceLocation.fromNamespaceAndPath("pixelmon", path));
        } catch (Throwable error) {
            warnOnce("parse:" + raw, error);
            return Optional.empty();
        }
    }

    private static boolean looksLikeClassName(ResourceLocation id) { return id.getPath().contains("com.") || id.getPath().contains("pixelmonmod") || id.getPath().matches("\\d+"); }

    private static ResourceLocation normalizePixelmonId(ResourceLocation id) {
        return "pixelmon".equals(id.getNamespace()) ? ResourceLocation.fromNamespaceAndPath("pixelmon", sanitizePath(id.getPath())) : ResourceLocation.fromNamespaceAndPath("pixelmon", sanitizePath(id.getPath()));
    }

    private static String sanitizeId(String text) {
        String[] parts = text.split(":", 2);
        return parts.length == 2 ? parts[0].toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "") + ":" + sanitizePath(parts[1]) : sanitizePath(text);
    }

    private static String sanitizePath(String text) {
        return text.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_/.-]", "");
    }

    private static float displayScale(Entity entity) {
        Optional<Object> pokemon = getPokemonObject(entity);
        return pokemon.flatMap(p -> value(p, "getRenderScale", "renderScale", "getScale", "scale", "getGrowthScale", "growthScale"))
                .flatMap(PixelmonIntegration::coerceFloat)
                .filter(v -> v > 0.05F && v < 5.0F)
                .orElse(1.0F);
    }

    private static Optional<Float> coerceFloat(Object value) {
        if (value instanceof Number number) return Optional.of(number.floatValue());
        try { return Optional.of(Float.parseFloat(String.valueOf(value))); }
        catch (Throwable ignored) { return Optional.empty(); }
    }

    @SafeVarargs
    private static <T> Optional<T> firstPresent(Optional<T>... options) { for (Optional<T> option : options) if (option.isPresent()) return option; return Optional.empty(); }

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

    private static Optional<Object> value(Object target, String... names) {
        for (String name : names) {
            Optional<Object> result = reflectNoArg(target, name).or(() -> readField(target, name));
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    private static void appendObjectValue(StringBuilder key, String name, Optional<Object> value) {
        value.ifPresent(object -> {
            if (!key.isEmpty()) key.append('|');
            key.append(name).append('=').append(object);
        });
    }

    private static Method findMethod(Class<?> type, String name) { for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == 0) return m; return null; }
    private static Field findField(Class<?> type, String name) { for (Class<?> c = type; c != null; c = c.getSuperclass()) try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {} return null; }
    private static void warnOnce(String key, Throwable error) { if (WARNED.add(key)) MobFarmBlockMod.LOGGER.debug("Pixelmon reflection failed for {}: {}", key, error.toString()); }
    private PixelmonIntegration() {}
}
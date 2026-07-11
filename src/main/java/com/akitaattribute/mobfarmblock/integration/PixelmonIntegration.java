package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.DropProfile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
        appendObjectValue(key, "form", value(pokemon.get(), "getForm", "form"));
        appendObjectValue(key, "palette", value(pokemon.get(), "getPalette", "palette"));
        appendObjectValue(key, "gender", value(pokemon.get(), "getGender", "gender"));
        appendObjectValue(key, "shiny", value(pokemon.get(), "isShiny", "getShiny", "shiny"));
        appendObjectValue(key, "growth", value(pokemon.get(), "getGrowth", "growth"));
        appendObjectValue(key, "size", value(pokemon.get(), "getSize", "getScale", "scale"));
        return key.isEmpty() ? Optional.empty() : Optional.of(key.toString());
    }

    public static Optional<DropProfile> resolveBattleDropProfile(Entity entity) { return Optional.empty(); }

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
        Optional<ResourceLocation> direct = coerceResourceLocation(species);
        if (direct.isPresent() && "pixelmon".equals(direct.get().getNamespace())) return direct;
        for (String method : new String[] {"getRegistryValue", "getRegistryName", "getResourceLocation", "getResourceIdentifier", "getIdentifier", "getId", "id", "resourceLocation", "getName", "getPokemonName", "name"}) {
            Optional<ResourceLocation> candidate = reflectNoArg(species, method).flatMap(PixelmonIntegration::coerceResourceLocation);
            if (candidate.isPresent()) return candidate;
        }
        for (String field : new String[] {"registryValue", "registryName", "resourceLocation", "resourceIdentifier", "identifier", "id", "name"}) {
            Optional<ResourceLocation> candidate = readField(species, field).flatMap(PixelmonIntegration::coerceResourceLocation);
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }

    public static Optional<ResourceLocation> coerceResourceLocation(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ResourceLocation id) return Optional.of(normalizePixelmonId(id));
        Optional<ResourceLocation> nsPath = namespacePath(value);
        if (nsPath.isPresent()) return nsPath;
        if (value instanceof CharSequence text) return parseOrPixelmon(text.toString());
        if (value instanceof Enum<?> e) return parseOrPixelmon(e.name());
        Optional<Object> nested = value(value, "getRegistryValue", "getRegistryName", "getResourceLocation", "getResourceIdentifier", "getIdentifier", "getId", "id", "getName", "name");
        if (nested.isPresent() && nested.get() != value) return coerceResourceLocation(nested.get());
        return parseOrPixelmon(String.valueOf(value));
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
        int bracket = text.indexOf('[');
        if (bracket > 0) text = text.substring(0, bracket).trim();
        int at = text.indexOf('@');
        if (at > 0 && text.contains(".")) text = text.substring(0, at).trim();
        int colon = text.lastIndexOf(':');
        try {
            if (colon > 0 && colon < text.length() - 1) return Optional.of(normalizePixelmonId(ResourceLocation.parse(sanitizeId(text))));
            String path = sanitizePath(text);
            return path.isBlank() ? Optional.empty() : Optional.of(ResourceLocation.fromNamespaceAndPath("pixelmon", path));
        } catch (Throwable error) {
            warnOnce("parse:" + raw, error);
            return Optional.empty();
        }
    }

    private static ResourceLocation normalizePixelmonId(ResourceLocation id) {
        return "pixelmon".equals(id.getNamespace()) ? id : ResourceLocation.fromNamespaceAndPath("pixelmon", sanitizePath(id.getPath()));
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
        return pokemon.flatMap(p -> value(p, "getScale", "scale", "getSize", "size", "getGrowthScale", "growthScale"))
                .flatMap(PixelmonIntegration::coerceFloat)
                .filter(v -> v > 0.05F && v < 20.0F)
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

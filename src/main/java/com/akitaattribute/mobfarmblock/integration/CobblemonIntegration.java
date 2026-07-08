package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.DropProfile;

import net.minecraft.resources.ResourceLocation;
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

    public static Optional<DropProfile> resolveBattleDropProfile(Entity entity) { return Optional.empty(); }
    public static Optional<DisplaySnapshot> resolveDisplay(Entity entity) { return Optional.empty(); }
    private CobblemonIntegration() {}
}

package com.akitaattribute.mobfarmblock.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.MobKind;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Client-only Pixelmon dummy construction. Kept separate from the Cobblemon path. */
public final class PixelmonEntityRenderCache {
    private static final Map<String, Entity> CACHE = new HashMap<>();
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    public static Entity getOrCreate(StoredMob stored) {
        if (stored == null || stored.isEmpty() || Minecraft.getInstance().level == null || !isPixelmonStored(stored) || stored.speciesId == null) return null;
        String key = stored.mobId + "|" + stored.speciesId + "|" + stored.display.variantKey() + "|" + stored.display.scale();
        Entity cached = CACHE.get(key);
        if (cached != null) {
            ClientEntityRenderCache.freezeForRender(cached);
            return cached;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(stored.mobId);
        if (type == null || type == EntityType.PIG && !stored.mobId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG))) {
            warnOnce("Pixelmon entity type could not be resolved: " + stored.mobId);
            return null;
        }

        Entity entity = type.create(Minecraft.getInstance().level);
        if (entity == null) {
            warnOnce("Pixelmon dummy entity could not be created: " + stored.mobId);
            return null;
        }

        try {
            if (!applyPixelmon(entity, stored)) return null;
            CACHE.put(key, entity);
            return entity;
        } catch (Throwable error) {
            warnOnce("Pixelmon render dummy creation failed for " + key, error);
            return null;
        }
    }

    public static boolean isPixelmonStored(StoredMob stored) {
        return stored != null && stored.kind == MobKind.PIXELMON && "pixelmon:pixelmon".equals(stored.mobId.toString());
    }

    private static boolean applyPixelmon(Entity entity, StoredMob stored) {
        Optional<Object> species = findPixelmonSpeciesObject(stored.speciesId);
        Optional<Object> pokemon = createPokemon(stored.speciesId, species.orElse(null));
        if (pokemon.isEmpty()) pokemon = readPokemon(entity);
        if (pokemon.isEmpty()) {
            warnOnce("Pixelmon dummy has no Pokemon object and factory creation failed: " + stored.speciesId);
            return false;
        }

        boolean changed = false;
        Object pokemonObject = pokemon.get();
        if (species.isPresent()) changed |= applySpeciesToPokemon(pokemonObject, species.get(), stored.speciesId);
        changed |= applyVariantHints(pokemonObject, stored.display.variantKey());
        changed |= invoke(entity, "setPokemon", pokemonObject).isPresent();
        changed |= setField(entity, "pokemon", pokemonObject);
        changed |= invoke(entity, "setPixelmon", pokemonObject).isPresent();
        changed |= invoke(entity, "setPokemonData", pokemonObject).isPresent();
        invoke(entity, "refreshDimensions");
        invoke(entity, "recalculateSize");
        invoke(entity, "updateSize");
        ClientEntityRenderCache.freezeForRender(entity);

        Optional<ResourceLocation> readBack = readPokemon(entity).flatMap(PixelmonEntityRenderCache::readPokemonSpeciesId)
                .or(() -> readPokemonSpeciesId(pokemonObject));
        boolean valid = readBack.isPresent() && readBack.get().equals(stored.speciesId);
        if (!valid) {
            MobFarmBlockMod.LOGGER.info("Mob Farm Block Debug: Pixelmon render unresolved for storedSpecies={} readBack={} changed={} variant={}", stored.speciesId, readBack.map(Object::toString).orElse("unavailable"), changed, stored.display.variantKey());
        }
        return changed || valid;
    }

    private static boolean applySpeciesToPokemon(Object pokemon, Object species, ResourceLocation speciesId) {
        boolean changed = false;
        changed |= invoke(pokemon, "setSpecies", species).isPresent();
        changed |= invoke(pokemon, "setSpecies", speciesId.getPath()).isPresent();
        changed |= invoke(pokemon, "setSpecies", speciesId.toString()).isPresent();
        changed |= setField(pokemon, "species", species);
        changed |= setField(pokemon, "speciesValue", species);
        return changed;
    }

    private static boolean applyVariantHints(Object pokemon, String variantKey) {
        if (variantKey == null || variantKey.isBlank()) return false;
        boolean changed = false;
        String shiny = parseVariantValue(variantKey, "shiny");
        if (!shiny.isBlank()) {
            boolean value = Boolean.parseBoolean(shiny);
            changed |= invoke(pokemon, "setShiny", value).isPresent();
            changed |= setField(pokemon, "shiny", value);
        }
        String gender = parseVariantValue(variantKey, "gender");
        if (!gender.isBlank()) changed |= setEnumOrString(pokemon, "gender", "setGender", gender);
        String form = parseVariantValue(variantKey, "form");
        if (!form.isBlank()) changed |= setEnumOrString(pokemon, "form", "setForm", form);
        String palette = parseVariantValue(variantKey, "palette");
        if (!palette.isBlank()) changed |= setEnumOrString(pokemon, "palette", "setPalette", palette);
        return changed;
    }

    private static boolean setEnumOrString(Object target, String fieldName, String setter, String text) {
        boolean changed = false;
        Optional<Object> current = readField(target, fieldName).or(() -> invoke(target, "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1)));
        if (current.isPresent() && current.get().getClass().isEnum()) {
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object enumValue = Enum.valueOf((Class<Enum>) current.get().getClass(), text.toUpperCase(java.util.Locale.ROOT));
                changed |= invoke(target, setter, enumValue).isPresent();
                changed |= setField(target, fieldName, enumValue);
                return changed;
            } catch (Throwable error) {
                warnOnce("Pixelmon enum conversion failed: " + fieldName + "=" + text, error);
            }
        }
        changed |= invoke(target, setter, text).isPresent();
        changed |= setField(target, fieldName, text);
        return changed;
    }

    private static Optional<Object> createPokemon(ResourceLocation speciesId, Object speciesObject) {
        for (String className : java.util.List.of(
                "com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory",
                "com.pixelmonmod.pixelmon.api.pokemon.PokemonBuilder",
                "com.pixelmonmod.pixelmon.api.pokemon.PokemonRegistry"
        )) {
            try {
                Class<?> type = Class.forName(className);
                for (String method : new String[] {"create", "createPokemon", "createFromSpecies", "fromSpecies", "get"}) {
                    Optional<Object> created = Optional.empty();
                    if (speciesObject != null) created = invokeStaticOrSingleton(type, method, speciesObject);
                    created = created.or(() -> invokeStaticOrSingleton(type, method, speciesId))
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId.getPath()))
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId.toString()))
                            .or(() -> invokeStaticOrSingleton(type, method, titleCase(speciesId.getPath())));
                    if (created.isPresent()) return created;
                }
            } catch (Throwable error) {
                warnOnce("Pixelmon Pokemon factory unavailable: " + className, error);
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> findPixelmonSpeciesObject(ResourceLocation speciesId) {
        for (String className : java.util.List.of(
                "com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies",
                "com.pixelmonmod.pixelmon.api.pokemon.species.Species",
                "com.pixelmonmod.pixelmon.api.pokemon.Species"
        )) {
            try {
                Class<?> type = Class.forName(className);
                for (String method : new String[] {"get", "getFromName", "fromName", "getSpecies", "getByName", "getById", "getByIdentifier", "getValue"}) {
                    Optional<Object> species = invokeStaticOrSingleton(type, method, speciesId)
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId.getPath()))
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId.toString()))
                            .or(() -> invokeStaticOrSingleton(type, method, titleCase(speciesId.getPath())));
                    if (species.isPresent()) return unwrapSpecies(species.get());
                }
                Optional<Object> fieldSpecies = readStaticField(type, speciesId.getPath().toUpperCase(java.util.Locale.ROOT))
                        .or(() -> readStaticField(type, titleCase(speciesId.getPath()).toUpperCase(java.util.Locale.ROOT)));
                if (fieldSpecies.isPresent()) return unwrapSpecies(fieldSpecies.get());
            } catch (Throwable error) {
                warnOnce("Pixelmon species lookup failed: " + className, error);
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> unwrapSpecies(Object value) {
        if (value == null) return Optional.empty();
        return invoke(value, "getValue").or(() -> invoke(value, "getSpecies")).or(() -> Optional.of(value));
    }

    private static Optional<ResourceLocation> readPokemonSpeciesId(Object pokemon) {
        return invoke(pokemon, "getSpecies").or(() -> readField(pokemon, "species")).or(() -> readField(pokemon, "speciesValue")).flatMap(PixelmonEntityRenderCache::coercePixelmonId);
    }

    private static Optional<ResourceLocation> coercePixelmonId(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ResourceLocation id) return Optional.of("pixelmon".equals(id.getNamespace()) ? id : ResourceLocation.fromNamespaceAndPath("pixelmon", sanitizePath(id.getPath())));
        Optional<Object> nested = invoke(value, "getRegistryValue").or(() -> invoke(value, "getRegistryName")).or(() -> invoke(value, "getResourceLocation")).or(() -> invoke(value, "getResourceIdentifier")).or(() -> invoke(value, "getIdentifier")).or(() -> invoke(value, "getId")).or(() -> invoke(value, "getName")).or(() -> readField(value, "registryValue")).or(() -> readField(value, "name"));
        if (nested.isPresent() && nested.get() != value) return coercePixelmonId(nested.get());
        String text = String.valueOf(value).trim();
        if (text.isBlank()) return Optional.empty();
        int colon = text.lastIndexOf(':');
        try {
            if (colon > 0 && colon < text.length() - 1) return Optional.of(ResourceLocation.parse(sanitizeId(text)));
            return Optional.of(ResourceLocation.fromNamespaceAndPath("pixelmon", sanitizePath(text)));
        } catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Object> readPokemon(Entity entity) { return invoke(entity, "getPokemon").or(() -> invoke(entity, "pokemon")).or(() -> readField(entity, "pokemon")); }

    private static String parseVariantValue(String variantKey, String key) {
        for (String part : variantKey.split("\\|")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals(key)) return part.substring(equals + 1);
        }
        return "";
    }

    private static Optional<Object> invoke(Object target, String name, Object... args) {
        if (target == null) return Optional.empty();
        try {
            Method method = findMethod(target.getClass(), name, args);
            if (method == null) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target, args));
        } catch (Throwable error) { warnOnce("invoke:" + target.getClass().getName() + "." + name, error); return Optional.empty(); }
    }

    private static Optional<Object> invokeStatic(Class<?> type, String name, Object... args) {
        try {
            Method method = findMethod(type, name, args);
            if (method == null || !Modifier.isStatic(method.getModifiers())) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(null, args));
        } catch (Throwable error) { warnOnce("invokeStatic:" + type.getName() + "." + name, error); return Optional.empty(); }
    }

    private static Optional<Object> invokeStaticOrSingleton(Class<?> type, String name, Object... args) {
        Optional<Object> direct = invokeStatic(type, name, args);
        if (direct.isPresent()) return direct;
        for (Object singleton : singletonObjects(type)) {
            Optional<Object> value = invoke(singleton, name, args);
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    private static java.util.List<Object> singletonObjects(Class<?> type) {
        java.util.List<Object> singletons = new java.util.ArrayList<>();
        for (String fieldName : java.util.List.of("INSTANCE", "Companion")) {
            try { Field field = type.getDeclaredField(fieldName); field.setAccessible(true); Object value = field.get(null); if (value != null) singletons.add(value); } catch (Throwable ignored) {}
        }
        return singletons;
    }

    private static Optional<Object> readStaticField(Class<?> type, String name) {
        try { Field field = findField(type, name); if (field == null || !Modifier.isStatic(field.getModifiers())) return Optional.empty(); field.setAccessible(true); return Optional.ofNullable(field.get(null)); }
        catch (Throwable error) { warnOnce("staticField:" + type.getName() + "." + name, error); return Optional.empty(); }
    }

    private static Optional<Object> readField(Object target, String name) {
        if (target == null) return Optional.empty();
        try { Field field = findField(target.getClass(), name); if (field == null) return Optional.empty(); field.setAccessible(true); return Optional.ofNullable(field.get(target)); }
        catch (Throwable error) { warnOnce("field:" + target.getClass().getName() + "." + name, error); return Optional.empty(); }
    }

    private static boolean setField(Object target, String name, Object value) {
        if (target == null) return false;
        try { Field field = findField(target.getClass(), name); if (field == null) return false; field.setAccessible(true); field.set(target, value); return true; }
        catch (Throwable error) { warnOnce("setField:" + target.getClass().getName() + "." + name, error); return false; }
    }

    private static Method findMethod(Class<?> type, String name, Object... args) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length && parametersCompatible(m.getParameterTypes(), args)) return m;
            }
        }
        return null;
    }

    private static boolean parametersCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null) continue;
            Class<?> parameter = wrapPrimitive(parameterTypes[i]);
            Class<?> actual = wrapPrimitive(args[i].getClass());
            if (!parameter.isAssignableFrom(actual)) return false;
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Field findField(Class<?> type, String name) { for (Class<?> c = type; c != null; c = c.getSuperclass()) try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {} return null; }
    private static String titleCase(String text) { if (text == null || text.isBlank()) return text; return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(java.util.Locale.ROOT); }
    private static String sanitizeId(String text) { String[] parts = text.split(":", 2); return parts.length == 2 ? parts[0].toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "") + ":" + sanitizePath(parts[1]) : sanitizePath(text); }
    private static String sanitizePath(String text) { return text.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_/.-]", ""); }
    private static void warnOnce(String message) { if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon render fallback: {}", message); }
    private static void warnOnce(String message, Throwable error) { if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon render fallback: {}: {}", message, error.toString()); }
    private PixelmonEntityRenderCache() {}
}

package com.akitaattribute.mobfarmblock.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

public final class ClientEntityRenderCache {
    private static final String EXISTING_POKEMON_ENTITY_STATE = "ExistingPokemonEntityState";
    private static final Map<String, Entity> CACHE = new HashMap<>();
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();
    private static final Map<String, String> SUCCESSFUL_COBBLEMON_METHODS = new HashMap<>();
    private static final java.util.Set<String> ANNOUNCED_COBBLEMON_METHODS = new java.util.HashSet<>();

    public static Entity getOrCreate(StoredMob stored) {
        if (stored == null || stored.isEmpty() || Minecraft.getInstance().level == null) return null;
        String key = stored.mobId + "|" + stored.speciesId + "|" + stored.display.variantKey() + "|" + stored.display.colorKey() + "|" + stored.display.baby();
        Entity cached = CACHE.get(key);
        if (cached != null) { freezeForRender(cached); return cached; }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(stored.mobId);
        if (type == null || type == EntityType.PIG && !stored.mobId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG))) {
            warnOnce("entity type could not be resolved: " + stored.mobId);
            return null;
        }
        Entity entity = type.create(Minecraft.getInstance().level);
        if (entity == null) {
            warnOnce("dummy entity could not be created: " + stored.mobId);
            return null;
        }
        if (!applyDisplay(entity, stored)) return null;
        CACHE.put(key, entity);
        return entity;
    }

    private static boolean applyDisplay(Entity entity, StoredMob stored) {
        freezeForRender(entity);
        if (entity instanceof Sheep sheep && !stored.display.colorKey().isBlank()) sheep.setColor(DyeColor.byName(stored.display.colorKey(), DyeColor.WHITE));
        if (entity instanceof AgeableMob ageable) ageable.setBaby(stored.display.baby());
        if ("cobblemon:pokemon".equals(stored.mobId.toString()) && stored.speciesId != null) {
            String method = applyCobblemonSpecies(entity, stored);
            if (method == null) return false;
        }
        return true;
    }

    public static void freezeForRender(Entity entity) {
        entity.tickCount = 0;
        entity.setYRot(0.0F); entity.setXRot(0.0F); entity.yRotO = 0.0F; entity.xRotO = 0.0F;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = 0.0F; living.yBodyRotO = 0.0F; living.yHeadRot = 0.0F; living.yHeadRotO = 0.0F;
            living.setPose(Pose.STANDING);
            living.walkAnimation.setSpeed(0.0F); living.walkAnimation.update(0.0F, 1.0F);
        }
    }

    private static String applyCobblemonSpecies(Entity entity, StoredMob stored) {
        Optional<Object> pokemon = readPokemon(entity);
        if (pokemon.isEmpty()) { announceCobblemonAttempt(stored, "unavailable:no_pokemon_object", false, validationFor(entity, stored)); warnOnce("Cobblemon dummy has no readable pokemon object for " + stored.speciesId); return null; }
        String key = cobblemonMethodKey(stored);
        String preferred = SUCCESSFUL_COBBLEMON_METHODS.get(key);
        for (String method : orderedCobblemonMethods(preferred)) {
            boolean applied = tryCobblemonMethod(entity, pokemon.get(), stored, method);
            ValidationResult validation = validationFor(entity, stored);
            announceCobblemonAttempt(stored, method + (method.equals(preferred) ? " (cached)" : ""), applied, validation);
            if (validation.valid()) {
                SUCCESSFUL_COBBLEMON_METHODS.put(key, method);
                return method;
            }
            if (method.equals(preferred)) {
                SUCCESSFUL_COBBLEMON_METHODS.remove(key);
                warnOnce("Previously successful Cobblemon render method failed and will be rediscovered: " + preferred);
            }
        }
        warnOnce("Could not apply Cobblemon species " + stored.speciesId + " to dummy; rendering generic placeholder");
        return null;
    }

    private static java.util.List<String> orderedCobblemonMethods(String preferred) {
        java.util.List<String> methods = new java.util.ArrayList<>();
        methods.add(EXISTING_POKEMON_ENTITY_STATE);
        if (preferred != null && !preferred.equals(EXISTING_POKEMON_ENTITY_STATE)) methods.add(preferred);
        for (String method : mutationCobblemonMethods()) if (!methods.contains(method)) methods.add(method);
        return methods;
    }

    private static java.util.List<String> mutationCobblemonMethods() {
        return java.util.List.of(
                "PokemonProperties:com.cobblemon.mod.common.api.pokemon.PokemonProperties",
                "PokemonProperties:com.cobblemon.mod.common.pokemon.PokemonProperties",
                "PokemonObject:setSpecies");
    }

    private static boolean tryCobblemonMethod(Entity entity, Object existingPokemon, StoredMob stored, String method) {
        if (EXISTING_POKEMON_ENTITY_STATE.equals(method)) return false;
        if (method.startsWith("PokemonProperties:")) return tryPokemonPropertiesClass(entity, stored.speciesId, stored.display.variantKey(), method.substring("PokemonProperties:".length()));
        if ("PokemonObject:setSpecies".equals(method)) {
            if (trySetSpeciesOnPokemon(existingPokemon, stored.speciesId)) {
                applyCobblemonAspects(entity, existingPokemon, stored.display.variantKey());
                syncCobblemonEntityData(entity, stored.speciesId, existingPokemon, stored.display.variantKey());
                return true;
            }
        }
        return false;
    }

    private static boolean tryPokemonPropertiesClass(Entity entity, ResourceLocation speciesId, String variantKey, String className) {
        try {
            Class<?> propertiesClass = Class.forName(className);
            Object props = invokeStatic(propertiesClass, "parse", pokemonPropertiesText(speciesId, variantKey)).or(() -> invokeStatic(propertiesClass, "parse", speciesId.getPath())).or(() -> invokeStatic(propertiesClass, "parse", speciesId.toString())).orElse(null);
            if (props == null) return false;
            Object pokemon = invoke(props, "create").orElse(null);
            if (pokemon == null) return false;
            if (invoke(entity, "setPokemon", pokemon).isPresent() || setField(entity, "pokemon", pokemon)) {
                applyCobblemonAspects(entity, pokemon, variantKey);
                syncCobblemonEntityData(entity, speciesId, pokemon, variantKey);
                return true;
            }
        } catch (Throwable error) {
            warnOnce("Cobblemon PokemonProperties failed: " + className, error);
        }
        return false;
    }

    private static String cobblemonMethodKey(StoredMob stored) { return stored.speciesId + "|" + stored.display.variantKey(); }

    private static void announceCobblemonAttempt(StoredMob stored, String method, boolean applied, ValidationResult validation) {
        String key = cobblemonMethodKey(stored) + "|" + method + "|" + applied + "|" + validation.valid();
        if (!ANNOUNCED_COBBLEMON_METHODS.add(key)) return;
        String message = "Mob Farm Block Debug: Cobblemon render method " + method
                + " for " + stored.speciesId
                + " applied=" + applied
                + " valid=" + validation.valid()
                + " readBack=" + validation.readBack()
                + " aspects=" + validation.aspects()
                + " renderable=" + validation.renderable();
        MobFarmBlockMod.LOGGER.info(message);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(message), false);
    }

    private static boolean trySetSpeciesOnPokemon(Object pokemon, ResourceLocation speciesId) {
        Optional<Object> species = findSpeciesObject(speciesId);
        if (species.isEmpty()) return false;
        boolean changed = invoke(pokemon, "setSpecies", species.get()).isPresent() || setField(pokemon, "species", species.get());
        return changed;
    }

    private static String pokemonPropertiesText(ResourceLocation speciesId, String variantKey) {
        StringBuilder text = new StringBuilder(speciesId.getPath());
        if (variantKey != null) {
            for (String aspect : parseAspects(variantKey)) text.append(' ').append(aspect);
            String form = parseVariantValue(variantKey, "form");
            if (!form.isBlank()) text.append(' ').append(form);
            String level = parseVariantValue(variantKey, "level");
            if (!level.isBlank()) text.append(" level=").append(level);
            String shiny = parseVariantValue(variantKey, "shiny");
            if ("true".equalsIgnoreCase(shiny)) text.append(" shiny");
            String gender = parseVariantValue(variantKey, "gender");
            if (!gender.isBlank()) text.append(' ').append(gender.toLowerCase(java.util.Locale.ROOT));
        }
        return text.toString();
    }

    private static java.util.List<String> parseAspects(String variantKey) {
        int start = variantKey.indexOf("aspects=[");
        if (start < 0) return java.util.List.of();
        int end = variantKey.indexOf(']', start);
        if (end < 0) return java.util.List.of();
        String body = variantKey.substring(start + 9, end);
        if (body.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(body.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private static String parseVariantValue(String variantKey, String key) {
        for (String part : variantKey.split("\\|")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals(key)) return part.substring(equals + 1);
        }
        return "";
    }

    private static Optional<Object> findSpeciesObject(ResourceLocation speciesId) {
        String[] classNames = {"com.cobblemon.mod.common.CobblemonSpecies", "com.cobblemon.mod.common.api.pokemon.CobblemonSpecies"};
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className);
                for (String method : new String[] {"getByIdentifier", "getById", "get", "getSpecies"}) {
                    Optional<Object> species = invokeStatic(type, method, speciesId).or(() -> invokeStatic(type, method, speciesId.toString())).or(() -> invokeStatic(type, method, speciesId.getPath()));
                    if (species.isPresent()) return species;
                }
            } catch (Throwable error) {
                warnOnce("Cobblemon species lookup failed: " + className, error);
            }
        }
        return Optional.empty();
    }

    private static void syncCobblemonEntityData(Entity entity, ResourceLocation speciesId, Object pokemon, String variantKey) {
        Optional<Object> species = readField(pokemon, "species").or(() -> invoke(pokemon, "getSpecies"));
        setEntityData(entity, "SPECIES", species.orElse(speciesId));
        setEntityData(entity, "SPECIES", speciesId);
        setEntityData(entity, "SPECIES", speciesId.getPath());
        java.util.List<String> aspects = parseAspects(variantKey);
        if (!aspects.isEmpty()) setEntityData(entity, "ASPECTS", aspects);
        invoke(entity, "refreshDimensions");
    }

    private static boolean setEntityData(Entity entity, String accessorFieldName, Object value) {
        try {
            Field accessorField = findField(entity.getClass(), accessorFieldName);
            if (accessorField == null) return false;
            accessorField.setAccessible(true);
            Object accessor = accessorField.get(null);
            entity.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor) accessor, value);
            return true;
        } catch (Throwable error) {
            warnOnce("Cobblemon entity data sync failed: " + accessorFieldName + "=" + value, error);
            return false;
        }
    }

    private static void applyCobblemonAspects(Entity entity, Object pokemon, String variantKey) {
        java.util.List<String> aspects = parseAspects(variantKey);
        if (aspects.isEmpty()) return;
        invoke(pokemon, "setAspects", aspects);
        setField(pokemon, "aspects", aspects);
        invoke(entity, "setAspects", aspects);
        setField(entity, "aspects", aspects);
        invoke(pokemon, "updateAspects");
        invoke(entity, "updateAspects");
    }

    private static ValidationResult validationFor(Entity entity, StoredMob stored) {
        Optional<Object> pokemon = readPokemon(entity);
        Optional<ResourceLocation> readBack = pokemon.flatMap(ClientEntityRenderCache::readPokemonSpeciesId)
                .or(() -> invoke(entity, "getExposedSpecies").flatMap(ClientEntityRenderCache::coerceResourceLocation));
        Optional<Object> aspects = pokemon.flatMap(p -> invoke(p, "getAspects").or(() -> readField(p, "aspects")))
                .or(() -> invoke(entity, "getExposedAspects"));
        Optional<Object> renderable = pokemon.flatMap(p -> invoke(p, "asRenderablePokemon"));
        boolean valid = readBack.isPresent() && readBack.get().equals(stored.speciesId);
        if (valid) {
            MobFarmBlockMod.LOGGER.debug("Cobblemon dummy validated: storedSpecies={} readBackSpecies={} aspects={} renderable={}", stored.speciesId, readBack.get(), aspects.map(Object::toString).orElse("unavailable"), renderable.map(Object::toString).orElse("unavailable"));
        }
        return new ValidationResult(valid, readBack.map(Object::toString).orElse("unavailable"), aspects.map(Object::toString).orElse("unavailable"), renderable.map(Object::toString).orElse("unavailable"));
    }

    private record ValidationResult(boolean valid, String readBack, String aspects, String renderable) {}

    private static Optional<ResourceLocation> readPokemonSpeciesId(Object pokemon) {
        return invoke(pokemon, "getSpecies").or(() -> readField(pokemon, "species")).flatMap(ClientEntityRenderCache::coerceResourceLocation);
    }

    private static Optional<ResourceLocation> coerceResourceLocation(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ResourceLocation id) return Optional.of(id);
        Optional<Object> resource = invoke(value, "getResourceIdentifier").or(() -> invoke(value, "getIdentifier")).or(() -> invoke(value, "getId")).or(() -> readField(value, "resourceIdentifier")).or(() -> readField(value, "identifier"));
        if (resource.isPresent() && resource.get() != value) return coerceResourceLocation(resource.get());
        String text = String.valueOf(value);
        try { return text.contains(":") ? Optional.of(ResourceLocation.parse(text)) : Optional.of(ResourceLocation.fromNamespaceAndPath("cobblemon", text)); }
        catch (Throwable ignored) { return Optional.empty(); }
    }

    private static Optional<Object> readPokemon(Entity entity) { return invoke(entity, "getPokemon").or(() -> invoke(entity, "pokemon")).or(() -> readField(entity, "pokemon")); }

    private static Optional<Object> invoke(Object target, String name, Object... args) {
        try {
            Method method = findMethod(target.getClass(), name, args.length);
            if (method == null) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target, args));
        } catch (Throwable error) { warnOnce("invoke:" + target.getClass().getName() + "." + name, error); return Optional.empty(); }
    }

    private static Optional<Object> invokeStatic(Class<?> type, String name, Object... args) {
        try {
            Method method = findMethod(type, name, args.length);
            if (method == null) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(null, args));
        } catch (Throwable error) { warnOnce("invokeStatic:" + type.getName() + "." + name, error); return Optional.empty(); }
    }

    private static Optional<Object> readField(Object target, String name) {
        try { Field field = findField(target.getClass(), name); if (field == null) return Optional.empty(); field.setAccessible(true); return Optional.ofNullable(field.get(target)); }
        catch (Throwable error) { warnOnce("field:" + target.getClass().getName() + "." + name, error); return Optional.empty(); }
    }

    private static boolean setField(Object target, String name, Object value) {
        try { Field field = findField(target.getClass(), name); if (field == null) return false; field.setAccessible(true); field.set(target, value); return true; }
        catch (Throwable error) { warnOnce("setField:" + target.getClass().getName() + "." + name, error); return false; }
    }

    private static Method findMethod(Class<?> type, String name, int arity) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        return null;
    }
    private static Field findField(Class<?> type, String name) { for (Class<?> c = type; c != null; c = c.getSuperclass()) try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {} return null; }
    private static void warnOnce(String message) { if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block render fallback: {}", message); }
    private static void warnOnce(String message, Throwable error) { if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block render fallback: {}: {}", message, error.toString()); }
    private ClientEntityRenderCache() {}
}

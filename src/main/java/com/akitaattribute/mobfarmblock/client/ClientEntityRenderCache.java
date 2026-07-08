package com.akitaattribute.mobfarmblock.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.client.Minecraft;
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
    private static final Map<String, Entity> CACHE = new HashMap<>();
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

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
        applyDisplay(entity, stored);
        CACHE.put(key, entity);
        return entity;
    }

    private static void applyDisplay(Entity entity, StoredMob stored) {
        freezeForRender(entity);
        if (entity instanceof Sheep sheep && !stored.display.colorKey().isBlank()) sheep.setColor(DyeColor.byName(stored.display.colorKey(), DyeColor.WHITE));
        if (entity instanceof AgeableMob ageable) ageable.setBaby(stored.display.baby());
        if ("cobblemon:pokemon".equals(stored.mobId.toString()) && stored.speciesId != null) applyCobblemonSpecies(entity, stored);
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

    private static void applyCobblemonSpecies(Entity entity, StoredMob stored) {
        Optional<Object> pokemon = readPokemon(entity);
        if (pokemon.isEmpty()) { warnOnce("Cobblemon dummy has no readable pokemon object for " + stored.speciesId); return; }
        if (tryPokemonProperties(entity, stored.speciesId, stored.display.variantKey())) return;
        if (trySetSpeciesOnPokemon(pokemon.get(), stored.speciesId)) { applyCobblemonAspects(entity, pokemon.get(), stored.display.variantKey()); return; }
        warnOnce("Could not apply Cobblemon species " + stored.speciesId + " to dummy; rendering generic placeholder");
    }

    private static boolean tryPokemonProperties(Entity entity, ResourceLocation speciesId, String variantKey) {
        String[] classNames = {"com.cobblemon.mod.common.api.pokemon.PokemonProperties", "com.cobblemon.mod.common.pokemon.PokemonProperties"};
        for (String className : classNames) {
            try {
                Class<?> propertiesClass = Class.forName(className);
                Object props = invokeStatic(propertiesClass, "parse", pokemonPropertiesText(speciesId, variantKey)).or(() -> invokeStatic(propertiesClass, "parse", speciesId.getPath())).or(() -> invokeStatic(propertiesClass, "parse", speciesId.toString())).orElse(null);
                if (props == null) continue;
                Object pokemon = invoke(props, "create").orElse(null);
                if (pokemon == null) continue;
                if (invoke(entity, "setPokemon", pokemon).isPresent() || setField(entity, "pokemon", pokemon)) { applyCobblemonAspects(entity, pokemon, variantKey); return true; }
            } catch (Throwable error) {
                warnOnce("Cobblemon PokemonProperties failed: " + className, error);
            }
        }
        return false;
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

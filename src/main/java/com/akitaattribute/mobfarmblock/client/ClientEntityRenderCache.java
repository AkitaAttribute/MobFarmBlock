package com.akitaattribute.mobfarmblock.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.CobblemonRenderSnapshot;
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
    private static Class<?> pokemonPropertiesClass;
    private static boolean pokemonPropertiesClassResolved;

    public static Entity getOrCreate(StoredMob stored) {
        if (stored == null || stored.isEmpty() || Minecraft.getInstance().level == null) return null;
        String key = stored.mobId + "|" + stored.speciesId + "|" + stored.display.variantKey() + "|" + stored.display.colorKey() + "|" + stored.display.baby() + "|" + snapshotHash(stored);
        Entity cached = CACHE.get(key);
        if (cached != null) {
            freezeForRender(cached);
            return cached;
        }

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

        try {
            if (!applyDisplay(entity, stored)) return null;
            CACHE.put(key, entity);
            return entity;
        } catch (Throwable error) {
            warnOnce("render dummy creation failed for " + key, error);
            return null;
        }
    }

    private static int snapshotHash(StoredMob stored) {
        return stored.cobblemonRenderSnapshot == null ? 0 : stored.cobblemonRenderSnapshot.toNbt().toString().hashCode();
    }

    private static boolean applyDisplay(Entity entity, StoredMob stored) {
        freezeForRender(entity);
        if (entity instanceof Sheep sheep && !stored.display.colorKey().isBlank()) sheep.setColor(DyeColor.byName(stored.display.colorKey(), DyeColor.WHITE));
        if (entity instanceof AgeableMob ageable) ageable.setBaby(stored.display.baby());
        if ("cobblemon:pokemon".equals(stored.mobId.toString()) && stored.speciesId != null) {
            return applyCobblemonDisplay(entity, stored);
        }
        return true;
    }

    private static boolean applyCobblemonDisplay(Entity entity, StoredMob stored) {
        // New captures should use the snapshot first.  Existing-state validation is
        // only a cheap exit for already-correct dummies, not the primary strategy.
        if (stored.cobblemonRenderSnapshot != null && applyCobblemonSnapshot(entity, stored)) return true;

        ValidationResult existing = validationFor(entity, stored);
        if (existing.valid()) {
            MobFarmBlockMod.LOGGER.debug("Cobblemon existing render state validated for {} aspects={} renderable={}", stored.speciesId, existing.aspects(), existing.renderable());
            return true;
        }

        if (tryPokemonPropertiesText(entity, stored.speciesId, pokemonPropertiesText(stored.speciesId, stored.display.variantKey()), pokemonPropertiesClass())) {
            readPokemon(entity).ifPresent(pokemon -> {
                applyCobblemonAspects(entity, pokemon, stored.display.variantKey());
                completeCobblemonRenderSync(entity, pokemon, stored.speciesId, parseAspects(stored.display.variantKey()), parseVariantInt(stored.display.variantKey(), "level", 1), 1.0F);
            });
            ValidationResult validation = validationFor(entity, stored);
            if (validation.valid()) return true;
            MobFarmBlockMod.LOGGER.info("Mob Farm Block Debug: Cobblemon legacy properties render failed for {} readBack={} aspects={} renderable={}", stored.speciesId, validation.readBack(), validation.aspects(), validation.renderable());
        }

        MobFarmBlockMod.LOGGER.info("Mob Farm Block Debug: Cobblemon render unavailable for {} variant={}", stored.speciesId, stored.display.variantKey());
        return false;
    }

    private static boolean applyCobblemonSnapshot(Entity entity, StoredMob stored) {
        CobblemonRenderSnapshot snapshot = stored.cobblemonRenderSnapshot;
        Optional<Object> pokemon = readPokemon(entity);
        if (pokemon.isEmpty()) {
            warnOnce("Cobblemon snapshot render failed because dummy has no Pokemon object: " + snapshot.speciesId());
            return false;
        }

        boolean directApplied = applySnapshotToPokemonObject(entity, pokemon.get(), snapshot);
        ValidationResult directValidation = validationFor(entity, stored);
        if (directApplied && snapshotValidationPasses(snapshot, directValidation)) {
            MobFarmBlockMod.LOGGER.debug("Cobblemon snapshot direct render validated for {} readBack={} aspects={} renderable={}", snapshot.speciesId(), directValidation.readBack(), directValidation.aspects(), directValidation.renderable());
            return true;
        }

        String propertiesText = cleanSnapshotPropertiesText(snapshot);
        boolean propertiesApplied = tryPokemonPropertiesText(entity, snapshot.speciesId(), propertiesText, pokemonPropertiesClass());
        Optional<Object> rebuiltPokemon = readPokemon(entity);
        rebuiltPokemon.ifPresent(value -> {
            applySnapshotToPokemonObject(entity, value, snapshot);
            completeCobblemonRenderSync(entity, value, snapshot.speciesId(), snapshot.aspects(), snapshot.level(), snapshot.scaleModifier());
        });
        ValidationResult propertiesValidation = validationFor(entity, stored);
        if (propertiesApplied && snapshotValidationPasses(snapshot, propertiesValidation)) {
            MobFarmBlockMod.LOGGER.debug("Cobblemon snapshot properties render validated for {} readBack={} aspects={} payloadFormat={}", snapshot.speciesId(), propertiesValidation.readBack(), propertiesValidation.aspects(), snapshot.pokemonPayloadFormat());
            return true;
        }

        MobFarmBlockMod.LOGGER.info("Mob Farm Block Debug: Cobblemon snapshot render failed for {} directApplied={} directValid={} propertiesText='{}' propertiesApplied={} propertiesValid={} readBack={} aspects={} expectedAspects={} renderable={} capturedRenderable={} payloadFormat={}",
                snapshot.speciesId(), directApplied, directValidation.valid(), propertiesText, propertiesApplied, propertiesValidation.valid(), propertiesValidation.readBack(), propertiesValidation.aspects(), snapshot.aspects(), propertiesValidation.renderable(), snapshot.renderableDebug(), snapshot.pokemonPayloadFormat());
        return false;
    }

    private static boolean applySnapshotToPokemonObject(Entity entity, Object pokemon, CobblemonRenderSnapshot snapshot) {
        boolean changed = false;
        Optional<Object> species = findSpeciesObject(snapshot.speciesId());
        if (species.isPresent()) {
            changed |= invoke(pokemon, "setSpecies", species.get()).isPresent();
            changed |= setField(pokemon, "species", species.get());
        }
        if (snapshot.aspects() != null && !snapshot.aspects().isEmpty()) changed |= setCobblemonAspects(entity, pokemon, snapshot.aspects());
        if (snapshot.level() > 0) {
            changed |= invoke(pokemon, "setLevel", snapshot.level()).isPresent();
            changed |= setField(pokemon, "level", snapshot.level());
        }
        changed |= invoke(pokemon, "setShiny", snapshot.shiny()).isPresent();
        changed |= setField(pokemon, "shiny", snapshot.shiny());
        if (snapshot.gender() != null && !snapshot.gender().isBlank()) changed |= setCobblemonGender(pokemon, snapshot.gender());
        if (snapshot.scaleModifier() > 0) {
            changed |= invoke(pokemon, "setScaleModifier", snapshot.scaleModifier()).isPresent();
            changed |= setField(pokemon, "scaleModifier", snapshot.scaleModifier());
        }
        changed |= completeCobblemonRenderSync(entity, pokemon, snapshot.speciesId(), snapshot.aspects(), snapshot.level(), snapshot.scaleModifier());
        return changed;
    }

    private static boolean completeCobblemonRenderSync(Entity entity, Object pokemon, ResourceLocation speciesId, java.util.List<String> aspects, int level, float scaleModifier) {
        boolean changed = false;
        // Reassigning the Pokemon object through the PokemonEntity setter is important:
        // Cobblemon's setter updates the client delegate, dimensions, and render-facing
        // state.  Directly changing Pokemon fields alone can validate eventually but
        // leave the renderer stale for several frames.
        changed |= invoke(entity, "setPokemon", pokemon).isPresent();
        changed |= setField(entity, "pokemon", pokemon);
        java.util.LinkedHashSet<String> aspectSet = new java.util.LinkedHashSet<>(aspects == null ? java.util.List.of() : aspects);
        setEntityData(entity, "SPECIES", speciesId.getPath());
        if (!aspectSet.isEmpty()) setEntityData(entity, "ASPECTS", aspectSet);
        if (level > 0) setEntityData(entity, "LABEL_LEVEL", level);
        if (scaleModifier > 0) setEntityData(entity, "SCALE_MODIFIER", scaleModifier);
        readField(entity, "delegate").ifPresent(delegate -> invoke(delegate, "changePokemon", pokemon));
        invoke(entity, "refreshDimensions");
        freezeForRender(entity);
        return changed;
    }

    private static boolean snapshotValidationPasses(CobblemonRenderSnapshot snapshot, ValidationResult validation) {
        boolean speciesValid = validation.valid();
        boolean aspectsValid = snapshot.aspects() == null || snapshot.aspects().isEmpty()
                || snapshot.aspects().stream().allMatch(aspect -> validation.aspects().contains(aspect) || validation.renderable().contains(aspect));
        return speciesValid && aspectsValid;
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

    private static boolean tryPokemonPropertiesText(Entity entity, ResourceLocation speciesId, String propertiesText, Class<?> propertiesClass) {
        if (propertiesClass == null) return false;
        try {
            Object props = invokeStatic(propertiesClass, "parse", propertiesText)
                    .or(() -> invokeStatic(propertiesClass, "parse", speciesId.getPath()))
                    .or(() -> invokeStatic(propertiesClass, "parse", speciesId.toString()))
                    .orElse(null);
            if (props == null) return false;
            Object pokemon = invoke(props, "create").orElse(null);
            if (pokemon == null) return false;
            return invoke(entity, "setPokemon", pokemon).isPresent() || setField(entity, "pokemon", pokemon);
        } catch (Throwable error) {
            warnOnce("Cobblemon PokemonProperties render failed for " + speciesId, error);
            return false;
        }
    }

    private static Class<?> pokemonPropertiesClass() {
        if (pokemonPropertiesClassResolved) return pokemonPropertiesClass;
        pokemonPropertiesClassResolved = true;
        for (String className : java.util.List.of("com.cobblemon.mod.common.api.pokemon.PokemonProperties", "com.cobblemon.mod.common.pokemon.PokemonProperties")) {
            try {
                pokemonPropertiesClass = Class.forName(className);
                MobFarmBlockMod.LOGGER.debug("Resolved Cobblemon PokemonProperties class once: {}", className);
                return pokemonPropertiesClass;
            } catch (Throwable error) {
                warnOnce("Cobblemon PokemonProperties class unavailable: " + className, error);
            }
        }
        return null;
    }

    private static String cleanSnapshotPropertiesText(CobblemonRenderSnapshot snapshot) {
        // The captured debug string may contain duplicate gender/aspect tokens and a
        // display-only form such as sentretnormal.  PokemonProperties is more reliable
        // when it receives the species plus simple aspects; exact form/aspects are
        // applied to the resulting Pokemon object afterward.
        StringBuilder text = new StringBuilder(snapshot.speciesId().getPath());
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        if (snapshot.aspects() != null) for (String aspect : snapshot.aspects()) if (aspect != null && !aspect.isBlank()) tokens.add(aspect.toLowerCase(java.util.Locale.ROOT));
        if (snapshot.shiny()) tokens.add("shiny");
        if (snapshot.gender() != null && !snapshot.gender().isBlank()) tokens.add(snapshot.gender().toLowerCase(java.util.Locale.ROOT));
        for (String token : tokens) text.append(' ').append(token);
        if (snapshot.level() > 0) text.append(" level=").append(snapshot.level());
        return text.toString();
    }

    private static String pokemonPropertiesText(ResourceLocation speciesId, String variantKey) {
        StringBuilder text = new StringBuilder(speciesId.getPath());
        if (variantKey != null) {
            java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
            for (String aspect : parseAspects(variantKey)) tokens.add(aspect.toLowerCase(java.util.Locale.ROOT));
            String shiny = parseVariantValue(variantKey, "shiny");
            if ("true".equalsIgnoreCase(shiny)) tokens.add("shiny");
            String gender = parseVariantValue(variantKey, "gender");
            if (!gender.isBlank()) tokens.add(gender.toLowerCase(java.util.Locale.ROOT));
            for (String token : tokens) text.append(' ').append(token);
            String level = parseVariantValue(variantKey, "level");
            if (!level.isBlank()) text.append(" level=").append(level);
        }
        return text.toString();
    }

    private static int parseVariantInt(String variantKey, String key, int fallback) {
        String text = parseVariantValue(variantKey, key);
        if (text.isBlank()) return fallback;
        try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { return fallback; }
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
        String[] classNames = {
                "com.cobblemon.mod.common.api.pokemon.PokemonSpecies",
                "com.cobblemon.mod.common.pokemon.PokemonSpecies",
                "com.cobblemon.mod.common.CobblemonSpecies",
                "com.cobblemon.mod.common.api.pokemon.CobblemonSpecies"
        };
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className);
                for (String method : new String[] {"getByIdentifier", "getById", "get", "getSpecies", "getByName", "getByPokedexNumber"}) {
                    Optional<Object> species = invokeStaticOrSingleton(type, method, speciesId)
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId.toString()))
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId.getPath()));
                    if (species.isPresent()) return species;
                }
                Optional<Object> speciesMap = readStaticOrSingletonField(type, "species").or(() -> readStaticOrSingletonField(type, "BY_IDENTIFIER"));
                Optional<Object> fromMap = speciesMap.flatMap(map -> lookupInMap(map, speciesId));
                if (fromMap.isPresent()) return fromMap;
            } catch (Throwable error) {
                warnOnce("Cobblemon species lookup failed: " + className, error);
            }
        }
        return Optional.empty();
    }

    private static void syncCobblemonEntityData(Entity entity, ResourceLocation speciesId, Object pokemon, java.util.List<String> aspects) {
        java.util.LinkedHashSet<String> aspectSet = new java.util.LinkedHashSet<>(aspects == null ? java.util.List.of() : aspects);
        setEntityData(entity, "SPECIES", speciesId.getPath());
        if (!aspectSet.isEmpty()) setEntityData(entity, "ASPECTS", aspectSet);
        invoke(entity, "refreshDimensions");
    }

    private static boolean setEntityData(Entity entity, String accessorFieldName, Object value) {
        try {
            Field accessorField = findField(entity.getClass(), accessorFieldName);
            if (accessorField == null) return false;
            accessorField.setAccessible(true);
            Object accessor = accessorField.get(null);
            if ("ASPECTS".equals(accessorFieldName) && value instanceof java.util.Collection<?> collection && !(value instanceof java.util.Set<?>)) value = new java.util.LinkedHashSet<>(collection);
            entity.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor) accessor, value);
            return true;
        } catch (Throwable error) {
            warnOnce("Cobblemon entity data sync failed: " + accessorFieldName + "=" + value, error);
            return false;
        }
    }

    private static void applyCobblemonAspects(Entity entity, Object pokemon, String variantKey) {
        java.util.List<String> aspects = parseAspects(variantKey);
        if (!aspects.isEmpty()) setCobblemonAspects(entity, pokemon, aspects);
    }

    private static boolean setCobblemonAspects(Entity entity, Object pokemon, java.util.List<String> aspects) {
        java.util.LinkedHashSet<String> aspectSet = new java.util.LinkedHashSet<>(aspects);
        boolean changed = false;
        changed |= invoke(pokemon, "setForcedAspects", aspectSet).isPresent();
        changed |= setField(pokemon, "forcedAspects", aspectSet);
        invoke(pokemon, "updateAspects");
        changed |= setField(pokemon, "aspects", aspectSet);
        changed |= invoke(entity, "setAspects", aspectSet).isPresent();
        changed |= setField(entity, "aspects", aspectSet);
        setEntityData(entity, "ASPECTS", aspectSet);
        invoke(entity, "updateAspects");
        return changed;
    }

    private static boolean setCobblemonGender(Object pokemon, String genderText) {
        boolean changed = false;
        Optional<Object> currentGender = invoke(pokemon, "getGender").or(() -> readField(pokemon, "gender"));
        if (currentGender.isPresent() && currentGender.get().getClass().isEnum()) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object gender = Enum.valueOf((Class<Enum>) currentGender.get().getClass(), genderText.toUpperCase(java.util.Locale.ROOT));
                changed |= invoke(pokemon, "setGender", gender).isPresent();
                changed |= setField(pokemon, "gender", gender);
                return changed;
            } catch (Throwable error) {
                warnOnce("Cobblemon gender enum conversion failed: " + genderText, error);
            }
        }
        changed |= invoke(pokemon, "setGender", genderText).isPresent();
        changed |= setField(pokemon, "gender", genderText);
        return changed;
    }

    private static ValidationResult validationFor(Entity entity, StoredMob stored) {
        Optional<Object> pokemon = readPokemon(entity);
        Optional<ResourceLocation> readBack = pokemon.flatMap(ClientEntityRenderCache::readPokemonSpeciesId)
                .or(() -> invoke(entity, "getExposedSpecies").flatMap(ClientEntityRenderCache::coerceResourceLocation));
        Optional<Object> aspects = pokemon.flatMap(p -> invoke(p, "getAspects").or(() -> readField(p, "aspects")))
                .or(() -> invoke(entity, "getExposedAspects"));
        Optional<Object> renderable = pokemon.flatMap(p -> invoke(p, "asRenderablePokemon"));
        boolean valid = readBack.isPresent() && readBack.get().equals(stored.speciesId);
        if (valid) MobFarmBlockMod.LOGGER.debug("Cobblemon dummy validated: storedSpecies={} readBackSpecies={} aspects={} renderable={}", stored.speciesId, readBack.get(), aspects.map(Object::toString).orElse("unavailable"), renderable.map(Object::toString).orElse("unavailable"));
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
            Method method = findMethod(target.getClass(), name, args);
            if (method == null) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target, args));
        } catch (Throwable error) { warnOnce("invoke:" + target.getClass().getName() + "." + name, error); return Optional.empty(); }
    }

    private static Optional<Object> invokeStatic(Class<?> type, String name, Object... args) {
        try {
            Method method = findMethod(type, name, args);
            if (method == null) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(null, args));
        } catch (Throwable error) { warnOnce("invokeStatic:" + type.getName() + "." + name, error); return Optional.empty(); }
    }

    private static Optional<Object> invokeStaticOrSingleton(Class<?> type, String name, Object... args) {
        Optional<Object> direct = invokeStatic(type, name, args);
        if (direct.isPresent()) return direct;
        return singletonObjects(type).stream().map(singleton -> invoke(singleton, name, args)).filter(Optional::isPresent).map(Optional::get).findFirst();
    }

    private static java.util.List<Object> singletonObjects(Class<?> type) {
        java.util.List<Object> singletons = new java.util.ArrayList<>();
        for (String fieldName : java.util.List.of("INSTANCE", "Companion")) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) singletons.add(value);
            } catch (Throwable ignored) {}
        }
        return singletons;
    }

    private static Optional<Object> readStaticOrSingletonField(Class<?> type, String name) {
        try {
            Field field = findField(type, name);
            if (field != null) {
                field.setAccessible(true);
                Object value = java.lang.reflect.Modifier.isStatic(field.getModifiers()) ? field.get(null) : null;
                if (value != null) return Optional.of(value);
            }
        } catch (Throwable error) { warnOnce("staticField:" + type.getName() + "." + name, error); }
        for (Object singleton : singletonObjects(type)) {
            Optional<Object> value = readField(singleton, name);
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    private static Optional<Object> lookupInMap(Object mapObject, ResourceLocation speciesId) {
        if (mapObject instanceof java.util.Map<?, ?> map) {
            Object value = map.get(speciesId);
            if (value == null) value = map.get(speciesId.toString());
            if (value == null) value = map.get(speciesId.getPath());
            return Optional.ofNullable(value);
        }
        return invoke(mapObject, "get", speciesId).or(() -> invoke(mapObject, "get", speciesId.toString())).or(() -> invoke(mapObject, "get", speciesId.getPath()));
    }

    private static Optional<Object> readField(Object target, String name) {
        try { Field field = findField(target.getClass(), name); if (field == null) return Optional.empty(); field.setAccessible(true); return Optional.ofNullable(field.get(target)); }
        catch (Throwable error) { warnOnce("field:" + target.getClass().getName() + "." + name, error); return Optional.empty(); }
    }

    private static boolean setField(Object target, String name, Object value) {
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
    private static void warnOnce(String message) { if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block render fallback: {}", message); }
    private static void warnOnce(String message, Throwable error) { if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block render fallback: {}: {}", message, error.toString()); }
    private ClientEntityRenderCache() {}
}

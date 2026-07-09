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
    private static final String EXISTING_POKEMON_ENTITY_STATE = "ExistingPokemonEntityState";
    private static final Map<String, Entity> CACHE = new HashMap<>();
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();
    private static final Map<String, Long> FAILED_UNTIL = new HashMap<>();
    private static final Map<String, String> SUCCESSFUL_COBBLEMON_METHODS = new HashMap<>();
    private static final java.util.Set<String> ANNOUNCED_COBBLEMON_METHODS = new java.util.HashSet<>();
    private static Class<?> pokemonPropertiesClass;
    private static boolean pokemonPropertiesClassResolved;
    private static long rebuildTick = Long.MIN_VALUE;
    private static int cobblemonRebuildsThisTick;
    private static final int MAX_COBBLEMON_REBUILDS_PER_TICK = 2;

    public static Entity getOrCreate(StoredMob stored) {
        if (stored == null || stored.isEmpty() || Minecraft.getInstance().level == null) return null;
        String key = stored.mobId + "|" + stored.speciesId + "|" + stored.display.variantKey() + "|" + stored.display.colorKey() + "|" + stored.display.baby() + "|" + snapshotHash(stored);
        long now = Minecraft.getInstance().level.getGameTime();
        if (FAILED_UNTIL.getOrDefault(key, 0L) > now) return null;
        Entity cached = CACHE.get(key);
        if (cached != null) { freezeForRender(cached); return cached; }
        if (stored.cobblemonRenderSnapshot != null && !allowCobblemonRebuild(now)) return null;
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
        if (!applyDisplay(entity, stored)) { FAILED_UNTIL.put(key, now + 40L); return null; }
        CACHE.put(key, entity);
        return entity;
    }

    private static int snapshotHash(StoredMob stored) {
        return stored.cobblemonRenderSnapshot == null ? 0 : stored.cobblemonRenderSnapshot.toNbt().toString().hashCode();
    }

    private static boolean allowCobblemonRebuild(long now) {
        if (rebuildTick != now) { rebuildTick = now; cobblemonRebuildsThisTick = 0; }
        if (cobblemonRebuildsThisTick >= MAX_COBBLEMON_REBUILDS_PER_TICK) return false;
        cobblemonRebuildsThisTick++;
        return true;
    }

    private static boolean applyDisplay(Entity entity, StoredMob stored) {
        freezeForRender(entity);
        if (entity instanceof Sheep sheep && !stored.display.colorKey().isBlank()) sheep.setColor(DyeColor.byName(stored.display.colorKey(), DyeColor.WHITE));
        if (entity instanceof AgeableMob ageable) ageable.setBaby(stored.display.baby());
        if ("cobblemon:pokemon".equals(stored.mobId.toString()) && stored.speciesId != null) {
            if (stored.cobblemonRenderSnapshot != null) {
                if (applyCobblemonSnapshot(entity, stored)) return true;
                // Do not let the newer snapshot path suppress rendering entirely. If the
                // snapshot cannot be applied in this Cobblemon runtime, fall back to the
                // legacy validated dummy strategy that is known to render older stored
                // pens. This keeps newly captured Pokemon visible while the native
                // snapshot path is refined.
                Entity fallback = BuiltInRegistries.ENTITY_TYPE.get(stored.mobId).create(Minecraft.getInstance().level);
                if (fallback == null) return false;
                freezeForRender(fallback);
                String method = applyCobblemonSpecies(fallback, stored);
                if (method == null) return false;
                copyRenderState(entity, fallback);
                return true;
            }
            String method = applyCobblemonSpecies(entity, stored);
            if (method == null) return false;
        }
        return true;
    }

    private static boolean applyCobblemonSnapshot(Entity entity, StoredMob stored) {
        CobblemonRenderSnapshot snapshot = stored.cobblemonRenderSnapshot;
        Optional<Object> pokemon = readPokemon(entity);
        if (pokemon.isEmpty()) {
            warnOnce("Cobblemon snapshot render failed because dummy has no Pokemon object: " + snapshot.speciesId());
            return false;
        }

        // First, do no harm. A freshly-created Cobblemon PokemonEntity can already
        // contain the correct render-facing Pokemon state. The legacy path checks
        // this before mutating; the snapshot path must do the same or it can corrupt
        // an already-valid dummy and make new captures render blank.
        ValidationResult existingValidation = validationFor(entity, stored);
        if (snapshotValidationPasses(snapshot, existingValidation)) {
            MobFarmBlockMod.LOGGER.debug("Cobblemon snapshot existing-state render validated for {} readBack={} aspects={} renderable={}",
                    snapshot.speciesId(), existingValidation.readBack(), existingValidation.aspects(), existingValidation.renderable());
            return true;
        }

        // Fast path: mutate the real Pokemon object that the dummy PokemonEntity already owns.
        // This uses the species/aspects captured from the live entity instead of waiting for a
        // freshly-created generic cobblemon:pokemon to randomly/existing-state match the target.
        boolean directApplied = applySnapshotToPokemonObject(entity, pokemon.get(), snapshot);
        ValidationResult directValidation = validationFor(entity, stored);
        if (directApplied && snapshotValidationPasses(snapshot, directValidation)) {
            MobFarmBlockMod.LOGGER.debug("Cobblemon snapshot direct render validated for {} readBack={} aspects={} renderable={}",
                    snapshot.speciesId(), directValidation.readBack(), directValidation.aspects(), directValidation.renderable());
            return true;
        }

        // Compatibility fallback only. Some Cobblemon versions expose PokemonProperties but not
        // mutable Pokemon fields/setters. Keep it after direct snapshot application so new captures
        // do not depend on the old strategy search.
        boolean propertiesApplied = tryPokemonPropertiesText(entity, snapshot.speciesId(), snapshot.propertiesText().isBlank() ? snapshotPropertiesText(snapshot) : snapshot.propertiesText(), pokemonPropertiesClass());
        Optional<Object> rebuiltPokemon = readPokemon(entity);
        rebuiltPokemon.ifPresent(value -> {
            applySnapshotToPokemonObject(entity, value, snapshot);
            syncCobblemonEntityData(entity, snapshot.speciesId(), value, snapshot.aspects());
        });
        ValidationResult propertiesValidation = validationFor(entity, stored);
        boolean valid = propertiesApplied && snapshotValidationPasses(snapshot, propertiesValidation);
        if (!valid) MobFarmBlockMod.LOGGER.info("Mob Farm Block Debug: Cobblemon snapshot render failed for {} directApplied={} directValid={} propertiesApplied={} propertiesValid={} readBack={} aspects={} expectedAspects={} renderable={} capturedRenderable={} payloadFormat={}",
                snapshot.speciesId(), directApplied, directValidation.valid(), propertiesApplied, propertiesValidation.valid(), propertiesValidation.readBack(), propertiesValidation.aspects(), snapshot.aspects(), propertiesValidation.renderable(), snapshot.renderableDebug(), snapshot.pokemonPayloadFormat());
        else MobFarmBlockMod.LOGGER.debug("Cobblemon snapshot properties render validated for {} readBack={} aspects={} payloadFormat={}",
                snapshot.speciesId(), propertiesValidation.readBack(), propertiesValidation.aspects(), snapshot.pokemonPayloadFormat());
        return valid;
    }

    private static boolean applySnapshotToPokemonObject(Entity entity, Object pokemon, CobblemonRenderSnapshot snapshot) {
        boolean changed = false;
        Optional<Object> species = findSpeciesObject(snapshot.speciesId());
        if (species.isPresent()) {
            changed |= invoke(pokemon, "setSpecies", species.get()).isPresent();
            changed |= setField(pokemon, "species", species.get());
            changed |= invoke(entity, "setSpecies", species.get()).isPresent();
            changed |= setField(entity, "species", species.get());
        }
        if (snapshot.aspects() != null && !snapshot.aspects().isEmpty()) {
            changed |= setCobblemonAspects(entity, pokemon, snapshot.aspects());
        }
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
            setEntityData(entity, "SCALE_MODIFIER", snapshot.scaleModifier());
        }
        syncCobblemonEntityData(entity, snapshot.speciesId(), pokemon, snapshot.aspects() == null ? java.util.List.of() : snapshot.aspects());
        invoke(entity, "refreshDimensions");
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

    private static void copyRenderState(Entity target, Entity source) {
        // We intentionally copy only the Cobblemon Pokemon/render-facing state and
        // basic visual state. The cache keeps the original target object, so callers
        // do not need a second getOrCreate path.
        Optional<Object> sourcePokemon = readPokemon(source);
        if (sourcePokemon.isPresent()) {
            invoke(target, "setPokemon", sourcePokemon.get());
            setField(target, "pokemon", sourcePokemon.get());
        }
        target.getEntityData().assignValues(source.getEntityData().getNonDefaultValues());
        target.refreshDimensions();
        freezeForRender(target);
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
        if (method.startsWith("PokemonProperties:")) {
            boolean applied = tryPokemonPropertiesClass(entity, stored.speciesId, stored.display.variantKey(), method.substring("PokemonProperties:".length()));
            if (applied) readPokemon(entity).ifPresent(pokemon -> {
                applyCobblemonAspects(entity, pokemon, stored.display.variantKey());
                syncCobblemonEntityData(entity, stored.speciesId, pokemon, stored.display.variantKey());
            });
            return applied;
        }
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
        return tryPokemonPropertiesText(entity, speciesId, pokemonPropertiesText(speciesId, variantKey), className);
    }

    private static boolean tryPokemonPropertiesText(Entity entity, ResourceLocation speciesId, String propertiesText, String className) {
        try {
            return tryPokemonPropertiesText(entity, speciesId, propertiesText, Class.forName(className));
        } catch (Throwable error) {
            warnOnce("Cobblemon PokemonProperties failed: " + className, error);
            return false;
        }
    }

    private static boolean tryPokemonPropertiesText(Entity entity, ResourceLocation speciesId, String propertiesText, Class<?> propertiesClass) {
        if (propertiesClass == null) return false;
        Object props = invokeStatic(propertiesClass, "parse", propertiesText).or(() -> invokeStatic(propertiesClass, "parse", speciesId.getPath())).or(() -> invokeStatic(propertiesClass, "parse", speciesId.toString())).orElse(null);
        if (props == null) return false;
        Object pokemon = invoke(props, "create").orElse(null);
        if (pokemon == null) return false;
        return invoke(entity, "setPokemon", pokemon).isPresent() || setField(entity, "pokemon", pokemon);
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

    private static String snapshotPropertiesText(CobblemonRenderSnapshot snapshot) {
        StringBuilder text = new StringBuilder(snapshot.speciesId().getPath());
        if (snapshot.aspects() != null) for (String aspect : snapshot.aspects()) if (aspect != null && !aspect.isBlank()) text.append(' ').append(aspect);
        if (snapshot.form() != null && !snapshot.form().isBlank()) text.append(' ').append(snapshot.form());
        if (snapshot.level() > 0) text.append(" level=").append(snapshot.level());
        if (snapshot.shiny()) text.append(" shiny");
        if (snapshot.gender() != null && !snapshot.gender().isBlank()) text.append(' ').append(snapshot.gender().toLowerCase(java.util.Locale.ROOT));
        return text.toString();
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

    private static void syncCobblemonEntityData(Entity entity, ResourceLocation speciesId, Object pokemon, String variantKey) {
        syncCobblemonEntityData(entity, speciesId, pokemon, parseAspects(variantKey));
    }

    private static void syncCobblemonEntityData(Entity entity, ResourceLocation speciesId, Object pokemon, java.util.List<String> aspects) {
        Optional<Object> species = readField(pokemon, "species").or(() -> invoke(pokemon, "getSpecies"));
        setEntityData(entity, "SPECIES", species.orElse(speciesId));
        setEntityData(entity, "SPECIES", speciesId);
        setEntityData(entity, "SPECIES", speciesId.getPath());
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
        setCobblemonAspects(entity, pokemon, aspects);
    }

    private static boolean setCobblemonAspects(Entity entity, Object pokemon, java.util.List<String> aspects) {
        java.util.LinkedHashSet<String> aspectSet = new java.util.LinkedHashSet<>(aspects);
        boolean changed = false;
        changed |= invoke(pokemon, "setAspects", aspectSet).isPresent();
        changed |= invoke(pokemon, "setAspects", aspects).isPresent();
        changed |= setField(pokemon, "aspects", aspectSet) || setField(pokemon, "aspects", aspects);
        changed |= invoke(entity, "setAspects", aspectSet).isPresent();
        changed |= invoke(entity, "setAspects", aspects).isPresent();
        changed |= setField(entity, "aspects", aspectSet) || setField(entity, "aspects", aspects);
        invoke(pokemon, "updateAspects");
        invoke(entity, "updateAspects");
        return changed;
    }

    private static boolean setCobblemonGender(Object pokemon, String genderText) {
        boolean changed = false;
        changed |= invoke(pokemon, "setGender", genderText).isPresent();
        changed |= setField(pokemon, "gender", genderText);
        Optional<Object> currentGender = invoke(pokemon, "getGender").or(() -> readField(pokemon, "gender"));
        if (currentGender.isPresent() && currentGender.get().getClass().isEnum()) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object gender = Enum.valueOf((Class<Enum>) currentGender.get().getClass(), genderText.toUpperCase(java.util.Locale.ROOT));
                changed |= invoke(pokemon, "setGender", gender).isPresent();
                changed |= setField(pokemon, "gender", gender);
            } catch (Throwable error) {
                warnOnce("Cobblemon gender enum conversion failed: " + genderText, error);
            }
        }
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
        Method fallback = null;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                if (parametersCompatible(m.getParameterTypes(), args)) return m;
                if (fallback == null) fallback = m;
            }
        }
        return fallback;
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

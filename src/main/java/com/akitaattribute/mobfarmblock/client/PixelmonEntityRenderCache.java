package com.akitaattribute.mobfarmblock.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.MobKind;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Client-only Pixelmon render reconstruction. */
public final class PixelmonEntityRenderCache {
    private static final Map<String, Entity> CACHE = new HashMap<>();
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    public static Entity getOrCreate(StoredMob stored) {
        Minecraft minecraft = Minecraft.getInstance();
        if (stored == null || stored.isEmpty() || minecraft.level == null || !isPixelmonStored(stored) || stored.speciesId == null) return null;
        String key = stored.mobId + "|" + stored.speciesId + "|" + stored.display.variantKey() + "|" + stored.display.scale() + "|" + payloadHash(stored.pixelmonRenderSnapshot);
        Entity cached = CACHE.get(key);
        if (cached != null) {
            ClientEntityRenderCache.freezeForRender(cached);
            return cached;
        }

        try {
            Optional<Object> species = findPixelmonSpeciesObject(stored.speciesId);
            Optional<Object> pokemon = createPokemon(stored.speciesId, species.orElse(null));
            if (pokemon.isEmpty()) pokemon = constructPokemon(stored.speciesId, species.orElse(null));
            if (pokemon.isEmpty()) {
                Entity shell = createEmptyPixelmonEntity(stored);
                if (shell != null) pokemon = readPokemon(shell);
            }
            if (pokemon.isEmpty()) {
                warnOnce("Pixelmon Pokemon object could not be created: " + stored.speciesId);
                return null;
            }

            Object pokemonObject = pokemon.get();
            if (species.isPresent()) applySpeciesToPokemon(pokemonObject, species.get(), stored.speciesId);
            applyVariantHints(pokemonObject, species.orElse(null), stored.display.variantKey());
            invoke(pokemonObject, "setSize", (double) Math.max(0.1F, stored.display.scale()));
            for (String method : java.util.List.of("initialize", "updateForm", "updatePalette", "updateStats", "recalculateStats")) invoke(pokemonObject, method);

            Entity entity = createEntityFromPokemon(pokemonObject, minecraft.level);
            if (entity == null) {
                entity = createEmptyPixelmonEntity(stored);
                if (entity != null) {
                    bindPokemonToEntity(pokemonObject, entity);
                    installPokemonOnEntity(entity, pokemonObject);
                    refreshPixelmonRenderState(entity, pokemonObject);
                }
            }
            if (entity == null) return null;

            Optional<ResourceLocation> readBack = readPokemon(entity).flatMap(PixelmonEntityRenderCache::readPokemonSpeciesId).or(() -> readPokemonSpeciesId(pokemonObject));
            if (readBack.isEmpty() || !readBack.get().equals(stored.speciesId)) {
                MobFarmBlockMod.LOGGER.info("Mob Farm Block Debug: Pixelmon 3D render unresolved for storedSpecies={} readBack={} variant={}", stored.speciesId, readBack.map(Object::toString).orElse("unavailable"), stored.display.variantKey());
                return null;
            }
            if (!isSafeRenderable(entity, stored)) return null;
            ClientEntityRenderCache.freezeForRender(entity);
            CACHE.put(key, entity);
            return entity;
        } catch (Throwable error) {
            warnOnce("Pixelmon render entity creation failed for " + key, error);
            return null;
        }
    }

    public static boolean isPixelmonStored(StoredMob stored) {
        return stored != null && stored.kind == MobKind.PIXELMON && "pixelmon:pixelmon".equals(stored.mobId.toString());
    }

    private static Entity createEntityFromPokemon(Object pokemon, Level level) {
        for (Object[] args : java.util.List.<Object[]>of(
                new Object[] { level, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F },
                new Object[] { level, 0.0D, 0.0D, 0.0D },
                new Object[] {}
        )) {
            Optional<Object> value = invoke(pokemon, "getOrCreatePixelmon", args);
            if (value.isPresent() && value.get() instanceof Entity entity) return entity;
        }
        return null;
    }

    private static Entity createEmptyPixelmonEntity(StoredMob stored) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(stored.mobId);
        if (type == null || type == EntityType.PIG && !stored.mobId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG))) return null;
        return type.create(Minecraft.getInstance().level);
    }

    private static boolean isSafeRenderable(Entity entity, StoredMob stored) {
        try {
            Object pokemon = readPokemon(entity).orElse(null);
            if (pokemon == null) return false;
            String formName = parseVariantValue(stored.display.variantKey(), "form");
            if (formName.isBlank()) formName = "base";
            if (invoke(pokemon, "getForm").isEmpty() && invoke(entity, "getForm").isEmpty()) return false;
            if (invoke(pokemon, "getPalette").isEmpty() && invoke(entity, "getPalette").isEmpty()) return false;
            setField(pokemon, "formName", formName);
            setField(entity, "formName", formName);
            return true;
        } catch (Throwable error) {
            warnOnce("Pixelmon render validation failed for " + stored.speciesId, error);
            return false;
        }
    }

    private static boolean applySpeciesToPokemon(Object pokemon, Object species, ResourceLocation speciesId) {
        boolean changed = false;
        Optional<Object> registryValue = invoke(species, "getRegistryValue").or(() -> readField(species, "registryValue"));
        changed |= invoke(pokemon, "setSpecies", species).isPresent();
        if (registryValue.isPresent()) changed |= invoke(pokemon, "setSpecies", registryValue.get()).isPresent();
        changed |= invoke(pokemon, "setSpecies", titleCase(speciesId.getPath())).isPresent();
        changed |= invoke(pokemon, "setSpecies", speciesId.getPath()).isPresent();
        changed |= invoke(pokemon, "setSpecies", speciesId.toString()).isPresent();
        changed |= setField(pokemon, "species", species);
        if (registryValue.isPresent()) changed |= setField(pokemon, "speciesValue", registryValue.get());
        changed |= setField(pokemon, "speciesValue", species);
        return changed;
    }

    private static boolean applyVariantHints(Object pokemon, Object species, String variantKey) {
        boolean changed = false;
        String formName = parseVariantValue(variantKey, "form");
        if (formName.isBlank()) formName = "base";
        String paletteName = parseVariantValue(variantKey, "palette");
        if (paletteName.isBlank()) paletteName = "none";
        String shiny = parseVariantValue(variantKey, "shiny");
        if (!shiny.isBlank()) {
            boolean value = Boolean.parseBoolean(shiny);
            changed |= invoke(pokemon, "setShiny", value).isPresent();
            changed |= setField(pokemon, "shiny", value);
        }
        String gender = parseVariantValue(variantKey, "gender");
        if (!gender.isBlank()) changed |= setEnumOrString(pokemon, "gender", "setGender", gender);

        Optional<Object> form = resolveFormObject(species, formName);
        if (form.isPresent()) {
            changed |= invoke(pokemon, "setForm", form.get()).isPresent();
            changed |= setField(pokemon, "form", form.get());
        }
        changed |= invoke(pokemon, "setForm", formName).isPresent();
        changed |= setField(pokemon, "formName", formName);

        Optional<Object> palette = resolvePaletteObject(pokemon, form.orElse(null), species, paletteName);
        if (palette.isPresent()) {
            changed |= invoke(pokemon, "setPalette", palette.get()).isPresent();
            changed |= setField(pokemon, "palette", palette.get());
        }
        changed |= invoke(pokemon, "setPalette", paletteName).isPresent();
        changed |= setField(pokemon, "paletteName", paletteName);
        return changed;
    }

    private static Optional<Object> resolveFormObject(Object species, String formName) {
        if (species == null) return Optional.empty();
        String wanted = formName == null || formName.isBlank() ? "base" : formName;
        Optional<Object> direct = invoke(species, "getForm", wanted).or(() -> invoke(species, "getDefaultForm")).or(() -> invoke(species, "getFirstForm"));
        if (direct.isPresent() && formMatches(direct.get(), wanted)) return direct;
        Optional<Object> forms = invoke(species, "getForms").or(() -> readField(species, "forms"));
        for (Object form : iterable(forms.orElse(null))) if (formMatches(form, wanted)) return Optional.of(form);
        return direct;
    }

    private static boolean formMatches(Object form, String wanted) {
        if (form == null) return false;
        String name = invoke(form, "getName").or(() -> readField(form, "name")).map(String::valueOf).orElse(String.valueOf(form));
        return name.equalsIgnoreCase(wanted) || name.toLowerCase(java.util.Locale.ROOT).contains(wanted.toLowerCase(java.util.Locale.ROOT));
    }

    private static Optional<Object> resolvePaletteObject(Object pokemon, Object form, Object species, String paletteName) {
        Optional<Object> current = invoke(pokemon, "getPalette");
        if (current.isPresent() && paletteMatches(current.get(), paletteName)) return current;
        for (Object source : java.util.List.of(form, species)) {
            if (source == null) continue;
            Optional<Object> direct = invoke(source, "getPalette", paletteName).or(() -> invoke(source, "getFirstPaletteProperties")).or(() -> invoke(source, "getDefaultPalette"));
            if (direct.isPresent() && paletteMatches(direct.get(), paletteName)) return direct;
            for (String method : java.util.List.of("getGenderProperties", "getDefaultGenderProperties", "getFirstGenderProperties")) {
                for (Object gp : iterable(invoke(source, method).orElse(null))) {
                    Optional<Object> palette = invoke(gp, "getDefaultPalette").or(() -> invoke(gp, "getRandomPalette"));
                    if (palette.isPresent() && paletteMatches(palette.get(), paletteName)) return palette;
                    for (Object p : iterable(invoke(gp, "getPalettes").orElse(null))) if (paletteMatches(p, paletteName)) return Optional.of(p);
                }
            }
        }
        return current;
    }

    private static boolean paletteMatches(Object palette, String wanted) {
        if (palette == null) return false;
        String name = invoke(palette, "getName").or(() -> readField(palette, "name")).map(String::valueOf).orElse(String.valueOf(palette));
        return wanted == null || wanted.isBlank() || name.equalsIgnoreCase(wanted) || name.toLowerCase(java.util.Locale.ROOT).contains(wanted.toLowerCase(java.util.Locale.ROOT));
    }

    private static java.util.List<Object> iterable(Object value) {
        if (value == null) return java.util.List.of();
        java.util.ArrayList<Object> out = new java.util.ArrayList<>();
        if (value instanceof java.util.Map<?, ?> map) out.addAll(map.values());
        else if (value instanceof Iterable<?> iterable) for (Object item : iterable) out.add(item);
        else if (value.getClass().isArray()) for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) out.add(java.lang.reflect.Array.get(value, i));
        else out.add(value);
        return out;
    }

    private static boolean bindPokemonToEntity(Object pokemon, Entity entity) {
        boolean changed = false;
        changed |= invoke(pokemon, "setEntity", entity).isPresent();
        changed |= invoke(pokemon, "setPixelmonEntity", entity).isPresent();
        changed |= invoke(pokemon, "setEntityID", entity.getId()).isPresent();
        changed |= setField(pokemon, "entity", entity);
        changed |= setField(pokemon, "pixelmonEntity", entity);
        changed |= setField(pokemon, "entityID", entity.getId());
        return changed;
    }

    private static boolean installPokemonOnEntity(Entity entity, Object pokemon) {
        boolean changed = false;
        changed |= invoke(entity, "setPokemon", pokemon).isPresent();
        changed |= invoke(entity, "setPixelmon", pokemon).isPresent();
        changed |= invoke(entity, "setPokemonData", pokemon).isPresent();
        changed |= invoke(entity, "updatePokemon", pokemon).isPresent();
        changed |= setField(entity, "pokemon", pokemon);
        changed |= setField(entity, "pixelmon", pokemon);
        changed |= setField(entity, "pokemonData", pokemon);
        return changed;
    }

    private static boolean refreshPixelmonRenderState(Entity entity, Object pokemon) {
        boolean changed = false;
        for (String method : java.util.List.of("updatePokemon", "updatePokemonData", "updatePixelmon", "refreshPokemon", "refreshDimensions", "recalculateSize", "updateSize", "updateModel", "reloadModel")) changed |= invoke(entity, method).isPresent();
        readField(entity, "delegate").ifPresent(delegate -> { invoke(delegate, "changePokemon", pokemon); invoke(delegate, "setPokemon", pokemon); invoke(delegate, "updatePokemon", pokemon); });
        return changed;
    }

    private static boolean setEnumOrString(Object target, String fieldName, String setter, String text) {
        boolean changed = false;
        Optional<Object> current = readField(target, fieldName).or(() -> invoke(target, "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1)));
        if (current.isPresent() && current.get().getClass().isEnum()) {
            try {
                @SuppressWarnings({"rawtypes", "unchecked"}) Object enumValue = Enum.valueOf((Class<Enum>) current.get().getClass(), text.toUpperCase(java.util.Locale.ROOT));
                changed |= invoke(target, setter, enumValue).isPresent();
                changed |= setField(target, fieldName, enumValue);
                return changed;
            } catch (Throwable error) { warnOnce("Pixelmon enum conversion failed: " + fieldName + "=" + text, error); }
        }
        changed |= invoke(target, setter, text).isPresent();
        changed |= setField(target, fieldName, text);
        return changed;
    }

    private static Optional<Object> createPokemon(ResourceLocation speciesId, Object speciesObject) {
        for (String className : java.util.List.of("com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory", "com.pixelmonmod.pixelmon.api.pokemon.PokemonBuilder", "com.pixelmonmod.pixelmon.api.pokemon.PokemonRegistry")) {
            try {
                Class<?> type = Class.forName(className);
                for (String method : new String[] {"create", "createPokemon", "createFromSpecies", "fromSpecies", "get"}) for (Object candidate : speciesCandidates(speciesId, speciesObject)) {
                    Optional<Object> created = invokeStaticOrSingleton(type, method, candidate);
                    if (created.isPresent()) return created;
                }
            } catch (Throwable error) { warnOnce("Pixelmon Pokemon factory unavailable: " + className, error); }
        }
        return Optional.empty();
    }

    private static Optional<Object> constructPokemon(ResourceLocation speciesId, Object speciesObject) {
        try {
            Class<?> type = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon");
            for (Object candidate : speciesCandidates(speciesId, speciesObject)) {
                Optional<Object> created = construct(type, candidate);
                if (created.isPresent()) return created;
            }
            Optional<Object> noArg = construct(type);
            noArg.ifPresent(pokemon -> speciesObjectOptional(speciesObject).ifPresent(species -> applySpeciesToPokemon(pokemon, species, speciesId)));
            return noArg;
        } catch (Throwable error) { warnOnce("Pixelmon Pokemon constructor unavailable", error); return Optional.empty(); }
    }

    private static java.util.List<Object> speciesCandidates(ResourceLocation speciesId, Object speciesObject) {
        java.util.ArrayList<Object> candidates = new java.util.ArrayList<>();
        if (speciesObject != null) { candidates.add(speciesObject); invoke(speciesObject, "getRegistryValue").ifPresent(candidates::add); }
        candidates.add(titleCase(speciesId.getPath())); candidates.add(speciesId.getPath()); candidates.add(speciesId.toString()); candidates.add(speciesId);
        return candidates;
    }

    private static Optional<Object> speciesObjectOptional(Object speciesObject) { return speciesObject == null ? Optional.empty() : Optional.of(speciesObject); }

    private static Optional<Object> findPixelmonSpeciesObject(ResourceLocation speciesId) {
        for (String className : java.util.List.of("com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies", "com.pixelmonmod.pixelmon.api.pokemon.species.Species", "com.pixelmonmod.pixelmon.api.pokemon.Species")) {
            try {
                Class<?> type = Class.forName(className);
                for (String method : new String[] {"get", "getFromName", "fromName", "getSpecies", "getByName", "getById", "getByIdentifier", "getValue"}) {
                    Optional<Object> species = invokeStaticOrSingleton(type, method, titleCase(speciesId.getPath())).or(() -> invokeStaticOrSingleton(type, method, speciesId.getPath())).or(() -> invokeStaticOrSingleton(type, method, speciesId.toString())).or(() -> invokeStaticOrSingleton(type, method, speciesId));
                    if (species.isPresent()) return unwrapSpecies(species.get());
                }
                Optional<Object> fieldSpecies = readStaticField(type, speciesId.getPath().toUpperCase(java.util.Locale.ROOT)).or(() -> readStaticField(type, titleCase(speciesId.getPath()).toUpperCase(java.util.Locale.ROOT)));
                if (fieldSpecies.isPresent()) return unwrapSpecies(fieldSpecies.get());
            } catch (Throwable error) { warnOnce("Pixelmon species lookup failed: " + className, error); }
        }
        return Optional.empty();
    }

    private static Optional<Object> unwrapSpecies(Object value) { if (value == null) return Optional.empty(); return invoke(value, "getValue").or(() -> invoke(value, "getSpecies")).or(() -> Optional.of(value)); }
    private static Optional<ResourceLocation> readPokemonSpeciesId(Object pokemon) { return invoke(pokemon, "getSpecies").or(() -> readField(pokemon, "species")).or(() -> readField(pokemon, "speciesValue")).flatMap(PixelmonEntityRenderCache::coercePixelmonId); }

    private static Optional<ResourceLocation> coercePixelmonId(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof ResourceLocation id) return Optional.of(ResourceLocation.fromNamespaceAndPath("pixelmon", sanitizePath(id.getPath()))).filter(id2 -> !looksBadId(id2));
        Optional<String> name = speciesName(value);
        if (name.isPresent()) return parsePixelmonName(name.get());
        Optional<Object> nested = invoke(value, "getStrippedName").or(() -> invoke(value, "getName")).or(() -> invoke(value, "getPokemonName")).or(() -> invoke(value, "getLocalizedName")).or(() -> readField(value, "name"))
                .or(() -> invoke(value, "getRegistryName")).or(() -> invoke(value, "getResourceLocation")).or(() -> invoke(value, "getResourceIdentifier")).or(() -> invoke(value, "getIdentifier")).or(() -> invoke(value, "getId")).or(() -> readField(value, "registryName"));
        if (nested.isPresent() && nested.get() != value) return coercePixelmonId(nested.get());
        return parsePixelmonName(String.valueOf(value));
    }

    private static Optional<String> speciesName(Object value) {
        if (value == null) return Optional.empty();
        for (String method : new String[] {"getStrippedName", "getName", "getPokemonName", "getLocalizedName"}) {
            Optional<Object> result = invoke(value, method); if (result.isPresent()) { Optional<String> text = textName(result.get()); if (text.isPresent()) return text; }
        }
        for (String field : new String[] {"strippedName", "name", "pokemonName"}) {
            Optional<Object> result = readField(value, field); if (result.isPresent()) { Optional<String> text = textName(result.get()); if (text.isPresent()) return text; }
        }
        return nameFromToString(String.valueOf(value));
    }

    private static Optional<String> textName(Object value) { if (value == null) return Optional.empty(); String text = String.valueOf(value).trim(); if (text.isBlank() || text.matches("\\d+")) return Optional.empty(); if (text.contains("{")) return nameFromToString(text); if (text.contains("@") && text.contains(".")) return Optional.empty(); return Optional.of(text); }
    private static Optional<String> nameFromToString(String text) { if (text == null) return Optional.empty(); java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:name|Name)=([A-Za-z0-9_ .-]+)").matcher(text); if (matcher.find()) return Optional.of(matcher.group(1).trim()); return Optional.empty(); }
    private static Optional<ResourceLocation> parsePixelmonName(String raw) { if (raw == null) return Optional.empty(); String text = raw.trim(); Optional<String> named = nameFromToString(text); if (named.isPresent()) text = named.get(); if (text.isBlank() || text.contains(".") || text.matches("\\d+")) return Optional.empty(); int colon = text.lastIndexOf(':'); try { ResourceLocation id = colon > 0 && colon < text.length() - 1 ? ResourceLocation.parse(sanitizeId(text)) : ResourceLocation.fromNamespaceAndPath("pixelmon", sanitizePath(text)); return looksBadId(id) ? Optional.empty() : Optional.of(ResourceLocation.fromNamespaceAndPath("pixelmon", sanitizePath(id.getPath()))); } catch (Throwable ignored) { return Optional.empty(); } }
    private static boolean looksBadId(ResourceLocation id) { return id.getPath().contains("com.") || id.getPath().contains("pixelmonmod") || id.getPath().matches("\\d+"); }
    private static Optional<Object> readPokemon(Entity entity) { return invoke(entity, "getPokemon").or(() -> invoke(entity, "pokemon")).or(() -> invoke(entity, "getPixelmon")).or(() -> readField(entity, "pokemon")).or(() -> readField(entity, "pixelmon")).or(() -> readField(entity, "pokemonData")); }
    private static String parseVariantValue(String variantKey, String key) { if (variantKey == null) return ""; for (String part : variantKey.split("\\|")) { int equals = part.indexOf('='); if (equals > 0 && part.substring(0, equals).equals(key)) return part.substring(equals + 1); } return ""; }
    private static int payloadHash(PixelmonRenderSnapshot snapshot) { return snapshot == null || snapshot.payload() == null ? 0 : snapshot.payload().hashCode(); }

    private static Optional<Object> invoke(Object target, String name, Object... args) { if (target == null) return Optional.empty(); try { Method method = findMethod(target.getClass(), name, args); if (method == null) return Optional.empty(); method.setAccessible(true); return Optional.ofNullable(method.invoke(target, args)); } catch (Throwable error) { warnOnce("invoke:" + target.getClass().getName() + "." + name, error); return Optional.empty(); } }
    private static Optional<Object> invokeStatic(Class<?> type, String name, Object... args) { try { Method method = findMethod(type, name, args); if (method == null || !Modifier.isStatic(method.getModifiers())) return Optional.empty(); method.setAccessible(true); return Optional.ofNullable(method.invoke(null, args)); } catch (Throwable error) { warnOnce("invokeStatic:" + type.getName() + "." + name, error); return Optional.empty(); } }
    private static Optional<Object> invokeStaticOrSingleton(Class<?> type, String name, Object... args) { Optional<Object> direct = invokeStatic(type, name, args); if (direct.isPresent()) return direct; for (Object singleton : singletonObjects(type)) { Optional<Object> value = invoke(singleton, name, args); if (value.isPresent()) return value; } return Optional.empty(); }
    private static Optional<Object> construct(Class<?> type, Object... args) { try { Constructor<?> constructor = findConstructor(type, args); if (constructor == null) return Optional.empty(); constructor.setAccessible(true); return Optional.ofNullable(constructor.newInstance(args)); } catch (Throwable error) { warnOnce("construct:" + type.getName(), error); return Optional.empty(); } }
    private static java.util.List<Object> singletonObjects(Class<?> type) { java.util.List<Object> singletons = new java.util.ArrayList<>(); for (String fieldName : java.util.List.of("INSTANCE", "Companion")) { try { Field field = type.getDeclaredField(fieldName); field.setAccessible(true); Object value = field.get(null); if (value != null) singletons.add(value); } catch (Throwable ignored) {} } return singletons; }
    private static Optional<Object> readStaticField(Class<?> type, String name) { try { Field field = findField(type, name); if (field == null || !Modifier.isStatic(field.getModifiers())) return Optional.empty(); field.setAccessible(true); return Optional.ofNullable(field.get(null)); } catch (Throwable error) { warnOnce("staticField:" + type.getName() + "." + name, error); return Optional.empty(); } }
    private static Optional<Object> readField(Object target, String name) { if (target == null) return Optional.empty(); try { Field field = findField(target.getClass(), name); if (field == null) return Optional.empty(); field.setAccessible(true); return Optional.ofNullable(field.get(target)); } catch (Throwable error) { warnOnce("field:" + target.getClass().getName() + "." + name, error); return Optional.empty(); } }
    private static boolean setField(Object target, String name, Object value) { if (target == null) return false; try { Field field = findField(target.getClass(), name); if (field == null) return false; field.setAccessible(true); field.set(target, value); return true; } catch (Throwable error) { warnOnce("setField:" + target.getClass().getName() + "." + name, error); return false; } }
    private static Method findMethod(Class<?> type, String name, Object... args) { for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == args.length && parametersCompatible(m.getParameterTypes(), args)) return m; return null; }
    private static Constructor<?> findConstructor(Class<?> type, Object... args) { for (Constructor<?> constructor : type.getDeclaredConstructors()) if (constructor.getParameterCount() == args.length && parametersCompatible(constructor.getParameterTypes(), args)) return constructor; return null; }
    private static boolean parametersCompatible(Class<?>[] parameterTypes, Object[] args) { for (int i = 0; i < parameterTypes.length; i++) { if (args[i] == null) continue; Class<?> parameter = wrapPrimitive(parameterTypes[i]); Class<?> actual = wrapPrimitive(args[i].getClass()); if (!parameter.isAssignableFrom(actual)) return false; } return true; }
    private static Class<?> wrapPrimitive(Class<?> type) { if (!type.isPrimitive()) return type; if (type == int.class) return Integer.class; if (type == long.class) return Long.class; if (type == float.class) return Float.class; if (type == double.class) return Double.class; if (type == boolean.class) return Boolean.class; if (type == byte.class) return Byte.class; if (type == short.class) return Short.class; if (type == char.class) return Character.class; return type; }
    private static Field findField(Class<?> type, String name) { for (Class<?> c = type; c != null; c = c.getSuperclass()) try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {} return null; }
    private static String titleCase(String text) { if (text == null || text.isBlank()) return text; return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(java.util.Locale.ROOT); }
    private static String sanitizeId(String text) { String[] parts = text.split(":", 2); return parts.length == 2 ? parts[0].toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "") + ":" + sanitizePath(parts[1]) : sanitizePath(text); }
    private static String sanitizePath(String text) { return text.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_/.-]", ""); }
    private static void warnOnce(String message) { if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon render: {}", message); }
    private static void warnOnce(String message, Throwable error) { if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon render: {}: {}", message, error.toString()); }
    private PixelmonEntityRenderCache() {}
}

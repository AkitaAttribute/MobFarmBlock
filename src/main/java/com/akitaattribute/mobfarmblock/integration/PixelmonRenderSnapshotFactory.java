package com.akitaattribute.mobfarmblock.integration;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Captures Pixelmon's live entity/Pokemon state before the source entity is removed. */
public final class PixelmonRenderSnapshotFactory {
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    public static Optional<PixelmonRenderSnapshot> capture(Entity entity) {
        if (!PixelmonIntegration.isPokemonEntity(entity)) return Optional.empty();
        Optional<ResourceLocation> species = PixelmonIntegration.getSpeciesId(entity);
        if (species.isEmpty()) return Optional.empty();

        Payload payload = entityPayload(entity).orElseGet(() -> PixelmonIntegration.getPokemonObject(entity).map(PixelmonRenderSnapshotFactory::pokemonPayload).orElse(new Payload("", "")));
        return Optional.of(new PixelmonRenderSnapshot(
                species.get(),
                PixelmonIntegration.getDisplayKey(entity).orElse(species.get().toString()),
                payload.format(),
                payload.value(),
                Math.max(0.0F, entity.getBbWidth()),
                Math.max(0.0F, entity.getBbHeight()),
                sizeCentimeters(entity)
        ));
    }

    private static float sizeCentimeters(Entity entity) {
        Optional<Object> pokemon = PixelmonIntegration.getPokemonObject(entity);
        if (pokemon.isPresent()) {
            for (String method : new String[] {"getSize", "size", "getActualSize", "getSizeInCm", "getSizeCM", "getHeight", "height"}) {
                Optional<Float> value = PixelmonIntegration.reflectNoArg(pokemon.get(), method).flatMap(PixelmonRenderSnapshotFactory::coerceCentimeters);
                if (value.isPresent()) return value.get();
            }
            for (String field : new String[] {"size", "actualSize", "sizeCm", "sizeCM", "height"}) {
                Optional<Float> value = PixelmonIntegration.readField(pokemon.get(), field).flatMap(PixelmonRenderSnapshotFactory::coerceCentimeters);
                if (value.isPresent()) return value.get();
            }
        }
        for (String method : new String[] {"getSize", "getActualSize", "getSizeInCm", "getSizeCM", "getHeight"}) {
            Optional<Float> value = PixelmonIntegration.reflectNoArg(entity, method).flatMap(PixelmonRenderSnapshotFactory::coerceCentimeters);
            if (value.isPresent()) return value.get();
        }
        return 0.0F;
    }

    private static Optional<Float> coerceCentimeters(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof Number number) return normalizeSizeNumber(number.floatValue(), false);
        String text = String.valueOf(value).trim();
        if (text.isBlank()) return Optional.empty();
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) return Optional.empty();
        try {
            float number = Float.parseFloat(matcher.group());
            String lower = text.toLowerCase(java.util.Locale.ROOT);
            return normalizeSizeNumber(number, lower.contains("cm"));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Float> normalizeSizeNumber(float value, boolean explicitCentimeters) {
        if (!Float.isFinite(value) || value <= 0.0F) return Optional.empty();
        if (explicitCentimeters) return Optional.of(value);
        // Pixelmon's UI displays centimeters, but API values may be stored as meters.
        return Optional.of(value <= 10.0F ? value * 100.0F : value);
    }

    private static Optional<Payload> entityPayload(Entity entity) {
        try {
            CompoundTag tag = new CompoundTag();
            entity.saveWithoutId(tag);
            stripRuntimeEntityState(tag);
            return Optional.of(new Payload("entity:saveWithoutId", tag.toString()));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static void stripRuntimeEntityState(CompoundTag tag) {
        for (String key : new String[] {
                "UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround",
                "Invulnerable", "PortalCooldown", "TicksFrozen", "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount",
                "Passengers", "Leash", "LeftHanded", "NoGravity", "Silent", "Glowing", "Tags", "Brain", "HandItems", "ArmorItems"
        }) tag.remove(key);
        // Preserve Pixelmon/Pokemon-owned fields; strip only generic Minecraft runtime state.
    }

    private static Payload pokemonPayload(Object pokemon) {
        for (String method : new String[] {
                "saveToNBT", "saveToNbt", "serializeNBT", "serializeNbt", "toNBT", "toNbt", "save", "writeToNBT", "writeNbt"
        }) {
            Optional<Object> value = PixelmonIntegration.reflectNoArg(pokemon, method);
            if (value.isPresent()) return new Payload("pokemon:" + method + ":" + value.get().getClass().getName(), String.valueOf(value.get()));
        }
        return new Payload("", "");
    }

    private record Payload(String format, String value) {}
    private PixelmonRenderSnapshotFactory() {}
}

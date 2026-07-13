package com.akitaattribute.mobfarmblock.integration;

import java.util.Optional;

import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Captures Pixelmon's live entity/Pokemon state before the source entity is removed. */
public final class PixelmonRenderSnapshotFactory {
    public static Optional<PixelmonRenderSnapshot> capture(Entity entity) {
        if (!PixelmonIntegration.isPokemonEntity(entity)) return Optional.empty();
        Optional<ResourceLocation> species = PixelmonIntegration.getSpeciesId(entity);
        if (species.isEmpty()) return Optional.empty();

        Payload payload = entityPayload(entity).orElseGet(() -> PixelmonIntegration.getPokemonObject(entity).map(PixelmonRenderSnapshotFactory::pokemonPayload).orElse(new Payload("", "")));
        return Optional.of(new PixelmonRenderSnapshot(
                species.get(),
                PixelmonIntegration.getDisplayKey(entity).orElse(species.get().toString()),
                payload.format(),
                payload.value()
        ));
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

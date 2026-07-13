package com.akitaattribute.mobfarmblock.integration;

import java.util.Optional;

import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Captures Pixelmon's own Pokemon serialization before the source entity is removed. */
public final class PixelmonRenderSnapshotFactory {
    public static Optional<PixelmonRenderSnapshot> capture(Entity entity) {
        if (!PixelmonIntegration.isPokemonEntity(entity)) return Optional.empty();
        Optional<Object> pokemon = PixelmonIntegration.getPokemonObject(entity);
        Optional<ResourceLocation> species = PixelmonIntegration.getSpeciesId(entity);
        if (pokemon.isEmpty() || species.isEmpty()) return Optional.empty();
        Payload payload = payload(pokemon.get());
        return Optional.of(new PixelmonRenderSnapshot(
                species.get(),
                PixelmonIntegration.getDisplayKey(entity).orElse(species.get().toString()),
                payload.format(),
                payload.value()
        ));
    }

    private static Payload payload(Object pokemon) {
        for (String method : new String[] {
                "saveToNBT", "saveToNbt", "serializeNBT", "serializeNbt", "toNBT", "toNbt", "save", "writeToNBT", "writeNbt"
        }) {
            Optional<Object> value = PixelmonIntegration.reflectNoArg(pokemon, method);
            if (value.isPresent()) return new Payload(method + ":" + value.get().getClass().getName(), String.valueOf(value.get()));
        }
        return new Payload("", "");
    }

    private record Payload(String format, String value) {}
    private PixelmonRenderSnapshotFactory() {}
}

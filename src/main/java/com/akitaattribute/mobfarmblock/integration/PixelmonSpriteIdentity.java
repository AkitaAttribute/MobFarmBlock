package com.akitaattribute.mobfarmblock.integration;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Captures the Pixelmon sprite path from the live Pokemon object for safe later rendering. */
public final class PixelmonSpriteIdentity {
    public static Optional<ResourceLocation> getSpriteTexture(Entity entity) {
        if (!PixelmonIntegration.isPokemonEntity(entity)) return Optional.empty();
        return PixelmonIntegration.getPokemonObject(entity)
                .flatMap(pokemon -> PixelmonIntegration.reflectNoArg(pokemon, "getSprite"))
                .flatMap(PixelmonSpriteIdentity::coerceSpriteTexture);
    }

    private static Optional<ResourceLocation> coerceSpriteTexture(Object value) {
        if (value == null) return Optional.empty();
        try {
            ResourceLocation id = value instanceof ResourceLocation location ? location : ResourceLocation.parse(String.valueOf(value));
            String path = id.getPath();
            if (!path.startsWith("textures/")) path = "textures/" + path;
            if (!path.endsWith(".png")) return Optional.empty();
            return Optional.of(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private PixelmonSpriteIdentity() {}
}

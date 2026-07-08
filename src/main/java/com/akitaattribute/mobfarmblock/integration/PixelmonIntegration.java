package com.akitaattribute.mobfarmblock.integration;

import java.util.Optional;

import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.DropProfile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public final class PixelmonIntegration {
    public static boolean isLoaded() { return net.neoforged.fml.ModList.get().isLoaded("pixelmon"); }
    public static boolean isPokemonEntity(Entity entity) { return isLoaded() && entity.getType().builtInRegistryHolder().key().location().getNamespace().equals("pixelmon"); }
    public static Optional<ResourceLocation> getSpeciesId(Entity entity) { return isPokemonEntity(entity) ? Optional.of(entity.getType().builtInRegistryHolder().key().location()) : Optional.empty(); }
    public static Optional<DropProfile> resolveBattleDropProfile(Entity entity) { return Optional.empty(); }
    public static Optional<DisplaySnapshot> resolveDisplay(Entity entity) { return Optional.empty(); }
    private PixelmonIntegration() {}
}

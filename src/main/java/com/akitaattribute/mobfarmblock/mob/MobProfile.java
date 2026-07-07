package com.akitaattribute.mobfarmblock.mob;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public record MobProfile(
        ResourceLocation mobId,
        MobKind kind,
        DisplaySnapshot display,
        DropProfile dropProfile,
        InteractionProfile interactions,
        Optional<ResourceLocation> speciesId,
        Optional<ResourceLocation> breedingItem,
        long breedingCooldown
) {
}

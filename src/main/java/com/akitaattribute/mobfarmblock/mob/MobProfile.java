package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.util.Identifier;

public record MobProfile(
        Identifier mobId,
        MobKind kind,
        DisplaySnapshot display,
        DropProfile dropProfile,
        InteractionProfile interactions,
        Identifier speciesId,
        Identifier breedingItem,
        long breedingCooldown
) {
}

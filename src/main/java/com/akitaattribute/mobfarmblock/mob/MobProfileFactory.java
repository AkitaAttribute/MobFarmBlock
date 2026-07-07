package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Factory for compact profile capture. Full entity NBT is never read or persisted. */
public final class MobProfileFactory {
    public static StoredMob fromEntity(LivingEntity entity) {
        Identifier mobId = Registries.ENTITY_TYPE.getId(entity.getType());
        DisplaySnapshot display = DisplaySnapshot.forEntity(entity);
        MobProfile profile = MobProfileRegistry.get(mobId).orElse(null);
        if (profile != null) {
            return fromProfile(profile, 1);
        }
        return vanilla(mobId, display, 1);
    }

    public static StoredMob vanilla(Identifier mobId, DisplaySnapshot display, long count) {
        return new StoredMob(
                mobId,
                MobKind.VANILLA_ENTITY,
                count,
                display,
                initialState(mobId, display),
                DropProfileRegistry.get(mobId),
                new HashMap<>()
        );
    }

    public static StoredMob fromProfile(MobProfile profile, long count) {
        return new StoredMob(
                profile.mobId(),
                profile.kind(),
                count,
                profile.display(),
                initialState(profile.mobId(), profile.display()),
                profile.dropProfile(),
                new HashMap<>()
        );
    }

    private static NbtCompound initialState(Identifier mobId, DisplaySnapshot display) {
        NbtCompound state = new NbtCompound();
        if ("minecraft:sheep".equals(mobId.toString())) {
            state.putString("sheepColor", display.colorKey().isBlank() ? "white" : display.colorKey());
            state.putLong("nextWoolReadyAt", 0L);
        }
        return state;
    }

    private MobProfileFactory() {
    }
}

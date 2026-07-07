package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;

import com.akitaattribute.mobfarmblock.integration.CobblemonIntegration;
import com.akitaattribute.mobfarmblock.integration.PixelmonIntegration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;

/** Factory for compact profile capture. Full entity NBT is never read or persisted. */
public final class MobProfileFactory {
    public static StoredMob fromEntity(LivingEntity entity) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        MobProfile profile = MobProfileRegistry.get(mobId).orElse(null);
        if (profile != null) return fromProfile(profile, 1);

        MobKind kind = detectKind(entity, mobId);
        DisplaySnapshot display = resolveDisplay(entity);
        DropProfile drops = CobblemonIntegration.resolveBattleDropProfile(entity)
                .or(() -> PixelmonIntegration.resolveBattleDropProfile(entity))
                .orElse(DropProfileRegistry.get(mobId));
        return new StoredMob(mobId, kind, 1, display, initialState(mobId, display), drops, new HashMap<>());
    }

    public static StoredMob vanilla(ResourceLocation mobId, DisplaySnapshot display, long count) {
        return new StoredMob(mobId, MobKind.VANILLA_ENTITY, count, display, initialState(mobId, display), DropProfileRegistry.get(mobId), new HashMap<>());
    }

    public static StoredMob fromProfile(MobProfile profile, long count) {
        return new StoredMob(profile.mobId(), profile.kind(), count, profile.display(), initialState(profile.mobId(), profile.display()), profile.dropProfile(), new HashMap<>());
    }

    private static MobKind detectKind(LivingEntity entity, ResourceLocation mobId) {
        if (CobblemonIntegration.isPokemonEntity(entity)) return MobKind.COBBLEMON;
        if (PixelmonIntegration.isPokemonEntity(entity)) return MobKind.PIXELMON;
        return "minecraft".equals(mobId.getNamespace()) ? MobKind.VANILLA_ENTITY : MobKind.CUSTOM;
    }

    private static DisplaySnapshot resolveDisplay(LivingEntity entity) {
        return CobblemonIntegration.resolveDisplay(entity)
                .or(() -> PixelmonIntegration.resolveDisplay(entity))
                .orElseGet(() -> DisplaySnapshot.forEntity(entity));
    }

    private static CompoundTag initialState(ResourceLocation mobId, DisplaySnapshot display) {
        CompoundTag state = new CompoundTag();
        if ("minecraft:sheep".equals(mobId.toString())) {
            state.putString("sheepColor", display.colorKey().isBlank() ? "white" : display.colorKey());
            state.putLong("nextWoolReadyAt", 0L);
        }
        return state;
    }

    private MobProfileFactory() {}
}

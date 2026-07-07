package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public final class MobProfileRegistry {
    private static final Map<ResourceLocation, MobProfile> PROFILES = new HashMap<>();

    public static void replaceAll(Map<ResourceLocation, MobProfile> profiles) {
        PROFILES.clear();
        PROFILES.putAll(profiles);
        profiles.forEach((id, profile) -> DropProfileRegistry.put(id, profile.dropProfile()));
    }

    public static Optional<MobProfile> get(ResourceLocation mobId) {
        return Optional.ofNullable(PROFILES.get(mobId));
    }

    private MobProfileRegistry() {
    }
}

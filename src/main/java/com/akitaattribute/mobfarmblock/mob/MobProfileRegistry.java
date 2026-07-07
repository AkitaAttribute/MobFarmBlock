package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.util.Identifier;

public final class MobProfileRegistry {
    private static final Map<Identifier, MobProfile> PROFILES = new HashMap<>();

    public static void replaceAll(Map<Identifier, MobProfile> profiles) {
        PROFILES.clear();
        PROFILES.putAll(profiles);
        profiles.forEach((id, profile) -> DropProfileRegistry.put(id, profile.dropProfile()));
    }

    public static Optional<MobProfile> get(Identifier mobId) {
        return Optional.ofNullable(PROFILES.get(mobId));
    }

    private MobProfileRegistry() {
    }
}

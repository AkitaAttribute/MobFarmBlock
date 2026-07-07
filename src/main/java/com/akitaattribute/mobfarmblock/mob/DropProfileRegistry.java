package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

public final class DropProfileRegistry {
    private static final Map<ResourceLocation, DropProfile> PROFILES = new HashMap<>();

    static {
        registerDefaults();
    }

    public static void put(ResourceLocation id, DropProfile profile) {
        PROFILES.put(id, profile);
    }

    public static DropProfile get(ResourceLocation id) {
        return PROFILES.getOrDefault(id, DropProfile.EMPTY);
    }

    private static void registerDefaults() {
        DropProfile cow = profile(
                xp(1, 3),
                drop("minecraft:leather", 1.0, 0, 2, true, 0.0, 1),
                drop("minecraft:beef", 1.0, 1, 3, true, 0.0, 1)
        );
        put(ResourceLocation.parse("minecraft:cow"), cow);
        put(ResourceLocation.parse("minecraft:mooshroom"), cow);

        put(ResourceLocation.parse("minecraft:pig"), profile(
                xp(1, 3),
                drop("minecraft:porkchop", 1.0, 1, 3, true, 0.0, 1)
        ));

        put(ResourceLocation.parse("minecraft:sheep"), profile(
                xp(1, 3),
                drop("minecraft:mutton", 1.0, 1, 2, true, 0.0, 1)
        ));

        put(ResourceLocation.parse("minecraft:chicken"), profile(
                xp(1, 3),
                drop("minecraft:feather", 1.0, 0, 2, true, 0.0, 1),
                drop("minecraft:chicken", 1.0, 1, 1, true, 0.0, 1)
        ));

        put(ResourceLocation.parse("minecraft:rabbit"), profile(
                xp(1, 3),
                drop("minecraft:rabbit", 1.0, 0, 1, true, 0.0, 1),
                drop("minecraft:rabbit_hide", 1.0, 0, 1, true, 0.0, 1),
                drop("minecraft:rabbit_foot", 0.10, 1, 1, true, 0.03, 0)
        ));
    }

    private static DropRule drop(
            String itemId,
            double chance,
            int min,
            int max,
            boolean affectedByLooting,
            double lootingChanceBonus,
            int lootingMaxBonus
    ) {
        return new DropRule(ResourceLocation.parse(itemId), chance, min, max, affectedByLooting, lootingChanceBonus, lootingMaxBonus);
    }

    private static XpProfile xp(int min, int max) {
        return new XpProfile(min, max);
    }

    private static DropProfile profile(XpProfile xp, DropRule... rules) {
        return new DropProfile(List.of(rules), xp);
    }

    private DropProfileRegistry() {
    }
}

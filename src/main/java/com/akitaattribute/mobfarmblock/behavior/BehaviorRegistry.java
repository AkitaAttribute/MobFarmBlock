package com.akitaattribute.mobfarmblock.behavior;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.Identifier;

public final class BehaviorRegistry {
    private static final Map<Identifier, StoredMobBehavior> BEHAVIORS = new HashMap<>();
    private static final StoredMobBehavior GENERIC = new GenericMobBehavior();

    public static void registerDefaults() {
        register(Identifier.of("minecraft:sheep"), new SheepBehavior());
        register(Identifier.of("minecraft:cow"), new CowBehavior());
        register(Identifier.of("minecraft:mooshroom"), new CowBehavior());
        register(Identifier.of("minecraft:chicken"), new ChickenBehavior());
    }

    public static void register(Identifier id, StoredMobBehavior behavior) {
        BEHAVIORS.put(id, behavior);
    }

    public static StoredMobBehavior get(Identifier id) {
        return BEHAVIORS.getOrDefault(id, GENERIC);
    }

    private BehaviorRegistry() {
    }
}

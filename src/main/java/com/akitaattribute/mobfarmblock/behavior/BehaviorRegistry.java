package com.akitaattribute.mobfarmblock.behavior;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

public final class BehaviorRegistry {
    private static final Map<ResourceLocation, StoredMobBehavior> BEHAVIORS = new HashMap<>();
    private static final StoredMobBehavior GENERIC = new GenericMobBehavior();
    public static void registerDefaults() {}
    public static void register(ResourceLocation id, StoredMobBehavior behavior) { BEHAVIORS.put(id, behavior); }
    public static StoredMobBehavior get(ResourceLocation id) { return BEHAVIORS.getOrDefault(id, GENERIC); }
    private BehaviorRegistry() {}
}

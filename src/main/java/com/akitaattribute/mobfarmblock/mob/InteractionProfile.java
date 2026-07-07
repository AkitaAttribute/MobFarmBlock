package com.akitaattribute.mobfarmblock.mob;

import java.util.Map;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

public record InteractionProfile(Map<String, NbtCompound> actions) {
    public static final InteractionProfile EMPTY = new InteractionProfile(Map.of());

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        actions.forEach((key, value) -> nbt.put(key, value.copy()));
        return nbt;
    }

    public static InteractionProfile fromNbt(NbtCompound nbt) {
        java.util.HashMap<String, NbtCompound> actions = new java.util.HashMap<>();
        for (String key : nbt.getKeys()) {
            actions.put(key, nbt.getCompound(key).copy());
        }
        return new InteractionProfile(Map.copyOf(actions));
    }

    public Identifier breedingItem() {
        NbtCompound breed = actions.get("breed");
        if (breed != null && breed.contains("item")) {
            return Identifier.of(breed.getString("item"));
        }
        return null;
    }
}

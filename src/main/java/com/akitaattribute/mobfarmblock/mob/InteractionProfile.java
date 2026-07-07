package com.akitaattribute.mobfarmblock.mob;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record InteractionProfile(List<InteractionDefinition> definitions) {
    public static final InteractionProfile EMPTY = new InteractionProfile(List.of());

    public List<InteractionDefinition> forMethod(ResourceLocation methodId) {
        return definitions.stream().filter(definition -> definition.methodId().equals(methodId)).toList();
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        definitions.forEach(def -> list.add(def.toNbt()));
        tag.put("definitions", list);
        return tag;
    }

    public static InteractionProfile fromNbt(CompoundTag tag) {
        java.util.ArrayList<InteractionDefinition> definitions = new java.util.ArrayList<>();
        for (Tag element : tag.getList("definitions", Tag.TAG_COMPOUND)) definitions.add(InteractionDefinition.fromNbt((CompoundTag) element));
        return new InteractionProfile(List.copyOf(definitions));
    }
}

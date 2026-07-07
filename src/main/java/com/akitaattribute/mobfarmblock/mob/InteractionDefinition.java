package com.akitaattribute.mobfarmblock.mob;

import java.util.Map;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record InteractionDefinition(
        ResourceLocation methodId,
        Optional<ResourceLocation> item,
        Optional<ResourceLocation> itemTag,
        Optional<ResourceLocation> outputItem,
        long cooldownTicks,
        int minCount,
        int maxCount,
        Optional<String> stateKey,
        Map<String, String> parameters
) {
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("method", methodId.toString());
        item.ifPresent(id -> tag.putString("item", id.toString()));
        itemTag.ifPresent(id -> tag.putString("itemTag", id.toString()));
        outputItem.ifPresent(id -> tag.putString("output", id.toString()));
        tag.putLong("cooldownTicks", cooldownTicks);
        tag.putInt("minCount", minCount);
        tag.putInt("maxCount", maxCount);
        stateKey.ifPresent(key -> tag.putString("stateKey", key));
        CompoundTag parameterTag = new CompoundTag();
        parameters.forEach(parameterTag::putString);
        tag.put("parameters", parameterTag);
        return tag;
    }

    public static InteractionDefinition fromNbt(CompoundTag tag) {
        java.util.HashMap<String, String> parameters = new java.util.HashMap<>();
        CompoundTag parameterTag = tag.getCompound("parameters");
        for (String key : parameterTag.getAllKeys()) parameters.put(key, parameterTag.getString(key));
        return new InteractionDefinition(
                ResourceLocation.parse(tag.getString("method")),
                tag.contains("item") ? Optional.of(ResourceLocation.parse(tag.getString("item"))) : Optional.empty(),
                tag.contains("itemTag") ? Optional.of(ResourceLocation.parse(tag.getString("itemTag"))) : Optional.empty(),
                tag.contains("output") ? Optional.of(ResourceLocation.parse(tag.getString("output"))) : Optional.empty(),
                tag.getLong("cooldownTicks"),
                tag.contains("minCount") ? tag.getInt("minCount") : 1,
                tag.contains("maxCount") ? tag.getInt("maxCount") : 1,
                tag.contains("stateKey") ? Optional.of(tag.getString("stateKey")) : Optional.empty(),
                Map.copyOf(parameters)
        );
    }
}

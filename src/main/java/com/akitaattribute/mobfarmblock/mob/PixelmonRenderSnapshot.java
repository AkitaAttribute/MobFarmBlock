package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Compact render-facing Pixelmon state captured from the live Pixelmon entity before it is removed. */
public record PixelmonRenderSnapshot(
        ResourceLocation speciesId,
        String variantKey,
        String payloadFormat,
        String payload,
        float capturedWidth,
        float capturedHeight,
        float sizeCentimeters
) {
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("speciesId", speciesId == null ? "" : speciesId.toString());
        tag.putString("variantKey", variantKey == null ? "" : variantKey);
        tag.putString("payloadFormat", payloadFormat == null ? "" : payloadFormat);
        tag.putString("payload", payload == null ? "" : payload);
        tag.putFloat("capturedWidth", capturedWidth);
        tag.putFloat("capturedHeight", capturedHeight);
        tag.putFloat("sizeCentimeters", sizeCentimeters);
        return tag;
    }

    public static PixelmonRenderSnapshot fromNbt(CompoundTag tag) {
        String species = tag.getString("speciesId");
        return new PixelmonRenderSnapshot(
                species.isBlank() ? null : ResourceLocation.parse(species),
                tag.getString("variantKey"),
                tag.getString("payloadFormat"),
                tag.getString("payload"),
                tag.contains("capturedWidth") ? tag.getFloat("capturedWidth") : 0.0F,
                tag.contains("capturedHeight") ? tag.getFloat("capturedHeight") : 0.0F,
                tag.contains("sizeCentimeters") ? tag.getFloat("sizeCentimeters") : 0.0F
        );
    }

    public boolean hasPayload() {
        return payload != null && !payload.isBlank();
    }
}

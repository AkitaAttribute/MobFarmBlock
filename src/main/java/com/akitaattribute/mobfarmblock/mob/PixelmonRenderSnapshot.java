package com.akitaattribute.mobfarmblock.mob;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private static final ConcurrentMap<String, CompoundTag> PREPARED_ENTITY_PAYLOADS = new ConcurrentHashMap<>();

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("speciesId", speciesId == null ? "" : speciesId.toString());
        tag.putString("variantKey", variantKey == null ? "" : variantKey);
        tag.putString("payloadFormat", payloadFormat == null ? "" : payloadFormat);
        tag.putString("payload", payload == null ? "" : payload);
        cachedEntityPayload().ifPresent(payloadTag -> tag.put("payloadTag", payloadTag));
        tag.putFloat("capturedWidth", capturedWidth);
        tag.putFloat("capturedHeight", capturedHeight);
        tag.putFloat("sizeCentimeters", sizeCentimeters);
        return tag;
    }

    public static PixelmonRenderSnapshot fromNbt(CompoundTag tag) {
        String species = tag.getString("speciesId");
        PixelmonRenderSnapshot snapshot = new PixelmonRenderSnapshot(
                species.isBlank() ? null : ResourceLocation.parse(species),
                tag.getString("variantKey"),
                tag.getString("payloadFormat"),
                tag.getString("payload"),
                tag.contains("capturedWidth") ? tag.getFloat("capturedWidth") : 0.0F,
                tag.contains("capturedHeight") ? tag.getFloat("capturedHeight") : 0.0F,
                tag.contains("sizeCentimeters") ? tag.getFloat("sizeCentimeters") : 0.0F
        );
        if (tag.contains("payloadTag")) snapshot.cacheEntityPayload(tag.getCompound("payloadTag"));
        return snapshot;
    }

    public boolean hasPayload() {
        return payload != null && !payload.isBlank();
    }

    public boolean hasEntityPayload() {
        return hasPayload() && payloadFormat != null && payloadFormat.startsWith("entity:");
    }

    public void cacheEntityPayload(CompoundTag tag) {
        if (!hasEntityPayload() || tag == null || tag.isEmpty()) return;
        PREPARED_ENTITY_PAYLOADS.put(payload, tag.copy());
    }

    public Optional<CompoundTag> cachedEntityPayload() {
        return cachedEntityPayload(payload);
    }

    public static Optional<CompoundTag> cachedEntityPayload(String payload) {
        if (payload == null || payload.isBlank()) return Optional.empty();
        CompoundTag tag = PREPARED_ENTITY_PAYLOADS.get(payload);
        return tag == null ? Optional.empty() : Optional.of(tag.copy());
    }
}

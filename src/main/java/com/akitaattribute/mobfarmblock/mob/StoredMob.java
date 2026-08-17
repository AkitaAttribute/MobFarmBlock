package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** StoredMob is a compact profile, not a serialized or ticking live entity. */
public class StoredMob {
    public ResourceLocation mobId;
    public MobKind kind;
    public long count;
    public DisplaySnapshot display;
    public CompoundTag state;
    public DropProfile dropProfile;
    public InteractionProfile interactionProfile;
    public Map<ResourceLocation, Long> readyAtTicks;
    public ResourceLocation speciesId;
    public String dropProfileSource;
    public CobblemonRenderSnapshot cobblemonRenderSnapshot;
    public PixelmonRenderSnapshot pixelmonRenderSnapshot;

    public StoredMob(ResourceLocation mobId, MobKind kind, long count, DisplaySnapshot display, CompoundTag state,
                     DropProfile dropProfile, InteractionProfile interactionProfile, Map<ResourceLocation, Long> readyAtTicks,
                     ResourceLocation speciesId, String dropProfileSource) {
        this(mobId, kind, count, display, state, dropProfile, interactionProfile, readyAtTicks, speciesId, dropProfileSource, null, null);
    }

    public StoredMob(ResourceLocation mobId, MobKind kind, long count, DisplaySnapshot display, CompoundTag state,
                     DropProfile dropProfile, InteractionProfile interactionProfile, Map<ResourceLocation, Long> readyAtTicks,
                     ResourceLocation speciesId, String dropProfileSource, CobblemonRenderSnapshot cobblemonRenderSnapshot) {
        this(mobId, kind, count, display, state, dropProfile, interactionProfile, readyAtTicks, speciesId, dropProfileSource, cobblemonRenderSnapshot, null);
    }

    public StoredMob(ResourceLocation mobId, MobKind kind, long count, DisplaySnapshot display, CompoundTag state,
                     DropProfile dropProfile, InteractionProfile interactionProfile, Map<ResourceLocation, Long> readyAtTicks,
                     ResourceLocation speciesId, String dropProfileSource, CobblemonRenderSnapshot cobblemonRenderSnapshot,
                     PixelmonRenderSnapshot pixelmonRenderSnapshot) {
        this.mobId = mobId;
        this.kind = kind;
        this.count = count;
        this.display = normalizedDisplay(kind, mobId, display);
        this.state = state == null ? new CompoundTag() : state;
        this.dropProfile = dropProfile == null ? DropProfile.EMPTY : dropProfile;
        this.interactionProfile = interactionProfile == null ? InteractionProfile.EMPTY : interactionProfile;
        this.readyAtTicks = readyAtTicks == null ? new HashMap<>() : readyAtTicks;
        this.speciesId = speciesId;
        this.dropProfileSource = dropProfileSource == null ? "empty" : dropProfileSource;
        this.cobblemonRenderSnapshot = cobblemonRenderSnapshot;
        this.pixelmonRenderSnapshot = pixelmonRenderSnapshot;
    }

    private static DisplaySnapshot normalizedDisplay(MobKind kind, ResourceLocation mobId, DisplaySnapshot display) {
        DisplaySnapshot safe = display == null ? DisplaySnapshot.EMPTY : display;
        if (kind == MobKind.PIXELMON && mobId != null && "pixelmon:pixelmon".equals(mobId.toString()) && safe.scale() != 1.0F) {
            return new DisplaySnapshot(safe.entityTypeId(), safe.textureId(), safe.variantKey(), safe.colorKey(), safe.baby(), 1.0F);
        }
        return safe;
    }

    public static StoredMob empty() {
        return new StoredMob(ResourceLocation.withDefaultNamespace("empty"), MobKind.CUSTOM, 0, DisplaySnapshot.EMPTY,
                new CompoundTag(), DropProfile.EMPTY, InteractionProfile.EMPTY, new HashMap<>(), null, "empty");
    }

    public boolean isEmpty() { return count <= 0 || "empty".equals(mobId.getPath()); }

    public boolean isSameType(StoredMob other) {
        if (other == null || !mobId.equals(other.mobId) || kind != other.kind) return false;
        if (isSingleEntityPokemonKind(kind, mobId)) {
            if (speciesId != null && other.speciesId != null) return speciesId.equals(other.speciesId);
            if (speciesId != null || other.speciesId != null) return false;
            String variant = display == null ? "" : display.variantKey();
            String otherVariant = other.display == null ? "" : other.display.variantKey();
            if (!variant.isBlank() && !otherVariant.isBlank()) return variant.equals(otherVariant);
            return false;
        }
        return true;
    }

    private static boolean isSingleEntityPokemonKind(MobKind kind, ResourceLocation mobId) {
        return (kind == MobKind.COBBLEMON && "cobblemon:pokemon".equals(mobId.toString()))
                || (kind == MobKind.PIXELMON && "pixelmon:pixelmon".equals(mobId.toString()));
    }

    public boolean isUnknownCobblemonPokemon() {
        return kind == MobKind.COBBLEMON && "cobblemon:pokemon".equals(mobId.toString()) && speciesId == null;
    }

    public boolean ready(ResourceLocation action, long now) { return now >= readyAtTicks.getOrDefault(action, 0L); }
    public void setCooldown(ResourceLocation action, long now, long cooldownTicks) { readyAtTicks.put(action, now + cooldownTicks); }

    public StoredMob copyWithCount(long newCount) {
        return new StoredMob(mobId, kind, newCount, display, state.copy(), dropProfile, interactionProfile,
                new HashMap<>(readyAtTicks), speciesId, dropProfileSource, cobblemonRenderSnapshot, pixelmonRenderSnapshot);
    }

    public void mergeDiscoveredProfileFrom(StoredMob incoming) {
        if (incoming == null) return;
        this.dropProfile = this.dropProfile.mergeDiscovered(incoming.dropProfile);
        this.dropProfileSource = mergeDropProfileSource(this.dropProfileSource, incoming.dropProfileSource);
        if (this.pixelmonRenderSnapshot == null) this.pixelmonRenderSnapshot = incoming.pixelmonRenderSnapshot;
        if (this.cobblemonRenderSnapshot == null) this.cobblemonRenderSnapshot = incoming.cobblemonRenderSnapshot;
    }

    private static String mergeDropProfileSource(String existing, String incoming) {
        if (incoming == null || incoming.isBlank() || "empty".equals(incoming)) return existing == null ? "empty" : existing;
        if (existing == null || existing.isBlank() || "empty".equals(existing)) return incoming;
        if (existing.equals(incoming)) return existing;
        if (existing.contains(incoming)) return existing;
        if (incoming.contains(existing)) return incoming;
        return existing + "+" + incoming;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mobId", mobId.toString());
        tag.putString("kind", kind.name());
        tag.putLong("count", count);
        tag.put("display", display.toNbt());
        tag.put("state", state.copy());
        tag.put("dropProfile", dropProfile.toNbt());
        tag.put("interactionProfile", interactionProfile.toNbt());
        if (speciesId != null) tag.putString("speciesId", speciesId.toString());
        tag.putString("dropProfileSource", dropProfileSource);
        if (cobblemonRenderSnapshot != null) tag.put("cobblemonRenderSnapshot", cobblemonRenderSnapshot.toNbt());
        if (pixelmonRenderSnapshot != null) tag.put("pixelmonRenderSnapshot", pixelmonRenderSnapshot.toNbt());
        CompoundTag readyTag = new CompoundTag();
        readyAtTicks.forEach((action, tick) -> readyTag.putLong(action.toString(), tick));
        tag.put("readyAtTicks", readyTag);
        return tag;
    }

    public static StoredMob fromNbt(CompoundTag tag) {
        Map<ResourceLocation, Long> readyAtTicks = new HashMap<>();
        CompoundTag readyTag = tag.getCompound("readyAtTicks");
        for (String key : readyTag.getAllKeys()) readyAtTicks.put(ResourceLocation.parse(key), tag.getCompound("readyAtTicks").getLong(key));
        ResourceLocation speciesId = tag.contains("speciesId") ? ResourceLocation.parse(tag.getString("speciesId")) : null;
        CobblemonRenderSnapshot cobblemonSnapshot = tag.contains("cobblemonRenderSnapshot") ? CobblemonRenderSnapshot.fromNbt(tag.getCompound("cobblemonRenderSnapshot")) : null;
        PixelmonRenderSnapshot pixelmonSnapshot = tag.contains("pixelmonRenderSnapshot") ? PixelmonRenderSnapshot.fromNbt(tag.getCompound("pixelmonRenderSnapshot")) : null;
        return new StoredMob(
                ResourceLocation.parse(tag.getString("mobId")),
                MobKind.valueOf(tag.contains("kind") ? tag.getString("kind") : MobKind.CUSTOM.name()),
                tag.getLong("count"),
                tag.contains("display") ? DisplaySnapshot.fromNbt(tag.getCompound("display")) : DisplaySnapshot.EMPTY,
                tag.getCompound("state").copy(),
                tag.contains("dropProfile") ? DropProfile.fromNbt(tag.getCompound("dropProfile")) : DropProfile.EMPTY,
                tag.contains("interactionProfile") ? InteractionProfile.fromNbt(tag.getCompound("interactionProfile")) : InteractionProfile.EMPTY,
                readyAtTicks,
                speciesId,
                tag.contains("dropProfileSource") ? tag.getString("dropProfileSource") : "empty",
                cobblemonSnapshot,
                pixelmonSnapshot
        );
    }
}

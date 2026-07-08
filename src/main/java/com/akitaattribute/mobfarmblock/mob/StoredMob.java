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

    public StoredMob(ResourceLocation mobId, MobKind kind, long count, DisplaySnapshot display, CompoundTag state,
                     DropProfile dropProfile, InteractionProfile interactionProfile, Map<ResourceLocation, Long> readyAtTicks,
                     ResourceLocation speciesId, String dropProfileSource) {
        this(mobId, kind, count, display, state, dropProfile, interactionProfile, readyAtTicks, speciesId, dropProfileSource, null);
    }

    public StoredMob(ResourceLocation mobId, MobKind kind, long count, DisplaySnapshot display, CompoundTag state,
                     DropProfile dropProfile, InteractionProfile interactionProfile, Map<ResourceLocation, Long> readyAtTicks,
                     ResourceLocation speciesId, String dropProfileSource, CobblemonRenderSnapshot cobblemonRenderSnapshot) {
        this.mobId = mobId;
        this.kind = kind;
        this.count = count;
        this.display = display == null ? DisplaySnapshot.EMPTY : display;
        this.state = state == null ? new CompoundTag() : state;
        this.dropProfile = dropProfile == null ? DropProfile.EMPTY : dropProfile;
        this.interactionProfile = interactionProfile == null ? InteractionProfile.EMPTY : interactionProfile;
        this.readyAtTicks = readyAtTicks == null ? new HashMap<>() : readyAtTicks;
        this.speciesId = speciesId;
        this.dropProfileSource = dropProfileSource == null ? "empty" : dropProfileSource;
        this.cobblemonRenderSnapshot = cobblemonRenderSnapshot;
    }

    public static StoredMob empty() {
        return new StoredMob(ResourceLocation.withDefaultNamespace("empty"), MobKind.CUSTOM, 0, DisplaySnapshot.EMPTY,
                new CompoundTag(), DropProfile.EMPTY, InteractionProfile.EMPTY, new HashMap<>(), null, "empty");
    }

    public boolean isEmpty() { return count <= 0 || "empty".equals(mobId.getPath()); }

    public boolean isSameType(StoredMob other) {
        if (other == null || !mobId.equals(other.mobId) || kind != other.kind) return false;
        if (kind == MobKind.COBBLEMON && "cobblemon:pokemon".equals(mobId.toString())) {
            if (speciesId != null && other.speciesId != null) return speciesId.equals(other.speciesId);
            if (speciesId != null || other.speciesId != null) return false;
            String variant = display == null ? "" : display.variantKey();
            String otherVariant = other.display == null ? "" : other.display.variantKey();
            if (!variant.isBlank() && !otherVariant.isBlank()) return variant.equals(otherVariant);
            return false;
        }
        return true;
    }

    public boolean isUnknownCobblemonPokemon() {
        return kind == MobKind.COBBLEMON && "cobblemon:pokemon".equals(mobId.toString()) && speciesId == null;
    }

    public boolean ready(ResourceLocation action, long now) { return now >= readyAtTicks.getOrDefault(action, 0L); }
    public void setCooldown(ResourceLocation action, long now, long cooldownTicks) { readyAtTicks.put(action, now + cooldownTicks); }

    public StoredMob copyWithCount(long newCount) {
        return new StoredMob(mobId, kind, newCount, display, state.copy(), dropProfile, interactionProfile,
                new HashMap<>(readyAtTicks), speciesId, dropProfileSource, cobblemonRenderSnapshot);
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
        CompoundTag readyTag = new CompoundTag();
        readyAtTicks.forEach((action, tick) -> readyTag.putLong(action.toString(), tick));
        tag.put("readyAtTicks", readyTag);
        return tag;
    }

    public static StoredMob fromNbt(CompoundTag tag) {
        Map<ResourceLocation, Long> readyAtTicks = new HashMap<>();
        CompoundTag readyTag = tag.getCompound("readyAtTicks");
        for (String key : readyTag.getAllKeys()) readyAtTicks.put(ResourceLocation.parse(key), readyTag.getLong(key));
        ResourceLocation speciesId = tag.contains("speciesId") ? ResourceLocation.parse(tag.getString("speciesId")) : null;
        CobblemonRenderSnapshot snapshot = tag.contains("cobblemonRenderSnapshot") ? CobblemonRenderSnapshot.fromNbt(tag.getCompound("cobblemonRenderSnapshot")) : null;
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
                snapshot
        );
    }
}

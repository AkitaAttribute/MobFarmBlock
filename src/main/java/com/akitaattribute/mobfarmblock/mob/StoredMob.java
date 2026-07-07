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
    public Map<ResourceLocation, Long> readyAtTicks;

    public StoredMob(ResourceLocation mobId, MobKind kind, long count, DisplaySnapshot display, CompoundTag state, DropProfile dropProfile, Map<ResourceLocation, Long> readyAtTicks) {
        this.mobId = mobId;
        this.kind = kind;
        this.count = count;
        this.display = display;
        this.state = state;
        this.dropProfile = dropProfile;
        this.readyAtTicks = readyAtTicks;
    }

    public static StoredMob empty() {
        return new StoredMob(ResourceLocation.withDefaultNamespace("empty"), MobKind.CUSTOM, 0, DisplaySnapshot.EMPTY, new CompoundTag(), DropProfile.EMPTY, new HashMap<>());
    }

    public boolean isEmpty() {
        return count <= 0 || "empty".equals(mobId.getPath());
    }

    public boolean isSameType(StoredMob other) {
        return mobId.equals(other.mobId) && kind == other.kind;
    }

    public boolean ready(ResourceLocation action, long now) {
        return now >= readyAtTicks.getOrDefault(action, 0L);
    }

    public void setCooldown(ResourceLocation action, long now, long cooldownTicks) {
        readyAtTicks.put(action, now + cooldownTicks);
    }

    public StoredMob copyWithCount(long newCount) {
        return new StoredMob(mobId, kind, newCount, display, state.copy(), dropProfile, new HashMap<>(readyAtTicks));
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mobId", mobId.toString());
        tag.putString("kind", kind.name());
        tag.putLong("count", count);
        tag.put("display", display.toNbt());
        tag.put("state", state.copy());
        tag.put("dropProfile", dropProfile.toNbt());
        CompoundTag readyTag = new CompoundTag();
        readyAtTicks.forEach((action, tick) -> readyTag.putLong(action.toString(), tick));
        tag.put("readyAtTicks", readyTag);
        return tag;
    }

    public static StoredMob fromNbt(CompoundTag tag) {
        Map<ResourceLocation, Long> readyAtTicks = new HashMap<>();
        CompoundTag readyTag = tag.getCompound("readyAtTicks");
        for (String key : readyTag.getAllKeys()) {
            readyAtTicks.put(ResourceLocation.parse(key), readyTag.getLong(key));
        }
        String kindName = tag.contains("kind") ? tag.getString("kind") : MobKind.CUSTOM.name();
        return new StoredMob(
                ResourceLocation.parse(tag.getString("mobId")),
                MobKind.valueOf(kindName),
                tag.getLong("count"),
                tag.contains("display") ? DisplaySnapshot.fromNbt(tag.getCompound("display")) : DisplaySnapshot.EMPTY,
                tag.getCompound("state").copy(),
                tag.contains("dropProfile") ? DropProfile.fromNbt(tag.getCompound("dropProfile")) : DropProfile.EMPTY,
                readyAtTicks
        );
    }
}

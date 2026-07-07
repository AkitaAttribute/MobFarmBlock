package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/** StoredMob is a compact profile, not a serialized or ticking live entity. */
public class StoredMob {
    public Identifier mobId;
    public MobKind kind;
    public long count;
    public DisplaySnapshot display;
    public NbtCompound state;
    public DropProfile dropProfile;
    public Map<Identifier, Long> readyAtTicks;

    public StoredMob(
            Identifier mobId,
            MobKind kind,
            long count,
            DisplaySnapshot display,
            NbtCompound state,
            DropProfile dropProfile,
            Map<Identifier, Long> readyAtTicks
    ) {
        this.mobId = mobId;
        this.kind = kind;
        this.count = count;
        this.display = display;
        this.state = state;
        this.dropProfile = dropProfile;
        this.readyAtTicks = readyAtTicks;
    }

    public static StoredMob empty() {
        return new StoredMob(
                Identifier.of("minecraft:empty"),
                MobKind.CUSTOM,
                0,
                DisplaySnapshot.EMPTY,
                new NbtCompound(),
                DropProfile.EMPTY,
                new HashMap<>()
        );
    }

    public boolean isEmpty() {
        return count <= 0 || "empty".equals(mobId.getPath());
    }

    public boolean isSameType(StoredMob other) {
        return mobId.equals(other.mobId) && kind == other.kind;
    }

    public boolean ready(Identifier action, long now) {
        return now >= readyAtTicks.getOrDefault(action, 0L);
    }

    public void setCooldown(Identifier action, long now, long cooldownTicks) {
        readyAtTicks.put(action, now + cooldownTicks);
    }

    public StoredMob copyWithCount(long newCount) {
        return new StoredMob(
                mobId,
                kind,
                newCount,
                display,
                state.copy(),
                dropProfile,
                new HashMap<>(readyAtTicks)
        );
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("mobId", mobId.toString());
        nbt.putString("kind", kind.name());
        nbt.putLong("count", count);
        nbt.put("display", display.toNbt());
        nbt.put("state", state.copy());
        nbt.put("dropProfile", dropProfile.toNbt());

        NbtCompound readyNbt = new NbtCompound();
        readyAtTicks.forEach((action, tick) -> readyNbt.putLong(action.toString(), tick));
        nbt.put("readyAtTicks", readyNbt);
        return nbt;
    }

    public static StoredMob fromNbt(NbtCompound nbt) {
        Map<Identifier, Long> readyAtTicks = new HashMap<>();
        NbtCompound readyNbt = nbt.getCompound("readyAtTicks");
        for (String key : readyNbt.getKeys()) {
            readyAtTicks.put(Identifier.of(key), readyNbt.getLong(key));
        }

        String kindName = nbt.contains("kind") ? nbt.getString("kind") : MobKind.CUSTOM.name();
        return new StoredMob(
                Identifier.of(nbt.getString("mobId")),
                MobKind.valueOf(kindName),
                nbt.getLong("count"),
                nbt.contains("display") ? DisplaySnapshot.fromNbt(nbt.getCompound("display")) : DisplaySnapshot.EMPTY,
                nbt.getCompound("state").copy(),
                nbt.contains("dropProfile") ? DropProfile.fromNbt(nbt.getCompound("dropProfile")) : DropProfile.EMPTY,
                readyAtTicks
        );
    }
}

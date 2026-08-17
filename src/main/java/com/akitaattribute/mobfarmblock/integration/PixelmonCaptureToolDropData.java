package com.akitaattribute.mobfarmblock.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PixelmonCaptureToolDropData extends SavedData {
    private static final String DATA_NAME = MobFarmBlockMod.MOD_ID + "_pixelmon_capture_tool_drops";
    private static final String MISSES_KEY = "misses";
    private static final SavedData.Factory<PixelmonCaptureToolDropData> FACTORY = new SavedData.Factory<>(
            PixelmonCaptureToolDropData::new,
            PixelmonCaptureToolDropData::load,
            null
    );

    private final Map<UUID, Long> missesByPlayer = new HashMap<>();

    public static PixelmonCaptureToolDropData get(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public long getMisses(ServerPlayer player) {
        return missesByPlayer.getOrDefault(player.getUUID(), 0L);
    }

    public void recordMiss(ServerPlayer player) {
        missesByPlayer.merge(player.getUUID(), 1L, PixelmonCaptureToolDropData::safeIncrement);
        setDirty();
    }

    public void reset(ServerPlayer player) {
        if (missesByPlayer.remove(player.getUUID()) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag misses = new CompoundTag();
        missesByPlayer.forEach((uuid, count) -> misses.putLong(uuid.toString(), Math.max(0L, count)));
        tag.put(MISSES_KEY, misses);
        return tag;
    }

    private static PixelmonCaptureToolDropData load(CompoundTag tag, HolderLookup.Provider provider) {
        PixelmonCaptureToolDropData data = new PixelmonCaptureToolDropData();
        CompoundTag misses = tag.getCompound(MISSES_KEY);
        for (String key : misses.getAllKeys()) {
            try {
                long count = Math.max(0L, misses.getLong(key));
                if (count > 0L) data.missesByPlayer.put(UUID.fromString(key), count);
            } catch (IllegalArgumentException ignored) {
                // Ignore corrupted or obsolete entries.
            }
        }
        return data;
    }

    private static long safeIncrement(long existing, long increment) {
        if (Long.MAX_VALUE - existing < increment) return Long.MAX_VALUE;
        return existing + increment;
    }
}

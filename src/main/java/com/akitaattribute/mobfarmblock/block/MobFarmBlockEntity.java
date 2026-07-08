package com.akitaattribute.mobfarmblock.block;

import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores abstract mob profiles and timestamps only; it never stores or simulates live entities. */
public class MobFarmBlockEntity extends BlockEntity {
    private StoredMob stored = StoredMob.empty();

    public MobFarmBlockEntity(BlockPos pos, BlockState state) { super(ModBlocks.MOB_FARM_BLOCK_ENTITY.get(), pos, state); }
    public StoredMob getStored() { return stored; }
    public void setStored(StoredMob stored) { this.stored = stored; sync(); }

    public InsertResult insertOrMergeDetailed(StoredMob incoming) {
        StoredMob previous = stored.copyWithCount(stored.count);
        if (incoming == null || incoming.isEmpty()) return new InsertResult(false, false, previous, stored, "invalid stored data");
        if (stored.isEmpty()) { stored = incoming.copyWithCount(incoming.count); sync(); return new InsertResult(true, false, previous, stored, "inserted"); }
        if (incoming.isUnknownCobblemonPokemon()) return new InsertResult(false, false, previous, stored, "Cobblemon species ID unavailable; refused merge to avoid mixing Pokemon species");
        if (stored.isUnknownCobblemonPokemon()) return new InsertResult(false, false, previous, stored, "Stored Cobblemon species ID unavailable; refused merge to avoid mixing Pokemon species");
        if (!stored.isSameType(incoming)) return new InsertResult(false, false, previous, stored, "different mob");
        stored.count += incoming.count;
        sync();
        return new InsertResult(true, true, previous, stored, "merged");
    }

    public boolean insertOrMerge(StoredMob incoming) { return insertOrMergeDetailed(incoming).success(); }

    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public void resetCooldownsOnPlacement(long now) {
        stored.state.putBoolean("breedingCycleActive", false);
        stored.state.putLong("breedingBaseCount", stored.count);
        stored.state.putInt("breedingFedCount", 0);
        stored.readyAtTicks.clear();
        stored.interactionProfile.definitions().forEach(definition -> {
            long cooldown = definition.cooldownTicks() > 0 ? definition.cooldownTicks() : 6000L;
            if (definition.methodId().toString().equals("mob_farm_block:breed")) stored.state.putLong("breedingReadyAt", now + cooldown);
            else stored.readyAtTicks.put(definition.methodId(), now + cooldown);
        });
        if ("minecraft:sheep".equals(stored.mobId.toString())) stored.state.putLong("nextWoolReadyAt", now + 12000L);
        sync();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); tag.put("storedMob", stored.toNbt()); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); stored = tag.contains("storedMob") ? StoredMob.fromNbt(tag.getCompound("storedMob")) : StoredMob.empty(); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    public record InsertResult(boolean success, boolean merged, StoredMob previous, StoredMob current, String reason) {}
}

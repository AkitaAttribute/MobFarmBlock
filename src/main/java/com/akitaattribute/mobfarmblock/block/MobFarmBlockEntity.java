package com.akitaattribute.mobfarmblock.block;

import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
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

    public boolean insertOrMerge(StoredMob incoming) {
        if (incoming == null || incoming.isEmpty()) return false;
        if (stored.isEmpty()) { stored = incoming.copyWithCount(incoming.count); sync(); return true; }
        if (!stored.isSameType(incoming)) return false;
        stored.count += incoming.count;
        sync();
        return true;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("storedMob", stored.toNbt());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.contains("storedMob") ? StoredMob.fromNbt(tag.getCompound("storedMob")) : StoredMob.empty();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

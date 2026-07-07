package com.akitaattribute.mobfarmblock.block;

import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Stores abstract mob profiles and timestamps only; it never stores or simulates live entities. */
public class MobFarmBlockEntity extends BlockEntity {
    private StoredMob stored = StoredMob.empty();

    public MobFarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MOB_FARM_BLOCK_ENTITY, pos, state);
    }

    public StoredMob getStored() {
        return stored;
    }

    public void setStored(StoredMob stored) {
        this.stored = stored;
        sync();
    }

    public boolean insertOrMerge(StoredMob incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        if (stored.isEmpty()) {
            stored = incoming.copyWithCount(incoming.count);
            sync();
            return true;
        }
        if (!stored.isSameType(incoming)) {
            return false;
        }
        stored.count += incoming.count;
        sync();
        return true;
    }

    private void sync() {
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.put("storedMob", stored.toNbt());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        stored = nbt.contains("storedMob") ? StoredMob.fromNbt(nbt.getCompound("storedMob")) : StoredMob.empty();
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt, registryLookup);
        return nbt;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}

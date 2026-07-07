package com.akitaattribute.mobfarmblock.block;

import com.akitaattribute.mobfarmblock.behavior.AttackResult;
import com.akitaattribute.mobfarmblock.behavior.BehaviorRegistry;
import com.akitaattribute.mobfarmblock.behavior.MobFarmContext;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.akitaattribute.mobfarmblock.registry.ModItems;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class MobFarmBlock extends BaseEntityBlock {
    public static final MapCodec<MobFarmBlock> CODEC = simpleCodec(MobFarmBlock::new);

    public MobFarmBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MobFarmBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof MobFarmBlockEntity blockEntity)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.is(ModItems.CAPTURE_TOOL.get()) && CaptureToolItem.hasStoredMob(stack)) return insertCapturedMob(stack, player, blockEntity);

        StoredMob stored = blockEntity.getStored();
        if (stored.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        InteractionResult result = BehaviorRegistry.get(stored.mobId).interact(new MobFarmContext(level, pos, player, stack, stored, level.random));
        if (result.consumesAction()) blockEntity.setStored(stored);
        return result.consumesAction() ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private ItemInteractionResult insertCapturedMob(ItemStack stack, Player player, MobFarmBlockEntity blockEntity) {
        StoredMob incoming = CaptureToolItem.getStoredMob(stack);
        if (blockEntity.insertOrMerge(incoming)) {
            CaptureToolItem.clearStoredMob(stack);
            player.displayClientMessage(Component.translatable("block.mob_farm_block.mob_farm_block.inserted"), true);
            return ItemInteractionResult.SUCCESS;
        }
        player.displayClientMessage(Component.translatable("block.mob_farm_block.mob_farm_block.different_mob"), true);
        return ItemInteractionResult.FAIL;
    }

    @Override public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof MobFarmBlockEntity blockEntity)) return;
        StoredMob stored = blockEntity.getStored();
        if (stored.isEmpty()) return;
        AttackResult result = BehaviorRegistry.get(stored.mobId).attack(new MobFarmContext(level, pos, player, player.getMainHandItem(), stored, level.random));
        if (result == AttackResult.SUCCESS) blockEntity.setStored(stored);
    }
}

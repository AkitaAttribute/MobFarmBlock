package com.akitaattribute.mobfarmblock.block;

import com.akitaattribute.mobfarmblock.behavior.AttackResult;
import com.akitaattribute.mobfarmblock.behavior.BehaviorRegistry;
import com.akitaattribute.mobfarmblock.behavior.MobFarmContext;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MobFarmBlock extends BlockWithEntity {
    public static final MapCodec<MobFarmBlock> CODEC = createCodec(MobFarmBlock::new);

    public MobFarmBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MobFarmBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected ItemActionResult onUseWithItem(
            ItemStack stack,
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            Hand hand,
            BlockHitResult hit
    ) {
        if (world.isClient) {
            return ItemActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof MobFarmBlockEntity blockEntity)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isOf(ModBlocks.CAPTURE_TOOL) && CaptureToolItem.hasStoredMob(stack)) {
            return insertCapturedMob(stack, player, blockEntity);
        }

        StoredMob stored = blockEntity.getStored();
        if (stored.isEmpty()) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ActionResult result = BehaviorRegistry.get(stored.mobId).interact(
                new MobFarmContext(world, pos, player, stack, stored, world.random)
        );
        if (result.isAccepted()) {
            blockEntity.setStored(stored);
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private ItemActionResult insertCapturedMob(ItemStack stack, PlayerEntity player, MobFarmBlockEntity blockEntity) {
        StoredMob incoming = CaptureToolItem.getStoredMob(stack);
        if (blockEntity.insertOrMerge(incoming)) {
            CaptureToolItem.clearStoredMob(stack);
            player.sendMessage(Text.translatable("block.mob_farm_block.mob_farm_block.inserted"), true);
            return ItemActionResult.SUCCESS;
        }
        player.sendMessage(Text.translatable("block.mob_farm_block.mob_farm_block.different_mob"), true);
        return ItemActionResult.FAIL;
    }

    public static ActionResult attack(World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof MobFarmBlockEntity blockEntity)) {
            return ActionResult.PASS;
        }
        StoredMob stored = blockEntity.getStored();
        if (stored.isEmpty()) {
            return ActionResult.PASS;
        }
        AttackResult result = BehaviorRegistry.get(stored.mobId).attack(
                new MobFarmContext(world, pos, player, player.getMainHandStack(), stored, world.random)
        );
        if (result == AttackResult.SUCCESS) {
            blockEntity.setStored(stored);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}

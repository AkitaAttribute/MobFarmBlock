package com.akitaattribute.mobfarmblock.registry;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.block.MobFarmBlock;
import com.akitaattribute.mobfarmblock.block.MobFarmBlockEntity;
import com.akitaattribute.mobfarmblock.item.MobFarmBlockItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MobFarmBlockMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MobFarmBlockMod.MOD_ID);

    public static final DeferredHolder<Block, MobFarmBlock> MOB_FARM_BLOCK = BLOCKS.register(
            "mob_farm_block",
            () -> new MobFarmBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).strength(0.5F, 0.5F))
    );

    public static final DeferredHolder<Item, MobFarmBlockItem> MOB_FARM_BLOCK_ITEM = ModItems.ITEMS.register(
            "mob_farm_block",
            () -> new MobFarmBlockItem(MOB_FARM_BLOCK.get(), new Item.Properties())
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MobFarmBlockEntity>> MOB_FARM_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "mob_farm_block",
            () -> BlockEntityType.Builder.of(MobFarmBlockEntity::new, MOB_FARM_BLOCK.get()).build(null)
    );

    private ModBlocks() {
    }
}

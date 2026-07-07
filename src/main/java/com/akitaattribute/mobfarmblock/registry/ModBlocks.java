package com.akitaattribute.mobfarmblock.registry;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.block.MobFarmBlock;
import com.akitaattribute.mobfarmblock.block.MobFarmBlockEntity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlocks {
    public static final MobFarmBlock MOB_FARM_BLOCK = new MobFarmBlock(AbstractBlock.Settings.copy(Blocks.BEDROCK).strength(5.0F, 6.0F));
    public static final BlockItem MOB_FARM_BLOCK_ITEM = new BlockItem(MOB_FARM_BLOCK, new Item.Settings());
    public static BlockEntityType<MobFarmBlockEntity> MOB_FARM_BLOCK_ENTITY;
    public static void register() {
        Registry.register(Registries.BLOCK, MobFarmBlockMod.id("mob_farm_block"), MOB_FARM_BLOCK);
        Registry.register(Registries.ITEM, MobFarmBlockMod.id("mob_farm_block"), MOB_FARM_BLOCK_ITEM);
        MOB_FARM_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, MobFarmBlockMod.id("mob_farm_block"), BlockEntityType.Builder.create(MobFarmBlockEntity::new, MOB_FARM_BLOCK).build(null));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> e.add(MOB_FARM_BLOCK_ITEM));
    }
    private ModBlocks() {}
}

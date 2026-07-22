package com.akitaattribute.mobfarmblock.item;

import com.akitaattribute.mobfarmblock.mob.MobDisplayNames;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class MobFarmBlockItem extends BlockItem {
    public MobFarmBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        StoredMob stored = MobFarmBlockItemData.getStoredMob(stack);
        return stored == null || stored.isEmpty() ? super.getName(stack) : Component.literal(MobDisplayNames.penName(stored));
    }
}

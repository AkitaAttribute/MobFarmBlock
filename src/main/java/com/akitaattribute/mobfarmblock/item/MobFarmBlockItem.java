package com.akitaattribute.mobfarmblock.item;

import java.util.List;

import com.akitaattribute.mobfarmblock.mob.MobDisplayNames;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        MobFarmItemTooltip.appendStoredMob(MobFarmBlockItemData.getStoredMob(stack), tooltip);
    }
}

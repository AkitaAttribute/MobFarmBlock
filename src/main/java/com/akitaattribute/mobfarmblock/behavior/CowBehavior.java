package com.akitaattribute.mobfarmblock.behavior;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CowBehavior extends GenericMobBehavior {
    @Override public InteractionResult interact(MobFarmContext context) {
        if (context.heldItem().is(Items.BUCKET)) {
            context.heldItem().shrink(1);
            BehaviorUtil.output(context, new ItemStack(Items.MILK_BUCKET));
            return InteractionResult.SUCCESS;
        }
        if (context.heldItem().is(Items.WHEAT)) {
            context.heldItem().shrink(1);
            context.stored().count++;
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}

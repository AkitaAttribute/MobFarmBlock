package com.akitaattribute.mobfarmblock.behavior;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ChickenBehavior extends GenericMobBehavior {
    private static final ResourceLocation EGG_ACTION = MobFarmBlockMod.id("egg");
    private static final long EGG_COOLDOWN_TICKS = 6_000L;

    @Override public InteractionResult interact(MobFarmContext context) {
        if (isSeed(context)) {
            context.heldItem().shrink(1);
            context.stored().count++;
            return InteractionResult.SUCCESS;
        }
        long now = context.level().getGameTime();
        if (context.stored().ready(EGG_ACTION, now)) {
            BehaviorUtil.output(context, new ItemStack(Items.EGG));
            context.stored().setCooldown(EGG_ACTION, now, EGG_COOLDOWN_TICKS);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static boolean isSeed(MobFarmContext context) {
        return context.heldItem().is(Items.WHEAT_SEEDS) || context.heldItem().is(Items.PUMPKIN_SEEDS) || context.heldItem().is(Items.MELON_SEEDS) || context.heldItem().is(Items.BEETROOT_SEEDS);
    }
}

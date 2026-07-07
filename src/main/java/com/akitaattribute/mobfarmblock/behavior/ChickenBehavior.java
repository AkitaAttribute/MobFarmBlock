package com.akitaattribute.mobfarmblock.behavior;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;

import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public class ChickenBehavior extends GenericMobBehavior {
    private static final Identifier EGG_ACTION = MobFarmBlockMod.id("egg");
    private static final long EGG_COOLDOWN_TICKS = 6_000L;

    @Override
    public ActionResult interact(MobFarmContext context) {
        if (isSeed(context)) {
            context.heldItem().decrement(1);
            context.stored().count++;
            return ActionResult.SUCCESS;
        }

        long now = context.level().getTime();
        if (context.stored().ready(EGG_ACTION, now)) {
            BehaviorUtil.output(context, Items.EGG.getDefaultStack());
            context.stored().setCooldown(EGG_ACTION, now, EGG_COOLDOWN_TICKS);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private static boolean isSeed(MobFarmContext context) {
        return context.heldItem().isOf(Items.WHEAT_SEEDS)
                || context.heldItem().isOf(Items.PUMPKIN_SEEDS)
                || context.heldItem().isOf(Items.MELON_SEEDS)
                || context.heldItem().isOf(Items.BEETROOT_SEEDS);
    }
}

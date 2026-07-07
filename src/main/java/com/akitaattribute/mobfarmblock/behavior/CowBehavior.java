package com.akitaattribute.mobfarmblock.behavior;

import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;

public class CowBehavior extends GenericMobBehavior {
    @Override
    public ActionResult interact(MobFarmContext context) {
        if (context.heldItem().isOf(Items.BUCKET)) {
            context.heldItem().decrement(1);
            BehaviorUtil.output(context, Items.MILK_BUCKET.getDefaultStack());
            return ActionResult.SUCCESS;
        }
        if (context.heldItem().isOf(Items.WHEAT)) {
            context.heldItem().decrement(1);
            context.stored().count++;
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}

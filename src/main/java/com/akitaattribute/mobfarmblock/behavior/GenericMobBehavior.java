package com.akitaattribute.mobfarmblock.behavior;

import net.minecraft.util.ActionResult;

public class GenericMobBehavior implements StoredMobBehavior {
    @Override
    public ActionResult interact(MobFarmContext context) {
        return ActionResult.PASS;
    }

    @Override
    public AttackResult attack(MobFarmContext context) {
        return BehaviorUtil.attack(context);
    }
}

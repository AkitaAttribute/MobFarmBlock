package com.akitaattribute.mobfarmblock.behavior;

import net.minecraft.util.ActionResult;

public interface StoredMobBehavior {
    ActionResult interact(MobFarmContext context);

    AttackResult attack(MobFarmContext context);
}

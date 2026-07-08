package com.akitaattribute.mobfarmblock.behavior;

import net.minecraft.world.InteractionResult;

public interface StoredMobBehavior {
    InteractionResult interact(MobFarmContext context);
    AttackResult attack(MobFarmContext context);
}

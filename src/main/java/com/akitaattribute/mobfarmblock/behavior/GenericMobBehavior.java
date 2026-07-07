package com.akitaattribute.mobfarmblock.behavior;

import net.minecraft.world.InteractionResult;

public class GenericMobBehavior implements StoredMobBehavior {
    @Override public InteractionResult interact(MobFarmContext context) { return InteractionResult.PASS; }
    @Override public AttackResult attack(MobFarmContext context) { return BehaviorUtil.attack(context); }
}

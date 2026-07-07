package com.akitaattribute.mobfarmblock.behavior;
import net.minecraft.util.ActionResult;
public class GenericMobBehavior implements StoredMobBehavior { public ActionResult interact(MobFarmContext c){return ActionResult.PASS;} public AttackResult attack(MobFarmContext c){return BehaviorUtil.attack(c);} }

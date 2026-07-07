package com.akitaattribute.mobfarmblock.mob;
import net.minecraft.nbt.NbtCompound; import net.minecraft.util.Identifier;
public record DropRule(Identifier itemId,double chance,int minCount,int maxCount,boolean affectedByLooting,double lootingChanceBonus,int lootingMaxBonus){
 public NbtCompound toNbt(){var n=new NbtCompound();n.putString("itemId",itemId.toString());n.putDouble("chance",chance);n.putInt("minCount",minCount);n.putInt("maxCount",maxCount);n.putBoolean("affectedByLooting",affectedByLooting);n.putDouble("lootingChanceBonus",lootingChanceBonus);n.putInt("lootingMaxBonus",lootingMaxBonus);return n;}
 public static DropRule fromNbt(NbtCompound n){return new DropRule(Identifier.of(n.getString("itemId")),n.getDouble("chance"),n.getInt("minCount"),n.getInt("maxCount"),n.getBoolean("affectedByLooting"),n.getDouble("lootingChanceBonus"),n.getInt("lootingMaxBonus"));}
}

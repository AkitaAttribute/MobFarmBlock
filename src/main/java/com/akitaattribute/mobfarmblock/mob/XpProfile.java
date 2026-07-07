package com.akitaattribute.mobfarmblock.mob;
import net.minecraft.nbt.NbtCompound;
public record XpProfile(int minXp, int maxXp) { public static final XpProfile NONE=new XpProfile(0,0); public NbtCompound toNbt(){var n=new NbtCompound();n.putInt("minXp",minXp);n.putInt("maxXp",maxXp);return n;} public static XpProfile fromNbt(NbtCompound n){return new XpProfile(n.getInt("minXp"),n.getInt("maxXp"));} }

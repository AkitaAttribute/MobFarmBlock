package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/** Display-only identity for a stored mob profile; never contains UUID, position, AI, equipment, or full entity NBT. */
public record DisplaySnapshot(Identifier entityTypeId, Identifier textureId, String variantKey, String colorKey, boolean baby, float scale) {
    public static final DisplaySnapshot EMPTY = new DisplaySnapshot(Identifier.of("minecraft:pig"), Identifier.of("minecraft:textures/entity/pig/pig.png"), "", "", false, 1.0F);
    public NbtCompound toNbt() { var n=new NbtCompound(); n.putString("entityTypeId", entityTypeId.toString()); n.putString("textureId", textureId.toString()); n.putString("variantKey", variantKey); n.putString("colorKey", colorKey); n.putBoolean("baby", baby); n.putFloat("scale", scale); return n; }
    public static DisplaySnapshot fromNbt(NbtCompound n) { return new DisplaySnapshot(Identifier.of(n.getString("entityTypeId")), Identifier.of(n.getString("textureId")), n.getString("variantKey"), n.getString("colorKey"), n.getBoolean("baby"), n.contains("scale") ? n.getFloat("scale") : 1.0F); }
}

package com.akitaattribute.mobfarmblock.behavior;
import com.akitaattribute.mobfarmblock.mob.StoredMob; import net.minecraft.entity.player.PlayerEntity; import net.minecraft.item.ItemStack; import net.minecraft.util.math.BlockPos; import net.minecraft.util.math.random.Random; import net.minecraft.world.World;
public record MobFarmContext(World level, BlockPos pos, PlayerEntity player, ItemStack heldItem, StoredMob stored, Random random) {}

package com.akitaattribute.mobfarmblock.behavior;

import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record MobFarmContext(Level level, BlockPos pos, Player player, ItemStack heldItem, StoredMob stored, RandomSource random) {
}

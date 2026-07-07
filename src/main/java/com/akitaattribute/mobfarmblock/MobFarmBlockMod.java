package com.akitaattribute.mobfarmblock;

import com.akitaattribute.mobfarmblock.behavior.BehaviorRegistry;
import com.akitaattribute.mobfarmblock.data.MobProfileReloadListener;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import com.akitaattribute.mobfarmblock.block.MobFarmBlock;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class MobFarmBlockMod implements ModInitializer {
    public static final String MOD_ID = "mob_farm_block";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String path) { return Identifier.of(MOD_ID, path); }
    @Override public void onInitialize() {
        ModBlocks.register();
        BehaviorRegistry.registerDefaults();
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new MobProfileReloadListener());
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> world.getBlockState(pos).isOf(ModBlocks.MOB_FARM_BLOCK) ? MobFarmBlock.attack(world, pos, player) : net.minecraft.util.ActionResult.PASS);
    }
}

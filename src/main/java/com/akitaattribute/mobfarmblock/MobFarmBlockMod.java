package com.akitaattribute.mobfarmblock;

import com.akitaattribute.mobfarmblock.behavior.BehaviorRegistry;
import com.akitaattribute.mobfarmblock.behavior.InteractionMethodRegistry;
import com.akitaattribute.mobfarmblock.data.MobProfileReloadListener;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;
import com.akitaattribute.mobfarmblock.registry.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;

@Mod(MobFarmBlockMod.MOD_ID)
public class MobFarmBlockMod {
    public static final String MOD_ID = "mob_farm_block";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MobFarmBlockMod(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::addCreativeTabItems);

        BehaviorRegistry.registerDefaults();
        InteractionMethodRegistry.registerDefaults();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.MOB_FARM_BLOCK_ITEM.get());
            event.accept(ModItems.CAPTURE_TOOL.get());
        }
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MobProfileReloadListener());
    }
}

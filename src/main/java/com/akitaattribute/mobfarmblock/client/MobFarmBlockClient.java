package com.akitaattribute.mobfarmblock.client;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;
import com.akitaattribute.mobfarmblock.registry.ModItems;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = MobFarmBlockMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MobFarmBlockClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.CAPTURE_TOOL.get(),
                MobFarmBlockMod.id("captured_mob"),
                (stack, level, entity, seed) -> CaptureToolItem.hasStoredMob(stack)
                        ? CaptureToolItemIconResolver.modelIndex(CaptureToolItem.getStoredMob(stack).mobId)
                        : 0.0F
        ));
    }

    private MobFarmBlockClient() {}
}

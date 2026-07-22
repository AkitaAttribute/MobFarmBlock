package com.akitaattribute.mobfarmblock.client;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.tick.ClientTickEvent;

public final class MobFarmClientKeys {
    public static final KeyMapping TOGGLE_PENS_ALWAYS_SHOW_SMALL = new KeyMapping(
            "key.mob_farm_block.togglePensAlwaysShowSmall",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            "key.categories.mob_farm_block"
    );

    @EventBusSubscriber(modid = MobFarmBlockMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_PENS_ALWAYS_SHOW_SMALL);
        }
    }

    @EventBusSubscriber(modid = MobFarmBlockMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            while (TOGGLE_PENS_ALWAYS_SHOW_SMALL.consumeClick()) {
                boolean next = !MobFarmConfig.PENS_ALWAYS_SHOW_SMALL.get();
                MobFarmConfig.PENS_ALWAYS_SHOW_SMALL.set(next);
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.literal("Pens Always Show Small: " + next), true);
                }
            }
        }
    }

    private MobFarmClientKeys() {}
}

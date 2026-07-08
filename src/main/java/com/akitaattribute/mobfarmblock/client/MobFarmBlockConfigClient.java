package com.akitaattribute.mobfarmblock.client;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MobFarmBlockMod.MOD_ID, dist = Dist.CLIENT)
public final class MobFarmBlockConfigClient {
    public MobFarmBlockConfigClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}

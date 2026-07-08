package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

public final class DropProfileRegistry {
    private static final Map<ResourceLocation, DropProfile> PROFILES = new HashMap<>();
    static { registerDefaults(); }
    public static void put(ResourceLocation id, DropProfile profile) { PROFILES.put(id, profile); }
    public static DropProfile get(ResourceLocation id) { return PROFILES.getOrDefault(id, DropProfile.EMPTY); }

    private static void registerDefaults() {
        put(id("cow"), profile(xp(1,3), drop("leather",1,0,2,true,0,1), drop("beef",1,1,3,true,0,1)));
        put(id("mooshroom"), get(id("cow")));
        put(id("pig"), profile(xp(1,3), drop("porkchop",1,1,3,true,0,1)));
        put(id("sheep"), profile(xp(1,3), drop("mutton",1,1,2,true,0,1)));
        put(id("chicken"), profile(xp(1,3), drop("feather",1,0,2,true,0,1), drop("chicken",1,1,1,true,0,1)));
        put(id("rabbit"), profile(xp(1,3), drop("rabbit",1,0,1,true,0,1), drop("rabbit_hide",1,0,1,true,0,1), drop("rabbit_foot",0.10,1,1,true,0.03,0)));
        put(id("cat"), DropProfile.EMPTY); put(id("wolf"), DropProfile.EMPTY);
        put(id("horse"), profile(xp(1,3), drop("leather",1,0,2,true,0,1)));
        put(id("donkey"), get(id("horse"))); put(id("mule"), get(id("horse"))); put(id("llama"), get(id("horse"))); put(id("trader_llama"), get(id("horse")));
        put(id("zombie"), profile(xp(5,5), drop("rotten_flesh",1,0,2,true,0,1), drop("iron_ingot",0.025,1,1,true,0.01,0), drop("carrot",0.025,1,1,true,0.01,0), drop("potato",0.025,1,1,true,0.01,0)));
        put(id("husk"), get(id("zombie"))); put(id("drowned"), profile(xp(5,5), drop("rotten_flesh",1,0,2,true,0,1), drop("copper_ingot",0.11,1,1,true,0.02,0)));
        put(id("skeleton"), profile(xp(5,5), drop("bone",1,0,2,true,0,1), drop("arrow",1,0,2,true,0,1)));
        put(id("stray"), profile(xp(5,5), drop("bone",1,0,2,true,0,1), drop("arrow",1,0,2,true,0,1), drop("tipped_arrow",0.5,0,1,true,0,1)));
        put(id("creeper"), profile(xp(5,5), drop("gunpowder",1,0,2,true,0,1)));
        put(id("spider"), profile(xp(5,5), drop("string",1,0,2,true,0,1), drop("spider_eye",0.33,1,1,true,0.0,0)));
        put(id("cave_spider"), get(id("spider")));
        put(id("enderman"), profile(xp(5,5), drop("ender_pearl",1,0,1,true,0,1)));
        put(id("witch"), profile(xp(5,5), drop("glowstone_dust",0.5,0,2,true,0,1), drop("redstone",0.5,0,2,true,0,1), drop("gunpowder",0.5,0,2,true,0,1), drop("stick",0.5,0,2,true,0,1), drop("spider_eye",0.5,0,2,true,0,1), drop("glass_bottle",0.5,0,2,true,0,1), drop("sugar",0.5,0,2,true,0,1)));
        put(id("slime"), profile(xp(1,4), drop("slime_ball",1,0,2,true,0,1)));
        put(id("pillager"), profile(xp(5,5), drop("arrow",1,0,2,true,0,1)));
        put(id("vindicator"), profile(xp(5,5), drop("emerald",0.5,0,1,true,0.01,1)));
        put(id("evoker"), profile(xp(10,10), drop("totem_of_undying",1,1,1,false,0,0), drop("emerald",1,0,1,true,0.01,1)));
        put(id("ravager"), profile(xp(20,20), drop("saddle",1,1,1,false,0,0)));
        put(id("iron_golem"), profile(xp(0,0), drop("iron_ingot",1,3,5,false,0,0), drop("poppy",1,0,2,false,0,0)));
        put(id("snow_golem"), profile(xp(0,0), drop("snowball",1,0,15,false,0,0)));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }
    private static DropRule drop(String itemId, double chance, int min, int max, boolean affectedByLooting, double lootingChanceBonus, int lootingMaxBonus) { return new DropRule(ResourceLocation.withDefaultNamespace(itemId), chance, min, max, affectedByLooting, lootingChanceBonus, lootingMaxBonus); }
    private static XpProfile xp(int min, int max) { return new XpProfile(min, max); }
    private static DropProfile profile(XpProfile xp, DropRule... rules) { return new DropProfile(List.of(rules), xp); }
    private DropProfileRegistry() {}
}

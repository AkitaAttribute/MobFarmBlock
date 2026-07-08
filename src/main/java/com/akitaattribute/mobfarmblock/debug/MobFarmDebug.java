package com.akitaattribute.mobfarmblock.debug;

import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class MobFarmDebug {
    public static void send(Player player, Component message) {
        if (player != null && MobFarmConfig.DEBUG_CHAT_MESSAGES.get()) player.displayClientMessage(message, false);
    }

    public static void captureSuccess(Player player, LivingEntity entity, StoredMob stored) {
        send(player, Component.literal("Mob Farm Block Debug:\nCaptured entity"
                + "\n- entity type: " + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                + "\n- detected kind: " + stored.kind
                + "\n- species id: " + (stored.speciesId == null ? "unavailable" : stored.speciesId)
                + "\n- display texture: " + stored.display.textureId()
                + "\n- variant key: " + stored.display.variantKey()
                + "\n- color key: " + stored.display.colorKey()
                + "\n- baby: " + stored.display.baby()
                + "\n- drop profile source: " + stored.dropProfileSource
                + "\n- drop rule count: " + stored.dropProfile.drops().size()
                + "\n- stored count: " + stored.count));
    }

    public static void captureRejected(Player player, LivingEntity entity, String reason) {
        send(player, Component.literal("Mob Farm Block Debug:\nCapture rejected"
                + "\n- reason: " + reason
                + "\n- entity type: " + (entity == null ? "unavailable" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))
                + "\n- entity class: " + (entity == null ? "unavailable" : entity.getClass().getName())));
    }

    public static void insertionSuccess(Player player, StoredMob incoming, StoredMob previous, StoredMob current, boolean merged) {
        send(player, Component.literal("Mob Farm Block Debug:\nInserted stored mob"
                + "\n- incoming mob id: " + incoming.mobId
                + "\n- incoming kind: " + incoming.kind
                + "\n- incoming count: " + incoming.count
                + "\n- block previous mob id: " + (previous == null || previous.isEmpty() ? "empty" : previous.mobId)
                + "\n- block previous kind: " + (previous == null || previous.isEmpty() ? "empty" : previous.kind)
                + "\n- block new count: " + current.count
                + "\n- merged: " + merged));
    }

    public static void insertionRejected(Player player, StoredMob incoming, StoredMob current, String reason) {
        send(player, Component.literal("Mob Farm Block Debug:\nInsertion rejected"
                + "\n- incoming mob id: " + (incoming == null ? "empty" : incoming.mobId)
                + "\n- incoming kind: " + (incoming == null ? "empty" : incoming.kind)
                + "\n- block mob id: " + (current == null || current.isEmpty() ? "empty" : current.mobId)
                + "\n- block kind: " + (current == null || current.isEmpty() ? "empty" : current.kind)
                + "\n- reason: " + reason));
    }

    public static void breeding(Player player, String message) {
        send(player, Component.literal("Mob Farm Block Debug:\nBreeding\n" + message));
    }

    private MobFarmDebug() {}
}

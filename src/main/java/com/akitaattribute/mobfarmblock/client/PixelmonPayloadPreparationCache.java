package com.akitaattribute.mobfarmblock.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

/** Prepares legacy string Pixelmon entity payloads away from the render thread. */
public final class PixelmonPayloadPreparationCache {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ThreadFactory THREAD_FACTORY = task -> {
        Thread thread = new Thread(task, "mob-farm-pixelmon-prepare-" + THREAD_IDS.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    };
    private static final int THREAD_COUNT = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT, THREAD_FACTORY);
    private static final Map<String, CompletableFuture<CompoundTag>> PREPARING = new ConcurrentHashMap<>();

    public static boolean ensurePrepared(PixelmonRenderSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasEntityPayload()) return true;
        if (snapshot.cachedEntityPayload().isPresent()) return true;

        String payload = snapshot.payload();
        CompletableFuture<CompoundTag> future = PREPARING.computeIfAbsent(payload, PixelmonPayloadPreparationCache::startPreparation);
        if (!future.isDone()) return false;
        if (future.isCompletedExceptionally()) return true;

        CompoundTag prepared = future.getNow(null);
        if (prepared == null) return true;
        snapshot.cacheEntityPayload(prepared);
        return true;
    }

    public static CompoundTag preparedOrParse(String payload) throws CommandSyntaxException {
        return PixelmonRenderSnapshot.cachedEntityPayload(payload).orElseGet(() -> parseUnchecked(payload));
    }

    private static CompletableFuture<CompoundTag> startPreparation(String payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TagParser.parseTag(payload);
            } catch (CommandSyntaxException error) {
                throw new CompletionException(error);
            }
        }, EXECUTOR).whenComplete((tag, error) -> {
            if (error != null) {
                MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon payload preparation failed: {}", error.toString());
            } else if (tag != null) {
                // The snapshot instance that requested this will copy the result into the shared persistent-payload cache.
            }
        });
    }

    private static CompoundTag parseUnchecked(String payload) {
        try {
            return TagParser.parseTag(payload);
        } catch (CommandSyntaxException error) {
            throw new CompletionException(error);
        }
    }

    private PixelmonPayloadPreparationCache() {}
}

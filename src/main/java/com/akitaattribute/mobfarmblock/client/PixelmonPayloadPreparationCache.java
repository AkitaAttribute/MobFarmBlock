package com.akitaattribute.mobfarmblock.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;

/** Prepares and persistently caches Pixelmon entity payloads away from the render thread. */
public final class PixelmonPayloadPreparationCache {
    private static final int CACHE_VERSION = 1;
    private static final long MAX_CACHE_NBT_BYTES = 64L * 1024L * 1024L;
    private static final Path CACHE_DIR = Path.of("config", "mob_farm_block", "cache", "pixelmon").toAbsolutePath();
    private static final Path LOAD_PROFILE_LOG = Path.of("config", "mob_farm_block", "debug", "pixelmon_pen_load_profile.jsonl").toAbsolutePath();
    private static final Object LOG_LOCK = new Object();

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
    private static final Set<String> PERSIST_REQUESTED = ConcurrentHashMap.newKeySet();

    public static boolean ensurePrepared(PixelmonRenderSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasEntityPayload()) return true;

        String payload = snapshot.payload();
        Optional<CompoundTag> alreadyPrepared = snapshot.cachedEntityPayload();
        if (alreadyPrepared.isPresent()) {
            persistPreparedAsync(payload, alreadyPrepared.get());
            return true;
        }

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
            String hash = payloadHash(payload);
            long started = System.nanoTime();

            Optional<CompoundTag> diskCached = readDiskCache(hash);
            if (diskCached.isPresent()) {
                logProfile("PERSISTENT_CACHE_HIT", hash, System.nanoTime() - started, payload.length());
                return diskCached.get();
            }

            long parseStarted = System.nanoTime();
            CompoundTag parsed;
            try {
                parsed = TagParser.parseTag(payload);
            } catch (CommandSyntaxException error) {
                logProfile("CACHE_MISS_PARSE_FAILED", hash, System.nanoTime() - parseStarted, payload.length());
                throw new CompletionException(error);
            }

            long writeStarted = System.nanoTime();
            if (writeDiskCache(hash, parsed)) {
                PERSIST_REQUESTED.add(hash);
                logProfile("CACHE_MISS_PARSED_AND_PERSISTED", hash, System.nanoTime() - parseStarted, payload.length());
            } else {
                logProfile("CACHE_MISS_PARSED_WRITE_FAILED", hash, System.nanoTime() - writeStarted, payload.length());
            }
            return parsed;
        }, EXECUTOR).whenComplete((tag, error) -> {
            if (error != null) {
                MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon payload preparation failed: {}", error.toString());
            }
        });
    }

    private static void persistPreparedAsync(String payload, CompoundTag prepared) {
        if (payload == null || payload.isBlank() || prepared == null || prepared.isEmpty()) return;
        String hash = payloadHash(payload);
        if (!PERSIST_REQUESTED.add(hash)) return;

        CompletableFuture.runAsync(() -> {
            Path file = cacheFile(hash);
            if (Files.isRegularFile(file)) {
                logProfile("PERSISTENT_CACHE_ALREADY_PRESENT", hash, 0L, payload.length());
                return;
            }
            long started = System.nanoTime();
            if (writeDiskCache(hash, prepared)) {
                logProfile("EMBEDDED_PAYLOAD_TAG_PERSISTED", hash, System.nanoTime() - started, payload.length());
            } else {
                PERSIST_REQUESTED.remove(hash);
                logProfile("EMBEDDED_PAYLOAD_TAG_WRITE_FAILED", hash, System.nanoTime() - started, payload.length());
            }
        }, EXECUTOR).exceptionally(error -> {
            PERSIST_REQUESTED.remove(hash);
            MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon persistent payload write failed: {}", error.toString());
            return null;
        });
    }

    private static Optional<CompoundTag> readDiskCache(String hash) {
        Path file = cacheFile(hash);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_CACHE_NBT_BYTES));
            if (root.getInt("cacheVersion") != CACHE_VERSION) return Optional.empty();
            if (!hash.equals(root.getString("payloadSha256"))) return Optional.empty();
            if (!root.contains("payloadTag")) return Optional.empty();
            CompoundTag payloadTag = root.getCompound("payloadTag");
            return payloadTag.isEmpty() ? Optional.empty() : Optional.of(payloadTag.copy());
        } catch (Throwable error) {
            MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon persistent payload read failed for {}: {}", file, error.toString());
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
            return Optional.empty();
        }
    }

    private static boolean writeDiskCache(String hash, CompoundTag payloadTag) {
        Path file = cacheFile(hash);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + Thread.currentThread().getId());
        try {
            Files.createDirectories(CACHE_DIR);
            CompoundTag root = new CompoundTag();
            root.putInt("cacheVersion", CACHE_VERSION);
            root.putString("payloadSha256", hash);
            root.put("payloadTag", payloadTag.copy());
            NbtIo.writeCompressed(root, temporary);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Throwable error) {
            MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon persistent payload write failed for {}: {}", file, error.toString());
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    private static Path cacheFile(String hash) {
        return CACHE_DIR.resolve(hash + ".nbt");
    }

    private static String payloadHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void logProfile(String event, String hash, long durationNanos, int payloadCharacters) {
        double durationMs = durationNanos / 1_000_000.0D;
        String line = "{\"event\":\"" + event + "\",\"payloadHash\":\"" + hash + "\",\"durationMs\":"
                + durationMs + ",\"payloadCharacters\":" + payloadCharacters + "}" + System.lineSeparator();
        synchronized (LOG_LOCK) {
            try {
                Files.createDirectories(LOAD_PROFILE_LOG.getParent());
                Files.writeString(LOAD_PROFILE_LOG, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException error) {
                MobFarmBlockMod.LOGGER.debug("Mob Farm Block Pixelmon load profile write failed: {}", error.toString());
            }
        }
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

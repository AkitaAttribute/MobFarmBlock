package com.akitaattribute.mobfarmblock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PixelmonMixinTargetTest {
    private static final String DROP_QUERY_LIST_TARGET = "com.pixelmonmod.pixelmon.entities.pixelmon.drops.DropItemQueryList";
    private static final String DROP_REGISTRY_TARGET = "com.pixelmonmod.pixelmon.entities.npcs.registry.DropItemRegistry";

    private static final String[] KNOWN_BAD_DROP_QUERY_LIST_TARGETS = {
            "com.pixelmonmod.pixelmon.api.drops.DropItemQueryList",
            "com.pixelmonmod.pixelmon.entities.npcs.registry.DropItemQueryList"
    };

    @Test
    void dropItemQueryListMixinTargetsKnownPixelmonLootUiClass() throws IOException {
        String mixinSource = read("src/main/java/com/akitaattribute/mobfarmblock/mixin/PixelmonDropItemQueryListMixin.java");

        assertTrue(mixinSource.contains("@Mixin(targets = \"" + DROP_QUERY_LIST_TARGET + "\""),
                "Pixelmon loot UI mixin must target the DropItemQueryList class observed in Pixelmon drop bytecode diagnostics.");
        for (String badTarget : KNOWN_BAD_DROP_QUERY_LIST_TARGETS) {
            assertFalse(mixinSource.contains(badTarget), "Rejected stale Pixelmon DropItemQueryList mixin target: " + badTarget);
        }
    }

    @Test
    void dropItemQueryListCallbacksAreStaticBecausePixelmonRegisterIsStatic() throws IOException {
        String mixinSource = read("src/main/java/com/akitaattribute/mobfarmblock/mixin/PixelmonDropItemQueryListMixin.java");

        assertTrue(mixinSource.contains("private static void mobFarmBlock$logRegisterHead"),
                "Pixelmon DropItemQueryList.register is static, so the HEAD injector callback must also be static.");
        assertTrue(mixinSource.contains("private static void mobFarmBlock$appendCaptureTool"),
                "Pixelmon DropItemQueryList.register is static, so the TAIL injector callback must also be static.");
        assertFalse(mixinSource.contains("private void mobFarmBlock$logRegisterHead"),
                "A non-static HEAD injector causes Mixin InvalidInjectionException against static Pixelmon register(...).");
        assertFalse(mixinSource.contains("private void mobFarmBlock$appendCaptureTool"),
                "A non-static TAIL injector causes Mixin InvalidInjectionException against static Pixelmon register(...).");
    }

    @Test
    void mixinConfigRegistersDropItemQueryListMixin() throws IOException {
        String mixinConfig = read("src/main/resources/mob_farm_block.mixins.json");

        assertTrue(mixinConfig.contains("PixelmonDropItemQueryListMixin"),
                "The Mob Farm mixin config must include the Pixelmon loot UI mixin.");
    }

    @Test
    void dropRegistryReflectionRemainsSeparateFromDropQueryListMixinTarget() throws IOException {
        String fallbackSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonDropFallback.java");
        String mixinSource = read("src/main/java/com/akitaattribute/mobfarmblock/mixin/PixelmonDropItemQueryListMixin.java");

        assertTrue(fallbackSource.contains(DROP_REGISTRY_TARGET),
                "Capture/drop profile reflection should keep using Pixelmon DropItemRegistry.");
        assertFalse(mixinSource.contains("com.pixelmonmod.pixelmon.entities.npcs.registry.DropItemQueryList"),
                "The DropItemRegistry package must not be reused as the DropItemQueryList mixin package.");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}

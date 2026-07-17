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
    private static final String DROPPED_ITEM_TARGET = "com.pixelmonmod.pixelmon.entities.pixelmon.drops.DroppedItem";

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

        assertTrue(mixinSource.contains("private static void mobFarmBlock$appendCaptureToolBeforeRegister"),
                "Pixelmon DropItemQueryList.register is static, so the HEAD injector callback must also be static.");
        assertTrue(mixinSource.contains("private static void mobFarmBlock$logRegisterTail"),
                "Pixelmon DropItemQueryList.register is static, so the TAIL logging callback must also be static.");
        assertFalse(mixinSource.contains("private void mobFarmBlock$"),
                "Non-static callbacks cause Mixin InvalidInjectionException against static Pixelmon register(...).");
    }

    @Test
    void captureToolIsInjectedBeforePixelmonRegistersLootUi() throws IOException {
        String mixinSource = read("src/main/java/com/akitaattribute/mobfarmblock/mixin/PixelmonDropItemQueryListMixin.java");

        assertTrue(mixinSource.contains("@Inject(method = \"register\", at = @At(\"HEAD\"), remap = false)"),
                "The Capture Tool must be added before Pixelmon registers/displays the loot UI list.");
        assertTrue(mixinSource.contains("mobFarmBlock$appendCaptureToolBeforeRegister"),
                "The HEAD callback should append the Capture Tool before Pixelmon builds the visible loot UI state.");
        assertTrue(mixinSource.contains("@Inject(method = \"register\", at = @At(\"TAIL\"), remap = false)"),
                "A TAIL callback may remain for final state logging.");
        assertFalse(mixinSource.contains("TAIL before append"),
                "Appending only at TAIL makes Take All receive the Capture Tool while the visible Pixelmon loot UI omits it.");
    }

    @Test
    void injectedLootUsesPixelmonDroppedItemWrapperNotRawItemStack() throws IOException {
        String injectorSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonLootCaptureToolInjector.java");

        assertTrue(injectorSource.contains(DROPPED_ITEM_TARGET),
                "Pixelmon loot lists contain DroppedItem entries, not raw Minecraft ItemStack entries.");
        assertTrue(injectorSource.contains("getConstructor(ItemStack.class, int.class)"),
                "The Capture Tool must be wrapped with Pixelmon DroppedItem(ItemStack, int) before adding it to DropItemQueryList.");
        assertFalse(injectorSource.contains("drops.add(captureTool);"),
                "Adding a raw ItemStack to the Pixelmon loot list causes ClassCastException and breaks normal Pixelmon drops.");
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

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
        assertTrue(mixinSource.contains("private static void mobFarmBlock$updateCaptureToolAfterRegister"),
                "Pixelmon DropItemQueryList.register is static, so the TAIL callback must also be static.");
        assertFalse(mixinSource.contains("private void mobFarmBlock$"),
                "Non-static callbacks cause Mixin InvalidInjectionException against static Pixelmon register(...).");
    }

    @Test
    void captureToolIsInjectedBeforePixelmonRegistersLootUiThenUpdatedAfterDropsExist() throws IOException {
        String mixinSource = read("src/main/java/com/akitaattribute/mobfarmblock/mixin/PixelmonDropItemQueryListMixin.java");

        assertTrue(mixinSource.contains("@Inject(method = \"register\", at = @At(\"HEAD\"), remap = false)"),
                "The Capture Tool must be added before Pixelmon registers/displays the loot UI list.");
        assertTrue(mixinSource.contains("mobFarmBlock$appendCaptureToolBeforeRegister"),
                "The HEAD callback should append the Capture Tool before Pixelmon builds the visible loot UI state.");
        assertTrue(mixinSource.contains("@Inject(method = \"register\", at = @At(\"TAIL\"), remap = false)"),
                "A TAIL callback must remain to replace the pending tool profile with the observed battle loot.");
        assertTrue(mixinSource.contains("updateCaptureToolObservedLoot"),
                "The TAIL callback should update the Capture Tool from the actual generated Pixelmon loot list.");
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
    void lootCaptureToolsAreConfigurableAndConsumedOnDeposit() throws IOException {
        String configSource = read("src/main/java/com/akitaattribute/mobfarmblock/config/MobFarmConfig.java");
        String injectorSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonLootCaptureToolInjector.java");
        String blockSource = read("src/main/java/com/akitaattribute/mobfarmblock/block/MobFarmBlock.java");

        assertTrue(configSource.contains("PIXELMON_CAPTURE_TOOL_DROP_CHANCE"),
                "Pixelmon Capture Tool loot injection should be governed by a common NeoForge config value.");
        assertTrue(configSource.contains("defineInRange(\"pixelmonCaptureToolDropChance\", 0.001D, 0.0D, 1.0D)"),
                "The Pixelmon Capture Tool loot chance should default to 1/1000 and allow fractional double precision down to zero.");
        assertTrue(injectorSource.contains("MobFarmConfig.PIXELMON_CAPTURE_TOOL_DROP_CHANCE.get()"),
                "The Pixelmon loot injector must roll against the configured chance before adding the Capture Tool.");
        assertTrue(injectorSource.contains("CaptureToolItem.setDiscardOnDeposit(captureTool, true)"),
                "Capture Tools created by Pixelmon loot should be marked one-shot so they do not become reusable empty tools.");
        assertTrue(blockSource.contains("CaptureToolItem.shouldDiscardOnDeposit(stack)"),
                "Depositing a Pixelmon-loot Capture Tool should consume the stack instead of clearing it to an empty tool.");
    }

    @Test
    void pixelmonPensUseEmptyHandDropHarvestAndProcessingOnlyAwardsXp() throws IOException {
        String factorySource = read("src/main/java/com/akitaattribute/mobfarmblock/mob/MobProfileFactory.java");
        String interactionSource = read("src/main/java/com/akitaattribute/mobfarmblock/behavior/InteractionMethodRegistry.java");
        String behaviorSource = read("src/main/java/com/akitaattribute/mobfarmblock/behavior/BehaviorUtil.java");

        assertTrue(factorySource.contains("\"pixelmon:pixelmon\".equals(id)"),
                "Stored Pixelmon should receive a built-in pen interaction.");
        assertTrue(factorySource.contains("MobFarmBlockMod.id(\"pixelmon_drops\")"),
                "Stored Pixelmon should use the empty-hand Pixelmon drop harvest interaction.");
        assertTrue(interactionSource.contains("PIXELMON_DROPS"),
                "The Pixelmon drop harvest method must be registered.");
        assertTrue(interactionSource.contains("definition.methodId().equals(EGG) || definition.methodId().equals(PIXELMON_DROPS)"),
                "Pixelmon drop harvesting should match an empty hand like chicken eggs.");
        assertTrue(interactionSource.contains("context.stored().setCooldown(action, now, definition.cooldownTicks() > 0 ? definition.cooldownTicks() : 6000L)"),
                "Pixelmon drop harvesting should use the configured five minute cooldown fallback.");
        assertTrue(behaviorSource.contains("pixelmonProcessing=drop rolls disabled; processing awards XP only"),
                "Weapon processing stored Pixelmon should not roll drops; it should only award XP.");
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

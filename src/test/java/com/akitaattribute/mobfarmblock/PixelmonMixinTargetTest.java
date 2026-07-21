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
        assertTrue(mixinSource.contains("@Mixin(targets = \"" + DROP_QUERY_LIST_TARGET + "\""));
        for (String badTarget : KNOWN_BAD_DROP_QUERY_LIST_TARGETS) assertFalse(mixinSource.contains(badTarget));
    }

    @Test
    void dropItemQueryListCallbacksAreStaticBecausePixelmonRegisterIsStatic() throws IOException {
        String mixinSource = read("src/main/java/com/akitaattribute/mobfarmblock/mixin/PixelmonDropItemQueryListMixin.java");
        assertTrue(mixinSource.contains("private static void mobFarmBlock$appendCaptureToolBeforeRegister"));
        assertTrue(mixinSource.contains("private static void mobFarmBlock$updateCaptureToolAfterRegister"));
        assertFalse(mixinSource.contains("private void mobFarmBlock$"));
    }

    @Test
    void captureToolIsInjectedBeforePixelmonRegistersLootUiThenUpdatedAfterDropsExist() throws IOException {
        String mixinSource = read("src/main/java/com/akitaattribute/mobfarmblock/mixin/PixelmonDropItemQueryListMixin.java");
        assertTrue(mixinSource.contains("@Inject(method = \"register\", at = @At(\"HEAD\"), remap = false)"));
        assertTrue(mixinSource.contains("mobFarmBlock$appendCaptureToolBeforeRegister"));
        assertTrue(mixinSource.contains("@Inject(method = \"register\", at = @At(\"TAIL\"), remap = false)"));
        assertTrue(mixinSource.contains("updateCaptureToolObservedLoot"));
        assertFalse(mixinSource.contains("TAIL before append"));
    }

    @Test
    void injectedLootUsesPixelmonDroppedItemWrapperNotRawItemStack() throws IOException {
        String injectorSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonLootCaptureToolInjector.java");
        assertTrue(injectorSource.contains(DROPPED_ITEM_TARGET));
        assertTrue(injectorSource.contains("getConstructor(ItemStack.class, int.class)"));
        assertFalse(injectorSource.contains("drops.add(captureTool);"));
    }

    @Test
    void lootCaptureToolsAreConfigurableAndConsumedOnDeposit() throws IOException {
        String configSource = read("src/main/java/com/akitaattribute/mobfarmblock/config/MobFarmConfig.java");
        String injectorSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonLootCaptureToolInjector.java");
        String blockSource = read("src/main/java/com/akitaattribute/mobfarmblock/block/MobFarmBlock.java");
        assertTrue(configSource.contains("PIXELMON_CAPTURE_TOOL_DROP_CHANCE"));
        assertTrue(configSource.contains("defineInRange(\"pixelmonCaptureToolDropChance\", 0.01D, 0.0D, 1.0D)"));
        assertTrue(injectorSource.contains("MobFarmConfig.PIXELMON_CAPTURE_TOOL_DROP_CHANCE.get()"));
        assertTrue(injectorSource.contains("CaptureToolItem.setDiscardOnDeposit(captureTool, true)"));
        assertTrue(blockSource.contains("CaptureToolItem.shouldDiscardOnDeposit(stack)"));
    }

    @Test
    void pixelmonCaptureLootGuaranteePersistsMissesAndResetsOnDrop() throws IOException {
        String dataSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonCaptureToolDropData.java");
        String injectorSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonLootCaptureToolInjector.java");
        assertTrue(dataSource.contains("extends SavedData"));
        assertTrue(dataSource.contains("Map<UUID, Long> missesByPlayer"));
        assertTrue(dataSource.contains("recordMiss(ServerPlayer player)"));
        assertTrue(dataSource.contains("reset(ServerPlayer player)"));
        assertTrue(injectorSource.contains("guaranteeThreshold(chance)"));
        assertTrue(injectorSource.contains("missesBefore + 1L >= guaranteeThreshold"));
        assertTrue(injectorSource.contains("PixelmonCaptureToolDropData.get(player).recordMiss(player)"));
        assertTrue(injectorSource.contains("PixelmonCaptureToolDropData.get(player).reset(player)"));
    }

    @Test
    void pixelmonNpcTrackerUsesShortLogsAndCleanupGate() throws IOException {
        String trackerSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonEntityTracker.java");
        String modSource = read("src/main/java/com/akitaattribute/mobfarmblock/MobFarmBlockMod.java");
        String configSource = read("src/main/java/com/akitaattribute/mobfarmblock/config/MobFarmConfig.java");
        String langSource = read("src/main/resources/assets/mob_farm_block/lang/en_us.json");
        assertTrue(configSource.contains("PIXELMON_ENTITY_TRACKING_LOG"));
        assertTrue(configSource.contains("PIXELMON_NPC_REMOVAL_ENABLED"));
        assertTrue(configSource.contains("define(\"pixelmonNpcRemovalEnabled\", true)"));
        assertTrue(langSource.contains("Pixelmon Entity Tracking Log"));
        assertTrue(langSource.contains("Pixelmon NPC Removal Enabled"));
        assertTrue(modSource.contains("PixelmonEntityTracker::onEntityJoinLevel"));
        assertTrue(modSource.contains("PixelmonEntityTracker::onServerTick"));
        assertTrue(trackerSource.contains("return \"pixelmon:npc\".equals(typeText);"));
        assertTrue(trackerSource.contains("private static final long REMOVAL_AGE_TICKS = 6000L"));
        assertTrue(trackerSource.contains("!protection.protectedNpc() && observedTicks >= REMOVAL_AGE_TICKS"));
        assertTrue(trackerSource.contains("entity.discard()"));
        assertTrue(trackerSource.contains("pixelmon_entities.jsonl"));
        assertTrue(trackerSource.contains("protected_pixelmon_npcs.jsonl"));
        assertTrue(trackerSource.contains("protected_nurse"));
        assertTrue(trackerSource.contains("protected_shopkeeper"));
        assertTrue(trackerSource.contains("protected_titled_npc"));
        assertTrue(trackerSource.contains("prop(out, \"name\"") && trackerSource.contains("prop(out, \"role\"") && trackerSource.contains("prop(out, \"observedAgeSeconds\""));
        assertFalse(trackerSource.contains("entityNbtSummary"));
        assertFalse(trackerSource.contains("entityClass"));
        assertFalse(trackerSource.contains("minecraftEntityTickCount"));
        assertFalse(trackerSource.contains("typeText.startsWith(\"pixelmon:\")"));
    }

    @Test
    void pixelmonPensUseEmptyHandDropHarvestAndProcessingOnlyAwardsXp() throws IOException {
        String factorySource = read("src/main/java/com/akitaattribute/mobfarmblock/mob/MobProfileFactory.java");
        String interactionSource = read("src/main/java/com/akitaattribute/mobfarmblock/behavior/InteractionMethodRegistry.java");
        String behaviorSource = read("src/main/java/com/akitaattribute/mobfarmblock/behavior/BehaviorUtil.java");
        assertTrue(factorySource.contains("\"pixelmon:pixelmon\".equals(id)"));
        assertTrue(factorySource.contains("MobFarmBlockMod.id(\"pixelmon_drops\")"));
        assertTrue(interactionSource.contains("PIXELMON_DROPS"));
        assertTrue(interactionSource.contains("definition.methodId().equals(EGG) || definition.methodId().equals(PIXELMON_DROPS)"));
        assertTrue(interactionSource.contains("context.stored().setCooldown(action, now, definition.cooldownTicks() > 0 ? definition.cooldownTicks() : 6000L)"));
        assertTrue(behaviorSource.contains("pixelmonProcessing=drop rolls disabled; processing awards XP only"));
    }

    @Test
    void pixelmonHarvestUiShowsReadyCooldownItemNamesAndCapitalizedSpecies() throws IOException {
        String rendererSource = read("src/main/java/com/akitaattribute/mobfarmblock/client/MobFarmBlockEntityRenderer.java");
        assertTrue(rendererSource.contains("isPixelmonDropHarvest"));
        assertTrue(rendererSource.contains("pixelmonDropRows"));
        assertTrue(rendererSource.contains("readyValue(stored, MobFarmBlockMod.id(\"pixelmon_drops\"), now)"));
        assertTrue(rendererSource.contains("readyColor(stored, MobFarmBlockMod.id(\"pixelmon_drops\"), now)"));
        assertTrue(rendererSource.contains("itemLabel(stack, rule.itemId())"));
        assertTrue(rendererSource.contains("private static String mobLabel(StoredMob stored) { return prettyName"));
        assertTrue(rendererSource.contains("Character.toUpperCase(part.charAt(0))"));
    }

    @Test
    void pixelmonRenderersUseNaturalPreviewScaleAndMetrics() throws IOException {
        String snapshotSource = read("src/main/java/com/akitaattribute/mobfarmblock/mob/PixelmonRenderSnapshot.java");
        String snapshotFactorySource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonRenderSnapshotFactory.java");
        String rendererSource = read("src/main/java/com/akitaattribute/mobfarmblock/client/MobFarmBlockEntityRenderer.java");
        String captureToolRendererSource = read("src/main/java/com/akitaattribute/mobfarmblock/client/CaptureToolItemRenderer.java");
        String blockItemRendererSource = read("src/main/java/com/akitaattribute/mobfarmblock/client/MobFarmBlockItemRenderer.java");
        assertTrue(rendererSource.contains("poseStack.translate(0.5D, 0.58D, 0.5D)"));
        assertTrue(rendererSource.contains("applyPixelmonFacing(entity, yaw)"));
        assertTrue(rendererSource.contains("minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, pixelmon ? yaw : 0.0F"));
        assertTrue(snapshotSource.contains("capturedWidth") && snapshotSource.contains("capturedHeight"));
        assertTrue(snapshotFactorySource.contains("entity.getBbWidth()") && snapshotFactorySource.contains("entity.getBbHeight()"));
        assertTrue(captureToolRendererSource.contains("poseStack.scale(0.72F, 0.72F, 0.72F)"));
        assertTrue(blockItemRendererSource.contains("poseStack.scale(0.50F, 0.50F, 0.50F)"));
        assertTrue(captureToolRendererSource.contains("getYCentre") && blockItemRendererSource.contains("getYCentre"));
        assertTrue(captureToolRendererSource.contains("Mob Farm Pixelmon render metrics") && blockItemRendererSource.contains("Mob Farm Pixelmon render metrics"));
        assertFalse(captureToolRendererSource.contains("fit *= 7.25F"));
        assertFalse(blockItemRendererSource.contains("fit *= 5.50F"));
    }

    @Test
    void mixinConfigRegistersDropItemQueryListMixin() throws IOException {
        String mixinConfig = read("src/main/resources/mob_farm_block.mixins.json");
        assertTrue(mixinConfig.contains("PixelmonDropItemQueryListMixin"));
    }

    @Test
    void dropRegistryReflectionRemainsSeparateFromDropQueryListMixinTarget() throws IOException {
        String fallbackSource = read("src/main/java/com/akitaattribute/mobfarmblock/integration/PixelmonDropFallback.java");
        String mixinSource = read("src/main/java/com/akitaattribute/mobfarmblock/mixin/PixelmonDropItemQueryListMixin.java");
        assertTrue(fallbackSource.contains(DROP_REGISTRY_TARGET));
        assertFalse(mixinSource.contains("com.pixelmonmod.pixelmon.entities.npcs.registry.DropItemQueryList"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}

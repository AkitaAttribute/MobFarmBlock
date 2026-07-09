# Mob Farm Block

Mob Farm Block is a NeoForge Minecraft 1.21.1 mod by Akita Attribute.

The Capture Tool captures compact mob profiles, and the Mob Farm Block stores those profiles for farm-style interactions and processing. Stored mobs are profiles, not live entities: the mod does not store full entity NBT and does not simulate live entities inside the block.

## Feature Checklist

### Pen Block

- [x] Pen block should break quickly, roughly like a dirt block by hand, regardless of tool or empty hand.
- [x] Crouching while breaking/punching the pen block should not process/kill the contained mob.
- [x] Large/tall mobs should shrink while the player is looking at the pen so the look UI remains visible.
- [x] Stored mob rendering should be stabilized so captured mobs do not visibly spazz, jitter, or animate wildly in the pen.

### Look UI

- [x] Add a look/hover UI above the pen block when the player is directly looking at the pen within normal reach distance.
- [x] The look UI should show possible output item icons.
- [x] The look UI should show `Ready!` when an output/action is ready.
- [x] The look UI should show a remaining timer when an output/action is not ready.
- [x] The look UI timer should be calculated only when rendered, using absolute `readyAt` timestamps, not by decrementing a permanently ticking timer.
- [x] The look UI should stay visually anchored above the pen/entity, not float far away.
- [x] The look UI should be readable, with icons and text aligned like a proper overlay.
- [x] The look UI should show drop chance percentages next to item icons in green, such as `100%`, `2.5%`, or `3%`.
- [x] The look UI should show feeding/breeding state when a breeding cycle is active.

### Capture Tool

- [ ] Capture Tool item rendering should be centered, readable, and finished-quality in inventory/hotbar.
- [ ] Capture Tool in-hand rendering should clearly show the player is holding the tool, even when empty.
- [ ] Filled Capture Tool should show the captured mob/model/icon in a clearly visible way. Current status: functional, but the yellow/visibility treatment needs improvement.
- [x] Capture Tool rendering should not rely on generated PNGs or binary assets.

### Cobblemon Rendering

- [x] Cobblemon captured Pokémon should render as the actual captured Pokémon in both the Capture Tool and pen.

### Cobblemon Drops and Debugging

- [x] Cobblemon drop resolution should produce consistent, correct-looking drops from runtime Cobblemon data.
- [ ] Cobblemon debug output/logging can be reduced or removed once the Look UI is working.

### Breeding

- [x] Breeding should require two feed items per offspring.
- [x] Breeding with one stored animal should be rejected.
- [x] Breeding capacity should be based on `floor(baseCount / 2)` at cycle start.
- [x] Newly created offspring should not count toward the current breeding cycle.
- [x] The breeding cycle base count should remain locked until the current cycle completes.
- [x] If the stored mob count is depleted before the breeding timer completes, pending breeding should not later repopulate an empty pen.

### Recipes and Recipe Book

- [ ] Recipes should work for both Capture Tool and Mob Farm Block.
- [ ] Recipes should appear in the vanilla recipe book.
- [ ] Capture Tool recipe should accept any vanilla glass pane color.
- [ ] Mob Farm Block recipe should accept any vanilla fence.

### Config and Optional Integrations

- [ ] Debug/config options should be visible/editable from the NeoForge/Forge Mods config UI. Current status: visible in the config UI, but behavior is untested.
- [ ] Debug/config options should include `debugChatMessages` and `debugCobblemonJsonDump`.
- [ ] JEI should remain optional; the mod should run without JEI installed. Current status: runs without JEI installed; JEI behavior is untested.

### Repository Hygiene

- [x] No binary files should be committed: no `.png`, `.jpg`, `.jpeg`, `.webp`, `.jar`, or `.class`.

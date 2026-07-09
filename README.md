# Mob Farm Block

Mob Farm Block is a NeoForge Minecraft 1.21.1 mod by Akita Attribute.

The Capture Tool captures compact mob profiles, and the Mob Farm Block stores those profiles for farm-style interactions and processing. Stored mobs are profiles, not live entities: the mod does not store full entity NBT and does not simulate live entities inside the block.

## Feature Checklist

### Pen Block

- [ ] Pen block should break quickly, roughly like a dirt block by hand, regardless of tool or empty hand.
- [ ] Crouching while breaking/punching the pen block should not process/kill the contained mob.
- [ ] Large/tall mobs should shrink while the player is looking at the pen so the look UI remains visible.
- [ ] Spider pen rendering should be stabilized if possible.

### Look UI

- [ ] Add a look/hover UI above the pen block when the player is directly looking at the pen within normal reach distance.
- [ ] The look UI should show possible output item icons.
- [ ] The look UI should show `Ready!` when an output/action is ready.
- [ ] The look UI should show a remaining timer when an output/action is not ready.
- [ ] The look UI timer should be calculated only when rendered, using absolute `readyAt` timestamps, not by decrementing a permanently ticking timer.
- [ ] The look UI should stay visually anchored above the pen/entity, not float far away.
- [ ] The look UI should be readable, with icons and text aligned like a proper overlay.
- [ ] The look UI should show drop chance percentages next to item icons in green, such as `100%`, `2.5%`, or `3%`.
- [ ] The look UI should show feeding/breeding state when a breeding cycle is active.

### Capture Tool

- [ ] Capture Tool item rendering should be centered, readable, and finished-quality in inventory/hotbar.
- [ ] Capture Tool in-hand rendering should clearly show the player is holding the tool, even when empty.
- [ ] Filled Capture Tool should show the captured mob/model/icon in a usable way.
- [ ] Capture Tool rendering should not rely on generated PNGs or binary assets.

### Cobblemon Rendering

- [ ] Cobblemon captured species should render as the actual captured species in both the Capture Tool and pen.
- [ ] Cobblemon rendering should use stored `speciesId`, form, and aspects correctly.
- [ ] Cobblemon rendering should not silently show a generic/default/wrong Pokémon as if it were correct.

### Cobblemon Drops and Debugging

- [ ] Cobblemon drop resolution should use Cobblemon runtime drop-table data/API/reflection, not vanilla entity loot tables.
- [ ] Cobblemon drops should preserve real item IDs, chances, and quantities from runtime data.
- [ ] Cobblemon drop debug should list all resolved drop rules in the JSON debug log.
- [ ] Cobblemon processing debug should show per-rule rolls, success/failure, quantity, output target, and no-output reason.
- [ ] Cobblemon debug dumps should include enough drop-table information to compare against runtime Cobblemon data.
- [ ] Cobblemon debug dumper should avoid invoking mutating methods during reflection.
- [ ] Cobblemon quantity ranges such as `0–1` should preserve a real minimum of `0`.

### Breeding

- [ ] Breeding should require two feed items per offspring.
- [ ] Breeding with one stored animal should be rejected.
- [ ] Breeding capacity should be based on `floor(baseCount / 2)` at cycle start.
- [ ] Newly created offspring should not count toward the current breeding cycle.
- [ ] The breeding cycle base count should remain locked until the current cycle completes.
- [ ] If the stored mob count is depleted before the breeding timer completes, pending breeding should not later repopulate an empty pen.

### Recipes and Recipe Book

- [ ] Recipes should work for both Capture Tool and Mob Farm Block.
- [ ] Recipes should appear in the vanilla recipe book.
- [ ] Capture Tool recipe should accept any vanilla glass pane color.
- [ ] Mob Farm Block recipe should accept any vanilla fence.

### Config and Optional Integrations

- [ ] Debug/config options should be visible/editable from the NeoForge/Forge Mods config UI.
- [ ] Debug/config options should include `debugChatMessages` and `debugCobblemonJsonDump`.
- [ ] JEI should remain optional; the mod should run without JEI installed.

### Repository Hygiene

- [ ] No binary files should be committed: no `.png`, `.jpg`, `.jpeg`, `.webp`, `.jar`, or `.class`.

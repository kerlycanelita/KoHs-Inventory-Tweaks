# KoHs Inventory Tweaks Porting and Behavior Specification

This document is the behavior contract for future Minecraft version ports of KoHs Inventory Tweaks. A port may replace mappings, renderer APIs, events, widgets, mixins, and implementation details, but it must not silently change the behavior described here.

The current implementation is the source of truth for details not covered by this document. Never copy code from the legacy KoHs Inv Cursor project into a new port. Port the current KoHs Inventory Tweaks implementation and adapt it to the target Minecraft and Fabric APIs.

## Master porting prompt

Use the following prompt as the starting point for a future port:

> Port the current KoHs Inventory Tweaks client mod to Minecraft `<TARGET_VERSION>` without modifying or deleting any existing supported version. Create an isolated version workspace. Use the target version's official Minecraft mappings, a compatible Fabric Loader, Fabric API, Mod Menu API, Java toolchain, and Loom version. Preserve every behavior contract in `docs/PORTING_BEHAVIOR_SPEC.md`, including defaults, persistence, localization, responsive GUI behavior, vanilla input semantics, resource-pack awareness, compatibility alerts, and client-only legality. Do not reuse legacy KoHs Inv Cursor code. Replace version-specific renderer and input APIs with the target version's equivalents. Audit every mixin target using the mapped target Minecraft JAR; compilation alone is not sufficient. Produce a remapped release JAR, install it into the requested test instance without deleting unrelated mods, launch the instance, inspect the log for mixin or initialization failures, and leave Minecraft open for manual testing.

## Non-negotiable invariants

- The mod is client-side. It must not require server installation or send custom inventory packets.
- Vanilla container rules remain authoritative. No fabricated clicks, impossible swaps, inventory prediction, drag automation, duplicate actions, or bypasses are allowed.
- The mod may improve client timing, presentation, pointer placement, and local input queuing only.
- Never replace the entire vanilla screen when a targeted render or input hook can preserve compatibility.
- A missing custom setting always falls back safely to vanilla behavior.
- All user changes that are confirmed with a save action must survive restart.
- Temporary previews must use a working config copy and must not mutate the persisted config until the user saves.
- Escape or Back with unsaved changes must show a discard warning with explicit return and discard choices.
- Every custom GUI must remain usable at large Minecraft GUI scales, small logical resolutions, window resizing, and fullscreen transitions.
- Spanish is used for every Minecraft locale whose language code starts with `es_`; every other language falls back to English.
- Audio highlighting is intentionally absent. Do not restore it unless it is designed and requested as a new feature.

## Configuration schema and defaults

New installations must begin with these values:

| Setting | Default | Contract |
|---|---:|---|
| Center Mouse Fix | On | Applies only when opening the player inventory. |
| SuperFastInventory | On | Client-side opening/input coordination only. |
| Remove All Inventory Animations | Off | Requires a warning before enabling. |
| Custom inventory GUI scale | Off | Vanilla GUI scale controls the inventory while off. |
| Inventory scale value | 100% | Stored even while the feature is off. |
| GUI scale warning dismissed | No | “Do not show again” applies only to the GUI Scaler warning. |
| Affect all containers | On | Supported containers inherit the selected inventory scale on new installations. Explicitly saved choices remain authoritative. |
| Chest cursor landing | Off | Single and double chest use vanilla pointer behavior. |
| Shulker Box cursor landing | Off | Uses vanilla pointer behavior. |
| Ender Chest cursor landing | Off | Uses vanilla pointer behavior. |
| Barrel cursor landing | Off | Uses vanilla pointer behavior. |
| Inventory texture source | Applied | Uses the active resource-pack inventory texture. |
| Frame color | White | Neutral multiplication keeps the source texture unchanged. |
| Frame opacity | 255 | Fully visible. |
| Slot color | White | Neutral multiplication keeps slots unchanged. |
| Slot opacity | 255 | Fully visible and never changes item opacity. |
| Custom background opacity | 255 | Fully visible when a background exists. |
| Outside inventory darkness | 208 | Matches the intended vanilla-like backdrop. |
| Custom background | None | No external file is required. |
| Item highlights | Empty | No item is highlighted. |
| Cursor positions | Unset | Vanilla pointer behavior is preserved. |

Configuration coordinates for cursor positions are normalized from `0.0` to `1.0`, not stored as physical pixels. Non-finite or out-of-range values must be sanitized. Invalid enum values, colors, opacity values, scale values, item identifiers, paths, or lists must be repaired without crashing.

Persistence must use a complete sanitized snapshot and an atomic replace pattern for `config/kohs_inventory_tweaks.json`. Loading an older config must preserve recognized values and supply current defaults for missing fields. Saving one tab must not erase settings owned by another tab.

## Main configuration menu

The main menu is a responsive, dark-purple, semi-transparent interface over the current world. It has floating purple particles and a soft entrance animation. The vanilla player inventory is centered at the active Minecraft GUI scale and the player model follows the mouse in the vanilla manner.

Behavior options occupy scrollable left and right columns around the central inventory. Scrolling is interpolated smoothly, clamped after layout changes, and faded at clipped edges. Hovering a control shows its description. Controls must visually communicate On and Off with color, marker, and translated state text rather than an ambiguous “Active” label.

The menu contains at least:

- Cursor Landing
- Inventory Tweaks
- Issues Tracker
- Herzium
- Customization
- Item Highlighter
- GUI Scaler
- Applied/Vanilla inventory texture source
- Menu key binding, default `Y`
- Done

Clicking the key binding enters a capture state and displays a translated request to press the desired key. The binding is saved through Minecraft's own options storage. It must not open the mod menu while chat, another screen, or an inventory is consuming input.

The configuration menu requires an active world or server because its live preview depends on a player and loaded resources. When invoked from the title screen, show a translated notice, a decorative purple snake, and a Back button; do not attempt to render the inventory preview.

## Cursor Landing

Cursor Landing stores an independent destination for:

- Player inventory
- Single chest
- Double chest
- Shulker Box
- Ender Chest
- Barrel

The editor displays a vanilla/resource-pack-aware preview and lets the player select a point inside it. Chest, Shulker Box, Ender Chest, and barrel have independent On/Off controls. Turning one off immediately restores vanilla pointer behavior for that container without deleting the stored point unless Reset All is used. The player inventory has no enable switch; an unset position means vanilla behavior.

The selected point is normalized against the relevant GUI rectangle so it remains correct across GUI scale, resolution, fullscreen, and resource packs. Pointer movement occurs only after the target screen has completed its layout. Do not simulate dragging, clicking, or item movement.

Save & Exit commits every changed point and switch. Reset All clears all points and disables optional container targets. Escape with changes opens the discard warning.

## Center Mouse Fix

Center Mouse Fix corrects a Minecraft or platform recenter that can occur during extremely fast player-inventory opening. It must:

- Apply only to `InventoryScreen`, never chest, Ender Chest, barrel, furnace, crafting table, or any other container.
- Work whether the player has a custom inventory cursor point or uses vanilla placement.
- Observe a short bounded opening window and correct only an unexpected recenter event.
- Stop after the screen stabilizes or closes.
- Never continuously pin the pointer.
- Never click, drag, select a slot, or modify container state.
- Use the target Minecraft version's window-to-GUI coordinate conversion and native cursor API.

## SuperFastInventory

SuperFastInventory preserves rapid legitimate player input without inventing server actions. It coordinates the player-inventory open request and a nearly simultaneous offhand key press.

Required behavior:

- Opening remains immediate and responsive even when the player repeatedly taps the inventory key.
- Maintain a small bounded client queue/state machine, not an unbounded list of repeated actions.
- Coalesce duplicate open requests while the same screen transition is pending.
- If the offhand key was pressed during the opening race, defer at most that single vanilla swap intent until `InventoryScreen` is initialized and rendered.
- Resolve the real slot currently under the transformed GUI pointer after the screen is ready.
- Invoke Minecraft's vanilla `SWAP`/offhand container action exactly once for that real slot.
- If no valid slot is under the pointer, do nothing; never substitute the selected hotbar sword or another guessed slot.
- Cancel the pending intent on timeout, screen replacement, disconnect, invalid player/game mode, or key release conditions required by the implementation.
- Do not accelerate server acknowledgement, alter packets, predict inventory contents, or perform illegal multi-actions.

The feature must be audited against rapid inventory toggling, simultaneous inventory/offhand input, scaled inventory coordinates, recipe-book layout, and high polling-rate mice/keyboards.

## Remove All Inventory Animations and anti-ghosting

This option is Off by default. Before it is enabled, warn that animated GIF/video inventory backgrounds will be displayed as a single static frame.

When enabled, remove inventory-only visual animation sources where safely possible, including:

- Animated custom inventory background playback, frozen to a deterministic frame.
- Recipe result bounce and recipe-tab bounce.
- Cycling slot background animations.
- Enchantment-book animation.
- Animated enchantment foil while inventory slots are rendered; HUD, held-item, entity, and world foil remain Vanilla.
- Furnace/brewing/container progress sprites that are visual-only and covered by the implementation.
- Transient snapback or carried visual copies that cause a client-side ghost duplicate.

Anti-ghosting must never delete, move, merge, or rewrite an `ItemStack`. It only suppresses stale/transient rendering and promptly uses the authoritative client container state. Real clicks and keyboard swaps remain at vanilla speed. If suppressing an animation would conceal gameplay-critical state on a target version, document the exception and keep that state visible.

## Customization

Entering Customization first shows a translated warning that visual changes affect every supported container where the player inventory is shown.

Layout contract:

- The live container preview is on the far left.
- All settings are in one smoothly scrollable panel on the right.
- Left and right arrow buttons cycle the preview backward and forward.
- The preview is clipped cleanly, never covered by old panels, and uses the current working settings live.

Preview coverage must include the player inventory plus all practical vanilla inventory/container screens, including single/double chest, Ender Chest, barrel, furnace family, crafting table, crafter, brewing stand, hopper, dispenser/dropper, shulker box, beacon, enchantment, anvil, smithing, grindstone, stonecutter, loom, cartography table, merchant, horse/inventory variants, and bundles or other new vanilla container types introduced by the target version. Unsupported screens must retain vanilla rendering rather than crash.

Visual settings:

- Inventory outer/frame tint selected from a color palette.
- Frame opacity slider where `0` is transparent.
- Slot surface tint selected from a color palette.
- Slot opacity slider where `0` is transparent.
- Item renders, counts, durability, glint, and tooltips are never faded by slot opacity.
- Outside inventory darkness controls only the world overlay behind inventory screens; `0` is clear.
- Applied texture source uses the active resource pack.
- Vanilla texture source uses the bundled vanilla baseline even when a pack replaces it.
- Mod colors and opacity compose over the selected source and therefore take precedence while respecting its layout.

All texture composition caches must be invalidated on resource reload, texture-source change, relevant color/opacity change, and custom background change.

## Custom inventory background media

Supported inputs are static images, GIFs, and accepted video formats. A video is converted to a bounded looping GIF or equivalent internal animation. Reject files that cannot be decoded and show a useful translated error.

Before accepting any media, open a modal crop editor above the previous screen. The underlying screen must not remain interactive. The user moves/zooms the media inside a mandatory crop rectangle matching the selected inventory surface and current GUI scale. Done commits the crop; cancel restores the previous working value.

Limits must protect memory and frame time. Preserve the implementation's advertised resolution, codec, duration, and frame-count constraints for the target version. Long video/audio-like sources must not block the render thread. When Remove All Inventory Animations is enabled, decode or retain only the chosen static frame for rendering.

## Item Highlighter

The Item Highlighter screen contains:

- A left panel with real slot renders for every configured item.
- A central/right vanilla item selector with translated search.
- A modal per-item editor that fully owns focus and input while open.

Selecting an item adds it to the left panel with default purple background and light-purple border colors. Clicking a configured item opens its editor with a larger slot/item preview, background palette, border palette, Hotbar option, Dynamic option, Done, and Reset.

Runtime behavior:

- Static highlighting colors configured items in real container slots.
- Hotbar highlighting applies the same rule to the HUD only when enabled for that item.
- Dynamic highlighting activates matching selected items only while the pointer is over that item in the real inventory; removing the pointer restores vanilla appearance.
- Matching uses stable item registry identifiers and adapts to active resource-pack item textures/models.
- Highlight layers must not replace or recolor the item model itself.
- Changes must be persisted as a complete item-highlight list; reopening the game must restore them.

## GUI Scaler

The GUI Scaler opens as a unique full-screen adjustment screen over a visible world. It does not use the normal purple background panel because its purpose is to show the inventory at its real on-screen position.

- The player inventory appears exactly where the real vanilla inventory will appear.
- A vertical slider is on the left: upward means larger and downward means smaller.
- The On/Off control is centered above the inventory and has unmistakable visual state feedback.
- Reset and Save & Exit are along the bottom.
- There is no separate Back button.
- Reset returns to disabled and 100%.
- When disabled, Minecraft's vanilla GUI scale determines the inventory.
- When enabled, scale only the player inventory UI, including background, slots, items, labels, carried stack, recipe UI belonging to the inventory, and the player model.
- The player model remains in its vanilla-relative rectangle, follows the mouse, and grows/shrinks exactly once with the inventory.
- The world backdrop remains full-screen and is not scaled into a black rectangle.
- Pointer coordinates and hit testing must be inversely transformed so the visual and clickable slot remain identical.

An `Affect Containers` button sits in the right control rail without shifting the centered inventory. It opens a second full-screen calibration view. While the switch is disabled, the centered container preview stays at vanilla 100%; while enabled, it immediately uses the currently selected inventory scale. Left/right arrows cycle only single chest, double chest, Shulker Box, barrel, and Ender Chest. The right-side `Affect all containers` switch is on by default for new installations, while an explicitly saved user choice remains authoritative. Enabling it always shows a translated warning naming those supported containers and explaining that more screens, such as villager trading, may be added later. When enabled, the full container background, labels, slots, carried item, hover position, clicks, releases, and drags use the same centered scale transform; unsupported containers remain vanilla.

The nominal range is 65% to 175%, further clamped when required so controls and the inventory remain usable in the actual window.

## Resource packs and reloads

Every live preview and real container customization must use resources loaded by the active Minecraft resource manager. Do not read vanilla textures exclusively from hard-coded disk paths. On a resource reload:

- Release old dynamic textures.
- Clear composed-surface caches.
- Re-read the selected Applied or Vanilla source.
- Preserve configuration values.
- Rebuild lazily or on the appropriate render/resource thread.

Item models, item textures, fonts, and layout changes supplied by packs remain Minecraft's responsibility. The mod draws its highlight/frame layers around those renders.

## Herzium integration

The main menu contains a Herzium button. When Herzium is installed, the button is vivid and surrounded by animated sparkles; clicking it opens Herzium's Mod Menu configuration screen. When missing, it is dim and its hover text explains that Herzium must be installed; clicking opens a warning with a link to the Herzium releases page.

Integration is optional. Never create a hard dependency, crash when Herzium is absent, or directly copy Herzium classes into this mod. Resolve its entry point/config screen defensively.

## Issues Tracker and incompatibility alerts

Issues Tracker lists detected problematic mods with icon, name, author, severity, and technical reason. Detection must be based on confirmed mod identifiers and confirmed injection/render conflicts, not guesses.

Better Screens (`betterscreens`) is an explicit blocking conflict. Its container-scale implementation owns global screen extraction, renderer state, window GUI scale, and mouse-coordinate conversion, which cannot safely run alongside KoHs' centered container transform. When detected, KoHs must gate every gameplay mixin, skip normal runtime registration, show the localized close-only blocker, and list the confirmed overlap points.

For an adaptable conflict, show a semi-transparent animated warning with abundant purple particles before normal play. Explain that removing the other mod is recommended but allow continuation when KoHs Inventory Tweaks can safely disable only its own conflicting hooks or use a confirmed compatibility path. A foreign redirect may be suppressed by mixin priority only for an explicit, tested rule whose exact invocation collision is known; never generalize that priority override to unknown mods.

For a confirmed crash-risk conflict that cannot be safely adapted, initialization enters a blocked mode and shows a translated blocking screen. The player may close the game but may not continue into an unsafe session. A crash in a previous run is not by itself permission to delete mods or configs.

Do not mutate another mod's files or dynamically unregister its mixins at runtime. A mixin plugin may conditionally disable this mod's own optional mixins before application. The scanner must accept Fabric metadata where `mixins` is either a single entry or an array and must fail open if heuristic inspection fails, while retaining explicit confirmed rules.

## Responsive GUI and animation contract

- Use logical GUI dimensions, not framebuffer pixels, for layout.
- Recalculate layout on `init`, resize, GUI scale change, and modal close.
- Prefer a compact mode and scrolling over shrinking text below Minecraft readability.
- Clamp scroll targets and rendered scroll values whenever content height changes.
- Apply scissor rectangles to both visual content and hit testing.
- Add top/bottom fade masks to communicate overflow without abruptly hiding controls.
- Smooth scrolling must be frame-rate-independent or time-based and must settle exactly at its clamped target.
- Entrance and modal animations affect only presentation; controls become interactable at a clearly defined point and cannot be clicked through.
- Particles are decorative, bounded in count, deterministic enough to avoid allocation spikes, and disabled/frozen by Remove All Inventory Animations where applicable.

## Version-port procedure

1. Preserve every existing version directory and release artifact.
2. Copy the current implementation into a new isolated target-version workspace.
3. Set Minecraft, Loader, Fabric API, Mod Menu, Loom, Java, and metadata constraints for the target.
4. Use official target-version mappings or the mapping set selected for the project; do not mix mapping namespaces in source.
5. Compile once to identify source API differences.
6. Inspect the mapped Minecraft JAR with `javap` or generated sources for every changed method.
7. Adapt immediate-mode versus extracted/deferred rendering deliberately. Never apply pose scaling twice.
8. Audit every mixin class, target method, injection point, invocation descriptor, accessor field, and invoker signature.
9. Treat remap warnings such as “cannot remap” as failures even when Gradle reports success.
10. Build the remapped JAR and inspect its `fabric.mod.json`, mixin JSON, embedded libraries, version, environment, Java requirement, and assets.
11. Install only the new JAR into the requested test instance. Back up or move aside an older JAR of this same mod; do not delete unrelated mods.
12. Launch the instance, wait for the title screen or a stable process, and inspect `latest.log` for ERROR, mixin, injection, classloading, and resource reload failures.
13. Leave Minecraft open when manual testing was requested.

## Required verification matrix

At minimum, verify:

- Clean config defaults and restart persistence.
- English and at least two `es_*` locale variants.
- Title-screen configuration notice.
- Main menu at GUI scales 1 through 4, small window, fullscreen, and resize.
- Cursor Landing for every target and every On/Off fallback.
- Center Mouse Fix affects only the player inventory.
- SuperFastInventory with repeated inventory taps and near-simultaneous offhand input.
- GUI Scaler minimum, 100%, maximum, disabled fallback, pointer hit testing, carried item, and player model.
- Customization on all supported container previews and real screens.
- Applied and Vanilla texture sources across a resource reload.
- Static image, GIF, accepted video conversion, mandatory crop, invalid file, and animation removal.
- Item Highlighter static, dynamic real-inventory behavior, hotbar behavior, modal focus, reset, and restart persistence.
- Remove All Inventory Animations and visual anti-ghosting without changing real stack state.
- Herzium installed and absent.
- Issues Tracker with no conflict, adaptable conflict, and blocking conflict fixtures.
- No remap warnings and no mixin/application errors in `latest.log`.

## Completion criteria

A target-version port is complete only when the remapped release JAR builds without unresolved remap warnings, launches in the requested target instance, reaches a stable Minecraft screen without mod-caused errors, preserves the contracts above, and is left ready for the requested manual verification. A successful Java compilation alone is not completion.

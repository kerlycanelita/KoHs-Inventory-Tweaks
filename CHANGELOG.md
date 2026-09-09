# Changelog

## Unreleased

## 1.0.9

- Aligned the maintained five-version set: 1.21.11, 26.1, 26.1.1, 26.1.2 and
  26.2 now share the reviewed cursor, fast-input, scale-scope and persistence
  behavior while retaining their version-specific GUI APIs.
- Added the previously missing 26.1.1 artifact and corrected its recipe-book
  and input ports.
- Made disabled container scaling an identity transform and packaged the MIT
  license text in every release JAR.
- Verified each mixin target and injector signature against its exact Minecraft
  client, and kept the stress debugger out of release artifacts.

## 1.0.8

- Stopped opening the inventory early while a mouse button is still held. Vanilla reads `minecraft.screen` a second time on the way out of `onButton`, so a button pressed against the world and released after the fast open had its release delivered to a screen that did not exist when the press happened. The world action it belonged to was lost and an inventory that had just placed the cursor on a landing slot received the release instead. The check reads `activeButton`, the only field Vanilla assigns on both edges: the public pressed flags are written inside the `screen == null` branch, so a release that lands while any screen is open leaves them reporting pressed until the next full press-and-release in the world.
- Opened once instead of not at all when two inventory presses share a single GLFW batch. A batch is one rendered frame -- eight milliseconds at 120 fps -- which no hand produces and a worn switch or a key repeat produces constantly. Cancelling both, which preserved a close intent nobody expressed at that speed, answered a hardware double-fire with a key that visibly did nothing.
- Gave way completely on any confirmed compatibility overlap instead of only on the severe ones. Two mods writing the same thing stop being predictable from either one's code, and a feature that only usually wins is worse than one that steps aside. The issue is still published, so the Issues Tracker still names the mod that took over and why.
- Scaled the drag delta for scaled containers, which already received a rewritten drag position. A widget reading the delta was being handed screen pixels next to a surface point. The player inventory was already correct through the recipe-book mixin.

- Kept a press handed to Vanilla from being taken back. Frames run far faster than the 20 TPS tick that drains Vanilla's click queue, so a yielded inventory click is still queued several batches later, and any later press let the early path reconsider it. For Vanilla actions the second check contained the damage, but a keybind belonging to another mod is consumed in that mod's own tick and never appears in the queue, so its action was stranded until the screen closed and then fired late. The batch that contains the press is now the only one that decides it.
- Folded the container title key to lower case in cursor landing, the way the scale classifier already folded it. A modded container whose translation key carries upper case was classified as a barrel for the scale and as a single chest for the stored point, so the player configured one and got the other.
- Fixed a start-up crash on Minecraft 1.21.10. `AbstractContainerScreen#renderSlot` takes `(GuiGraphics, Slot)` there, and both item-highlight injectors declared `mouseX` and `mouseY` as well. Mixin refuses that descriptor when it applies, and it applies on class load, so the game died the moment anything loaded a container screen. The defect was already published; compiling never showed it, because to the compiler those are ordinary private methods.
- Moved the first inventory open off the critical path on Minecraft 1.21.10, which was the only target still missing the warm-up. Measured at 27.9 ms of class loading and surface composition displaced from the first press, against 15.8 ms on 26.1.2: more than a whole frame at 60 fps, which is what players were describing as a stutter.

## 1.0.7

- Added an optional landing item for the player inventory. A stored point is a position on the screen, but during a fight the target is the item, and it moves; with a landing item set the cursor lands on the slot holding it and falls back to the stored point when it is not there. The offhand slot is never chosen, because landing on it would aim the swap at the item already in hand. The item is taken from the player's hand rather than a catalogue, so a totem is configured by holding one.
- Added a readout under the Super Fast Inventory option naming why the last inventory press waited for the client tick. A player whose keys share a batch takes the slow path on every open with nothing on screen to say so; the conflicting mappings are now named.

- Moved the first inventory open off the critical path. Opening the inventory for the first time in a session loads the screen and recipe book classes, and composes the customized surface when one is configured; both landed on the first press. They now run once on arriving in a world, where nothing waits on them. Measured at 15.8 ms of work moved on an unmodified configuration, which is almost entirely class loading.
- Removed the Profiles and Smart Highlighter tabs from the advanced screen. Both features were already gone from the runtime and migrated to inert values on load, so their tabs were unreachable dead code: no caller of the deep-link factories remained and neither appears in the tab bar.

- Restored Super Fast Inventory for Minecraft 26.1.2 using a reduced physical-input hook: the ordinary local inventory screen can open before the next client tick, while server-controlled openings and all slot/offhand/hotbar actions remain on Vanilla handlers. The previous combo windows, deferred offhand swap, retries, and timing guards were not restored.
- Added equal support for the configured keyboard inventory key and a mouse button remapped to Inventory, consuming only Vanilla's newly queued inventory click so the next tick cannot open a duplicate screen.
- Added an input-order guard: if offhand, hotbar, attack, use, drop, or pick input is already queued during the same tick, the early path yields the entire opening to Vanilla instead of starving or delaying that action.
- Removed post-open cursor verification and initialization-time refinement. Center Mouse Fix and Cursor Landing now choose one release target per opening and never pull the cursor back after the player begins moving it.
- Redesigned Advanced Tools as a seven-entry catalogue with a dedicated explanation and animated Configure action for each tool; every editor now separates controls from its live preview and preserves the purple glass/particle theme.
- Moved Profiles to the main configuration screen and kept its Vanilla, PvP, Building, Performance, and automatic context options in a dedicated editor.
- Added hierarchical controls whose dependent options are visible only while their parent feature is enabled, with smooth scrolling, clipping, fade masks, hover descriptions, responsive compact layout, immediate saved-state feedback, and palette selectors shared with Customization.
- Added live previews driven by the same texture source, composed inventory texture, item models, explicit and automatic highlights, accessibility focus, background opacity, and scale values used by real screens.
- Added a walking 3D preview made from the current player's extracted skin/equipment render state for the visible-player tool. The real depth-tested tint and light boost cover the complete submitted model and armor, skip models projected over the inventory panel, preserve compatible tint, never use Minecraft's through-wall outline pipeline, and change no packets or live world/entity state.
- Fixed catalogue summaries, cards, sliders, and footer notices so their reserved regions cannot overlap at compact logical resolutions.
- Added sanitized, atomically persisted settings for automatic low-durability and enchanted-item highlighting, hovered-slot focus, visual particle/background limits, automatic singleplayer/multiplayer profiles, and individual container scales.
- Added bounded in-session undo/redo, throttled automatic backups, sanitized JSON import/export, backup retention, and menu-key conflict reporting.
- Audited custom background opacity end to end: zero leaves the selected Vanilla/resource-pack surface unchanged, full opacity applies the source background, cache invalidation includes opacity, and items remain a separate unaffected render layer.

## 1.0.6

- Prepared the Minecraft 26.1.2 update with a unique artifact version, synchronized English and Spanish documentation, packaged MIT license, and release-specific validation notes.
- Aligned Raw Input Buffer compatibility with the behavior visible in Issues Tracker: Cursor Landing is paused for the confirmed mouse-position overlap, unrelated features remain available, and the unreachable reflective adapter and optional foreign-target mixin were removed.
- Fixed the offhand swap being lost during a fast inventory open. Pressing the offhand key before the inventory key left the queued click for KoHs to convert when the inventory key arrived, but a client tick landing between the two presses consumed it first and sent Vanilla's world swap instead: the same pair of keys produced the intended slot swap or a world swap depending on tick phase, at odds of roughly the gap between the presses over the 50 ms tick. The conversion is gone. An offhand key pressed in the world is always Vanilla's instant world swap, and the inventory-open combo is defined as inventory key first.
- Made the offhand and hotbar keys work from the moment the inventory opens. `hoveredSlot` is only assigned while a screen extracts its render state, so between an inventory opened on the physical key press and its first painted frame Vanilla's `checkHotbarKeyPressed` had no slot and dropped the swap. The slot is now resolved on demand for key presses, the same way `mouseClicked` already resolved it for clicks.
- Removed the mod's own deferred offhand swap. Every swap now runs through Vanilla's action on the hovered slot, so nothing can act twice on one press.
- Removed the KoHs Offhand Whitelist bridge, which existed only to filter that deferred swap. The whitelist enforces its rule on the Vanilla click path itself, which is now the only path.
- Applied the player's inventory customization to the recipe book. The panel is drawn from `textures/gui/recipe_book.png`, which sits outside the container texture directory the compositor accepted, so opening the book put an untouched vanilla panel next to a customized inventory. It now takes the same frame colour, opacity and background, and leaves its recipe grid untinted because those buttons are drawn as sprites on top of it.
- Fixed the post-open cursor verification never running for the player inventory. `AbstractRecipeBookScreen` overrides `extractRenderState` without calling super, so the hook on `AbstractContainerScreen` was unreachable for every recipe book screen.
- Removed the Herzium integration: the card in the configuration screen, its warning modal, the mod detection, the translations and the `suggests` entry.
- Stood Cursor Landing down when Herzium is installed. Herzium owns the low-latency input policy - it forces Vanilla Raw Input on at start-up over the player's own setting and removes the frame limit while the window is active - and both change when cursor samples reach the client around a screen opening. The feature reports as degraded in the Issues Tracker instead of competing for the pointer.
- Made the HUD hotbar highlight follow the item in the slot rather than the item on screen. The stack handed to the slot renderer can be a render-only preview of a pending selection substituted by another mod, which made the highlight track the preview; it now reads the inventory slot being drawn.
- Fixed the Minecraft 1.21 and 1.21.1 target: `ContainerScreen` and `InventoryScreen` were injected at a `GuiGraphics#blit` overload with float texture coordinates that only exists from 1.21.2, and `ClientBundleTooltip` was redirected at a `blitSprite` call those versions make from a different method. None of the three could apply.
- Fixed the Minecraft 1.21.2 to 1.21.5 target: the dynamic item highlight was injected into `renderSlots`, but those versions resolve the hovered slot and draw its highlight in `render`.
- Staged the artifacts of the 1.21-1.21.1 and 1.21.2-1.21.5 targets into `builds/<version>/` like every other target already did.

- Fixed the Center Mouse Fix switch doing nothing. The player inventory has no enable switch, so its cursor target was hardcoded as always enabled and the switch was never read: KoHs took over the pointer whether the option was on or off. With it on the pointer is now centered on every inventory open, and with it off and no saved landing point the pointer is left entirely to Vanilla.
- Fixed the hovered slot being resolved against a stale pointer for the first frames after a fast inventory open, which read as the cursor dragging into place. Minecraft delivers cursor samples as queued tasks, and while the mouse is grabbed those samples carry GLFW's unbounded virtual coordinates rather than screen pixels. SuperFastInventory opens the screen from inside that queued burst, so samples taken before the open landed afterwards and overwrote the tracked pointer. The tracked pointer is now taken from the physical cursor for 300 ms after a screen opens.
- Stopped re-committing the centered position after it has been placed. Center Mouse Fix asks for the vanilla centered position, so placing it once and leaving it alone is what keeps a fast open from pulling the pointer back mid-motion; only a configured landing point is still defended against a later centering event.
- Fixed the cursor being left on Vanilla's centered position when a container screen opened, most visibly with SuperFastInventory and with mods that raise the frame rate or take over the mouse pipeline. The landing point was written once inside `setScreen` and never checked afterwards, so any input owner that moved the pointer in the following milliseconds won. The placement is now verified for 300 ms after the screen opens and re-committed once if the pointer ends up centered.
- Kept that verification from ever fighting the player: a pointer found anywhere other than the centered position belongs to whoever is moving the mouse and is left untouched, and the check disarms after a single correction.
- Stopped re-committing the landing point after screen initialization when the pointer had already moved. Initialization takes real time with the cursor visible, so the old unconditional re-commit dragged the pointer back from wherever the player had just moved it.
- Stopped placing the cursor on window resizes. `Screen#init` also runs when the window is resized, and the placement ran every time.
- Fixed item tooltips being anchored to the scaled inventory surface instead of the pointer while the GUI Scaler is active; the drift reached several hundred pixels at the ends of the scale range.
- Fixed cursor landing resolving the inventory and container scale from an uninitialized screen size, which pinned the placement to the minimum 65% scale whenever the GUI Scaler was enabled.
- Resolved compatibility feature gating once at initialization instead of streaming the issue list, and entering a synchronized initializer, on every query; the gate is reached from every rendered slot.
- Indexed item highlights by item so slot and HUD hotbar rendering no longer builds a registry key string and scans the highlight list twice per slot per frame.
- Precomputed the inventory and container slot masks used when composing customized surfaces, replacing up to ninety rectangle tests per pixel.
- Throttled the custom background modification probe to twice a second instead of one filesystem call per rendered frame per surface, refreshed immediately on resource reload and configuration changes.
- Resolved the container surface signature once per opened screen and rejected the per-blit customization hook on configuration state before inspecting the screen.
- Measured the animated background loop length once at load instead of on every frame lookup.
- Added `tools/verify-mixin-targets.py` and `tools/verify-all-versions.py`, which prove every mixin target class, member and `INVOKE` injection point of a source tree against the Minecraft jar it is compiled against.
- Grouped the legacy KoHs Inv Cursor projects under `versions/legacy-kohs-inv-cursor/` so `versions/` lists only maintained KoHs Inventory Tweaks targets.

## 1.0.5

- Fixed the Minecraft 26.2 startup crash caused by an obsolete `extractSnapbackItem` mixin target inherited from 26.1.2.
- Removed only the obsolete snapback-animation injection from the 26.2 port. Minecraft 26.2 no longer contains `SnapbackData` or the corresponding extraction method.
- Verified that the 26.2 client completes mixin application, initializes KoHs Inventory Tweaks, and reaches resource loading successfully.

## 1.0.4

- Added optional GUI scaling for single chests, double chests, Shulker Boxes, barrels, and Ender Chests while preserving centered rendering and vanilla input coordinates.
- Made container scaling enabled by default for new installations while preserving every explicitly saved user choice.
- Made the Affect Containers preview state-aware: disabled shows the container at vanilla 100%, enabled shows the currently selected inventory scale.
- Added Shulker Box cursor landing with its own saved normalized position and vanilla fallback switch.
- Improved Issues Tracker conflict discovery by scanning mixin metadata and reporting known inventory-scaling adaptations without claiming unconfirmed crashes.
- Added a confirmed blocking rule for Better Screens (`betterscreens`): KoHs gates its gameplay mixins and runtime services before showing the non-bypassable incompatibility screen because both mods own the container-scale render and mouse-coordinate pipeline.
- Added maintained Minecraft 26.2, 26.1.1, and 26.1 source/build targets alongside 26.1.2, 1.21.11, and 1.21.10.

## 1.0.2

- Added the maintained Minecraft 1.21.11 Fabric implementation and its source tree.
- Fixed the player model's position and dimensions in the real 1.21.11 inventory when custom inventory scaling is active; mouse-follow coordinates now use the same centered transform as the inventory surface.
- Stabilized ON/OFF controls so state changes no longer restart button scale, lift, or glow animations.
- Kept the Inventory Tweaks panel stationary while the Remove All Inventory Animations confirmation is shown or dismissed.
- Extended Remove All Inventory Animations to suppress animated enchantment foil while inventory slots are rendered. The HUD hotbar, held items, entities, and world rendering remain untouched.

## 1.0.1+mc26.1.2

- Scoped Center Mouse Fix to `InventoryScreen`/the player inventory; custom cursor landing for enabled chest, double chest, ender chest, and barrel targets remains independent.
- Added a native Minecraft key mapping, default `Y`, for opening the configuration screen in-world, including runtime rebinding and `options.txt` persistence.
- Replaced discrete menu scrolling with frame-rate-independent interpolation and exact viewport/content bounds.
- Added scissor-aware clipping and hit testing for partially visible buttons, sliders, palettes, and option cards.
- Added rapid-input transaction guards for duplicate inventory-open events, close/reopen races, server-controlled inventory requests, and queued offhand swaps.
- Kept offhand execution on Minecraft's vanilla `ContainerInput.SWAP` path; no custom inventory packets or server-side automation are used.

## 1.0.0+mc26.1.2

Initial release of KoHs Inventory Tweaks for Minecraft 26.1.2.

- Rebuilt successor to KoHs Inv Cursor.
- Responsive purple configuration interface with animated particles and entry transition.
- Per-container cursor landing positions and Vanilla fallback switches.
- Center Mouse Fix and guarded SuperFastInventory offhand handling.
- Resource-pack-aware inventory and container customization.
- Mandatory media crop, animated GIF backgrounds, and local video conversion.
- Inventory-only GUI scaler.
- Per-item static, hotbar, and dynamic highlighting.
- Sanitized atomic configuration persistence.

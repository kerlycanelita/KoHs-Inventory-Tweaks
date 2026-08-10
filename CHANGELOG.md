# Changelog

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

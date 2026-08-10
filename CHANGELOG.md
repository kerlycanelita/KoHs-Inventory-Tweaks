# Changelog

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

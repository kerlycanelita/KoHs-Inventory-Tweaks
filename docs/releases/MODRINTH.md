<p align="center">
  <a href="https://github.com/kerlycanelita/KoHs-Inventory-Tweaks"><img alt="GitHub repository" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/issues"><img alt="Report an issue" src="https://img.shields.io/badge/Issues-Report_a_bug-c93c7a?style=for-the-badge&logo=githubissues&logoColor=white"></a>
  <a href="https://discord.gg/9t2VxEF7UU"><img alt="Discord" src="https://img.shields.io/badge/Discord-Join_us-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/blob/main/docs/wiki/WIKI.md"><img alt="Wiki" src="https://img.shields.io/badge/%F0%9F%93%96-Wiki-7c3aed?style=for-the-badge"></a>
</p>

![KoHs Inventory Tweaks main configuration menu](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/main-menu.png)


> **Support update (September 2026):** KoHs Inventory Tweaks now supports **Minecraft 1.21.11 and later**. The 1.21.10 builds stay downloadable as archived versions but no longer receive fixes or new features.

## Your inventory, your rules

**KoHs Inventory Tweaks** is the client-side successor to **KoHs Inv Cursor**, maintained for Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, and 26.2. It gives you precise cursor placement, faster and more reliable inventory input, visual customization, item highlighting, and an inventory-only GUI scaler—all through a responsive purple configuration menu, with a little cat to keep you company.

The mod keeps normal Minecraft inventory rules. It does not require installation on the server and does not add automated item transfers or custom inventory packets.

## New in 1.3.0

- **Minecraft's inventory bugs, fixed** — double clicks that collect again (MC-306262, MC-301852, MC-312333, MC-311852), F and number keys that keep swapping while held, inventory and drop keys on mouse buttons (MC-577), the player in your inventory moving only 20 times a second (MC-265935), and toggles on mouse buttons after a screen (MC-308595). Always on.
- **Steady inventory** — on servers with ping, your clicks stop flashing back while the server catches up. In our ping lab the double re-totem flickered in up to 11 of 24 tries at 200 ms; with Steady inventory, in none.
- **Faster enchanted items** — enchanted gear in menus and the hotbar is redrawn 60 times a second instead of on every frame (MC-310555), so it costs far less of your frame rate.
- **Hide recipe book** — no recipe book button, and no book pushing your inventory aside mid-fight. Off by default.
- **Leave with movement keys** — a movement key closes a container and you start moving. Off by default.
- **Center Mouse Fix in every container**, not only in your inventory.

## Features

- **Cursor Landing** — choose where the cursor appears in your inventory, single or double chests, Ender Chests, and barrels. Every container can be individually disabled to keep its Vanilla behavior.
- **Center Mouse Fix** — places the cursor in a single move right as Minecraft releases the mouse, before your inventory or any container exists. A hand already moving toward a totem or any item is never pulled back.
- **Super Fast Inventory** — shows the local inventory up to one tick (50 ms) sooner, and waits only when Vanilla would still run something first, such as a pending attack or an item in use. Only the screen is early: what you do in it is handled once that tick has run, in the order a Vanilla client sends.
- **Shortcuts follow the pointer** — under Super Fast Inventory: F, 1-9, Q and Ctrl+Q act on the slot under your pointer the moment you press them, even right after a fast flick, in your inventory and every Vanilla container.
- **Steady inventory** — with ping, the slots your clicks just changed keep showing those clicks until the server answers them all; everything else shows the server's word at once. Nothing you send changes.
- **Faster enchanted items** — enchanted and animated items in menus and the hotbar are redrawn 60 times a second, which looks the same and saves frames.
- **Hide recipe book** and **Leave with movement keys** — optional: no recipe book button, and movement keys that close a container.
- **Customization** — recolor inventory and slot frames, control opacity and world backdrop darkness, or use a cropped image, GIF, or short video background.
- **Resource-pack aware** — choose the active resource-pack inventory texture or the Vanilla base, then apply KoHs customization above it.
- **Item Highlighter** — search Vanilla items and configure slot colors, borders, hotbar highlighting, and dynamic hover highlighting.
- **GUI Scaler** — resize only the player inventory, including its slots, text, items, and character model. Pixel-perfect scale keeps every slot even and sharp.
- **Player Visibility** — tint and a glowing silhouette for the players you can already see while your inventory is open. Blocks hide both: nothing shows through walls.
- **Zymekoh mascot** — it breathes, blinks, follows your pointer and reacts to your switches. It only eats Totems of Undying, pulled by magic from your inventory preview: your real inventory is never touched. Five make it round and heavy for ten minutes. Turn it off on the Inventory Tweaks page or hide it from its menu.
- **The interface** — dark violet glass, animated icons that no graphics or particle setting stills, controls that never shift under your pointer, hover explanations, and pages that fit at GUI scale 2, 3 and 4.
- **English and Spanish** — every supported Spanish locale uses the Spanish interface; other languages fall back to English.

## A closer look

### Inventory Tweaks

![Inventory Tweaks: Super Fast Inventory with Shortcuts follow the pointer, Center Mouse Fix, Reduce inventory visual motion and the Zymekoh mascot](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/inventory-tweaks.png)

### The Zymekoh mascot

![The mascot after a few pets, with its hint](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/mascot.png)

![A Totem of Undying pulled by magic from the inventory preview](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/mascot-feed.png)

![The mascot's menu: Feed, size, personality, sounds and hide](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/mascot-menu.png)

### KoHs tab

![The KoHs tab: Zymekoh, her skills and links to Discord, the KoHs Mod Suite site, Modrinth and Buy me a coffee](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/kohs-tab.png)

![Zymekoh gets cross when the cat is brought to her face](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/kohs-tab-cross.png)

### Cursor Landing

![Cursor Landing configuration](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/cursor-landing.png)

### Inventory appearance

![Inventory customization](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/customization.png)

### Item Highlighter

![Item Highlighter](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/item-highlighter.png)

### Inventory-only GUI Scaler

![GUI Scaler with Pixel-perfect scale](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/gui-scaler.png)

### Player Visibility

![Player Visibility with the silhouette glow preview](https://raw.githubusercontent.com/kerlycanelita/KoHs-Inventory-Tweaks/v1.2.0/docs/media/player-visibility.png)

## Changelog

Every change, version by version: [CHANGELOG.md on GitHub](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/blob/main/CHANGELOG.md)

For complete instructions, media limits, troubleshooting, and configuration backups, read the [KoHs Inventory Tweaks Wiki](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/blob/main/docs/wiki/WIKI.md).

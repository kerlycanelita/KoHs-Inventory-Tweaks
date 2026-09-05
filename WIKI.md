<p align="center">
  <a href="WIKI_ES.md"><img alt="Leer en español" src="https://img.shields.io/badge/Idioma-Espa%C3%B1ol-7c3aed?style=for-the-badge"></a>
</p>

# KoHs Inventory Tweaks Wiki

This guide applies to **KoHs Inventory Tweaks 1.0.x for Minecraft 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, and 1.21.10**. The mod is the successor to **KoHs Inv Cursor**.

## Contents

- [Requirements](#requirements)
- [Opening the configuration menu](#opening-the-configuration-menu)
- [Main interface](#main-interface)
- [Cursor Landing](#cursor-landing)
- [Inventory Tweaks](#inventory-tweaks)
- [Customization](#customization)
- [Custom backgrounds](#custom-backgrounds)
- [GUI Scaler](#gui-scaler)
- [Item Highlighter](#item-highlighter)
- [Resource packs](#resource-packs)
- [Saving and files](#saving-and-files)
- [Troubleshooting](#troubleshooting)

## Requirements

- Minecraft Java Edition 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, or 1.21.10.
- Fabric Loader 0.19.3 or newer.
- Fabric API matching the selected Minecraft version.
- Java 25 or newer for Minecraft 26.x; Java 21 or newer for Minecraft 1.21.11 and 1.21.10.
- Mod Menu is optional but recommended for convenient access to the configuration menu.

KoHs Inventory Tweaks is client-side only. Vanilla servers do not need to install it.

## Opening the configuration menu

1. Join a world or server.
2. Press **Y**, the default configuration-menu key.
3. You can rebind it from the mod's main screen or Minecraft's Controls menu.

If Mod Menu is installed, you can also use **Mods → KoHs Inventory Tweaks → Configure**.

The configuration screen cannot be opened from Minecraft's title screen. A notice is shown instead because the previews require an active player, item models, and loaded resource packs.

## Main interface

The player's real inventory is displayed in the center. Features are divided between two scrollable panels:

- **Behavior:** Cursor Landing and Inventory Tweaks.
- **Appearance:** Customization, Item Highlighter, and GUI Scaler.

The selector at the bottom chooses the base inventory texture:

- **Applied:** uses the texture supplied by the active resource packs.
- **Vanilla:** uses Minecraft's original bundled inventory texture.

The mod's colors and backgrounds are composed over the selected source. Changing this option also refreshes every preview.

## Cursor Landing

Cursor Landing lets you select the exact point where the pointer appears when a supported screen opens.

### Setting a position

1. Open **Cursor Landing**.
2. Select Inventory, Chest, Shulker Box, Ender Chest, or Barrel.
3. For Chest, choose **×1** or **×2** to distinguish single and double chests.
4. Click the desired position inside the preview.
5. Select **Save & Exit**.

Coordinates are stored in normalized form, allowing them to adapt to the window size and GUI scale.

### Enabling or disabling containers

Chest, Shulker Box, Ender Chest, and Barrel have independent switches. When one is disabled, that container keeps vanilla cursor behavior even if a saved position exists.

**Reset All** removes every saved position and re-enables the containers. If you press **Esc** while unsaved changes exist, the mod warns you before discarding them.

## Inventory Tweaks

### Center Mouse Fix

Center Mouse Fix supplies the custom or vanilla centered coordinates to `MouseHandler#releaseMouse` exactly once for each player-inventory opening. It performs no initialization-time refinement, rendered-frame verification, interpolation, or later corrective movement.

It does not create a dragging effect, block later mouse movement, or affect chests, Ender Chests, barrels, or other containers. A separately configured Cursor Landing position remains a local visual placement and never changes an inventory action.

### Super Fast Inventory

When enabled, a physical inventory-key press—or a mouse button remapped to Inventory—constructs the ordinary local `InventoryScreen` immediately instead of waiting for the next client tick. It consumes only the logical inventory click Vanilla just queued, preventing the next tick from opening a duplicate screen. If offhand, hotbar, attack, use, drop, or pick input is already queued in that tick, KoHs does not open early and lets Vanilla process the complete input batch. It does not queue offhand input, synthesize clicks, retry actions, reset cooldowns, or send packets directly. Server-controlled inventory openings always stay on Vanilla's tick and packet path.

This changes local opening and possible follow-up input timing by up to one client tick. Packet types, payloads, ordering, slot validation, and action handlers remain Vanilla, but a server can observe the naturally earlier timing of a click the player performs after the early screen appears.

### Remove absolutely all inventory animations

This optional setting disables inventory-side animation work for players who prefer the most static possible interface. When enabled, animated custom backgrounds use a single static frame and enchanted item foil is suppressed while inventory slots are drawn.

The option does not alter server logic, item ownership, or container transactions. Its anti-ghosting safeguards only reconcile client-side visual state with the latest vanilla slot state. Enchantment rendering outside inventory slots, including the HUD hotbar, held items, entities, and the world, remains vanilla.

## Customization

Before entering Customization, the mod warns that these settings affect every supported inventory and container screen.

The preview appears on the left. Use its left and right arrows to cycle through:

- Inventory.
- Single Chest.
- Double Chest.
- Barrel.
- Ender Chest.
- Other supported inventory-based containers.

All settings are located in the scrollable right column and apply to the player inventory and compatible generic containers.

### Changing the inventory frame

1. Find **Inventory frame**.
2. Choose a color from the palette.
3. Set **Opacity** from 0 to 255.

An opacity of **0** makes the frame layer transparent; **255** makes it fully visible.

### Changing slot frames

1. Find **Slot frames**.
2. Choose a color.
3. Adjust its opacity.

This layer changes the slot's appearance without recoloring the item model inside it.

### Inventory backdrop

The **Outside darkness** slider controls the dark overlay rendered behind inventory screens:

- **0** keeps the world behind the inventory clear.
- Higher values progressively darken the area outside the inventory.

### Resetting customization

**Reset** restores colors, opacity values, the texture source, and the custom background to their defaults.

## Custom backgrounds

Select **Choose file** under **Custom inventory background**. Before any file is imported, a mandatory crop screen opens with a **176:166** aspect ratio.

- Drag the image to position it.
- Use the zoom control to resize it.
- Select **Apply Crop** to process it.

### Formats and limits

| Type | Formats | Limits |
|---|---|---|
| Image | PNG, JPG, JPEG, BMP | Maximum 32 MB and 2048 px per dimension |
| Animation | GIF | Maximum 32 MB, 2048 px per dimension, and 100 frames |
| Video | MP4 or MOV with JCodec-compatible content | 1–10 seconds, minimum 176×166, maximum 64 MB and 2048 px |

Videos are decoded locally at 10 FPS and saved as a looping GIF. Files are not uploaded to an external service.

Imported backgrounds are stored in:

    config/kohs_inventory_tweaks/backgrounds/

The **Background opacity** slider affects only the custom background.

## GUI Scaler

GUI Scaler changes the player's inventory without changing Minecraft's global GUI scale or the HUD hotbar. Its **Affect Containers** screen can apply the same selected scale to single chests, double chests, Shulker Boxes, barrels, and Ender Chests.

1. Open **GUI Scaler**.
2. Read and accept the warning. You may select **Do Not Show Again**.
3. Enable the switch at the top.
4. Adjust the vertical slider between **65% and 315%**. A fresh installation and Reset start at **200%**.

Use **Affect Containers** to preview each supported container. When its switch is disabled, the preview remains at vanilla 100%. When enabled, the preview and real supported containers use the selected inventory scale. This switch is enabled by default on a new installation and can be disabled at any time.

The inventory, slots, items, text, and player model scale as one unit while remaining in their vanilla-relative positions. The effective maximum is automatically reduced when the game window or recipe book does not leave enough space.

When the main scaler is disabled, the inventory and supported containers follow Minecraft's vanilla GUI scale again.

## Item Highlighter

### Adding an item

1. Open **Item Highlighter**.
2. Use the vanilla item catalog search field.
3. Click an item. It moves to the left panel using its currently active render.
4. Click the item in the left panel to open its editor.

Up to 256 different items can be stored.

### Colors

- **Slot background:** the color rendered behind the item.
- **Item border:** the outside border of the highlight.

The item's texture and model are not recolored.

### Hotbar

Enable **Hotbar** to display the configured highlight when the item appears in the HUD hotbar.

### Dynamic

With **Dynamic** enabled, matching items remain vanilla until the pointer is over one of them. Hovering one instance highlights every configured item with the same identifier; moving the pointer away immediately restores their vanilla appearance.

Detection uses the hovered slot calculated by Minecraft before slots are rendered, so it works in both previews and real inventories.

### Done, Reset, and Remove item

- **Done:** saves the item settings and returns to the selector.
- **Reset:** keeps the item but restores its colors and options.
- **Remove item:** removes that item from the highlighter.
- **Reset All:** removes the entire configured item list.

## Resource packs

Previews and items use the currently active resources:

- **Applied** loads the GUI supplied by the highest-priority active resource pack.
- **Vanilla** ignores pack replacements for the base inventory texture.
- KoHs Inventory Tweaks color layers and backgrounds are always applied over the selected base.
- Item Highlighter uses each item's active model and texture and modifies only the slot and highlight border.

After resource packs are reloaded, composed textures are invalidated and generated again.

## Player Visibility

Open **Customization**, scroll to **Player Visibility**, and select **Configure**. It provides palette-based model highlighting, local light intensity, distance, and an optional pulse. Its preview extracts the current player's active skin and equipment into an animated walking render without modifying the live entity.

The effect runs only while the player inventory is open and only outside its panel. Blocks still occlude players and it never uses a through-wall outline. Dependent settings remain hidden while Player Visibility is disabled; **Visible-player depth intensity** appears only after Player Visibility is enabled.

Profiles, Smart Highlighter, and the general Advanced Tools catalogue are not part of the current interface. Item Highlighter applies only explicit per-item rules selected by the user.

## Saving and files

Changes are saved when controls are used and when confirmation buttons close their screens. The main configuration file is:

    config/kohs_inventory_tweaks.json

Before writing, the mod validates value ranges, item identifiers, file names, and coordinates. It writes a temporary JSON file first and then replaces the previous file atomically to reduce corruption risk.

To create a backup, copy the JSON file and this directory:

    config/kohs_inventory_tweaks/backgrounds/

## Troubleshooting

### The configuration menu does not open from the title screen

This is intentional. Join a world or server so that a player and loaded resources are available.

### The cursor stays vanilla in a chest

Make sure the Chest switch is enabled and that you saved a position for the correct type: **×1** or **×2**.

### A custom background cannot be imported

Check its format, file size, resolution, duration, and frame count against the limits above. Some MOV or MP4 files use codecs that JCodec cannot decode.

### The inventory cannot reach 315%

The upper limit adapts to the window to prevent slots or buttons from leaving the screen. A visible recipe book reduces the safe maximum further.

### Dynamic highlighting is not visible

Confirm that the item was added, **Dynamic** is enabled in its editor, and the pointer is over a slot containing that exact item.

### Reporting a bug

Include the Minecraft version, Fabric Loader version, Fabric API version, mod list, active resource packs, and exact reproduction steps in [GitHub Issues](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/issues).

You can also ask for help in [Discord](https://discord.gg/9t2VxEF7UU).

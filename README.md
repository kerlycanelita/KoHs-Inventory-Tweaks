# KoHs Inventory Tweaks

[![GitHub](https://img.shields.io/badge/GitHub-KoHs--Inventory--Tweaks-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/kohs-inv-cursor)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/kohs_inventory_tweaks/icon.png" alt="KoHs Inventory Tweaks icon" width="220">
</p>

**Deterministic cursor placement, inventory customization, and guarded rapid input.**

KoHs Inventory Tweaks is a Fabric client-side mod that hooks into inventory-screen preparation and rendering without replacing server inventory logic. It provides deterministic cursor placement, guarded rapid input, configurable visuals, and a responsive interface while preserving vanilla interactions.

This repository contains released implementations for **Minecraft 26.2, 26.1.2, 26.1.1, 26.1, and 1.21.11**. The Minecraft 26.1.2 implementation is the repository root; every additional target lives in its own directory under `versions/`.

## Screenshots

<p align="center">
  <img src="docs/media/main-menu.png" alt="KoHs Inventory Tweaks main menu" width="720">
</p>

<p align="center"><a href="https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/releases/download/v1.2.0/KoHs-Inventory-Tweaks-1.2.0-trailer.mp4">▶ Watch the 1.2.0 trailer</a></p>

| Zymekoh mascot | KoHs tab |
| --- | --- |
| <img src="docs/media/mascot-feed.png" alt="The mascot pulling a Totem of Undying from the inventory preview" width="360"> | <img src="docs/media/kohs-tab-cross.png" alt="Zymekoh cross with the cat at her face on the KoHs tab" width="360"> |
| **Inventory Tweaks** | **Player Visibility** |
| --- | --- |
| <img src="docs/media/inventory-tweaks.png" alt="Inventory Tweaks page with its options" width="360"> | <img src="docs/media/player-visibility.png" alt="Player Visibility page with the live world view and silhouette glow" width="360"> |
| **Cursor Landing** | **Customization** |
| <img src="docs/media/cursor-landing.png" alt="Cursor Landing page" width="360"> | <img src="docs/media/customization.png" alt="Customization page" width="360"> |
| **Item Highlighter** | **GUI Scaler** |
| <img src="docs/media/item-highlighter.png" alt="Item Highlighter page" width="360"> | <img src="docs/media/gui-scaler.png" alt="GUI Scaler page" width="360"> |

## Supported versions

Since 26 September 2026, KoHs Inventory Tweaks supports **Minecraft 1.21.11 and later**. Minecraft 1.21.10 is no longer maintained and will not receive fixes or new features. The 1.21.10 source tree and the unreleased 1.21–1.21.9 work moved to [`archive/`](archive/README.md).

### Older versions

Modrinth only offers the latest version. Earlier releases stay on the [GitHub Releases](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/releases) page as archived references: they get no fixes, and each one lists the known problems that 1.1.0 fixes. The 1.0.3 and 1.0.4 releases include Minecraft 1.21.10 files.

## Compatibility

| Minecraft | Fabric API | Java | Source |
|---|---|---:|---|
| **26.2** | **0.157.0+26.2** | **25+** | `versions/kohs-inventory-tweaks-26.2` |
| **26.1.2** | **0.155.2+26.1.2** | **25+** | Repository root |
| **26.1.1** | **0.145.4+26.1.1** | **25+** | `versions/kohs-inventory-tweaks-26.1.1` |
| **26.1** | **0.145.1+26.1** | **25+** | `versions/kohs-inventory-tweaks-26.1` |
| **1.21.11** | **0.141.6+1.21.11** | **21+** | `versions/kohs-inventory-tweaks-1.21.11` |

All builds require Fabric Loader 0.19.3 or newer. Mod Menu is optional and recommended.

The mod does not need to be installed on the server.

## What it changes

- **Cursor Landing:** stores independent normalized coordinates for the player inventory, single chest, double chest, Shulker Box, Ender Chest, and barrel. Individual container types can fall back to vanilla cursor behavior.
- **Center Mouse Fix:** places the pointer at the real centre in the same move that releases it, even after the window changed size while the mouse was captured. Nothing rewrites the pointer once the inventory is open, so reaching for a totem is never pulled back.
- **Super Fast Inventory:** optionally constructs the ordinary local inventory screen directly from the physical keyboard or remapped mouse press, up to one client tick (50 ms) sooner. It waits for the tick only when Vanilla would still run something first: a hotbar key, a pending attack or use, an item in use, or a block being broken. A held mouse button never holds it back, and holding the inventory key toggles it only once. Only the screen is early: keys, clicks and the wheel made in it before its tick has run are kept until that tick has run and then handled in the same order and rhythm, so a server receives the packet order a Vanilla client sends and never a container click from a player it still sees sprinting. Server-controlled openings, slot actions, packet types, cooldowns, and validation stay on Vanilla paths.
- **Slot shortcuts:** with Super Fast Inventory, F, 1-9, Q, Ctrl+Q, clicks and the wheel act on the slot that was under the pointer when they were pressed, even right after a fast flick, in the player inventory and in every Vanilla container. Its sub-option returns them to the last-frame target Vanilla uses.
- **Reduce inventory visual motion:** stills recipe-button bounce, item return motion, the enchanting book and animated backgrounds, while progress indicators and enchanted-item glint stay Vanilla's.
- **Customization:** composes player-inventory and compatible-container textures at runtime using RGB palettes, opacity controls, static or animated backgrounds, and resource-pack-aware sources.
- **GUI Scaler:** scales the player inventory—including slots, items, text, and the player model—from 65% to 315%, starting at 200% on a fresh installation and using adaptive limits based on available space. The default-enabled Affect Containers switch applies that scale to supported chest, Shulker Box, barrel, and Ender Chest screens. Pixel-perfect scale, on by default, keeps every size on whole screen pixels so slots stay even and the highlighted slot is always the one a click takes.
- **Item Highlighter:** stores per-item colors, optional HUD hotbar highlighting, and dynamic activation based on Minecraft's calculated hovered slot.
- **Player Visibility:** can tint normally visible players and draw a glowing silhouette around them while your inventory is open, outside its panel. Both are depth-tested like the body, so blocks hide them and nothing shows through walls; Minecraft's glowing outline is never used. Its page shows a live world view beside a walking preview of your own skin and equipment.
- **Interface:** dark violet glass, a slow animated background that holds still with Reduce inventory visual motion, and controls that never shift under the pointer. Tab and option icons are animated scenes of Minecraft items and particles, and a little Zymekoh cat lives on the free floor of each window: pet it, carry it, throw it against the edges of the window, or feed it Totems of Undying from the inventory preview with its right-click menu. A KoHs tab introduces Zymekoh, who makes the KoHs mods. Every page fits at GUI scale 2, 3 and 4; each option shows only its name, and hovering it explains the option.
- **Safe persistence:** sanitizes every setting and writes it through atomic replacement to config/kohs_inventory_tweaks.json.

## Install

1. Install Fabric Loader for your supported Minecraft version.
2. Install the matching Fabric API release.
3. Place the matching KoHs Inventory Tweaks JAR in the instance's mods directory.
4. Optionally install Mod Menu to access configuration through the mod list.
5. Join a world or server before opening the configuration menu; previews require an active player and loaded resources.

Read the [English wiki](docs/wiki/WIKI.md) for complete instructions covering colors, backgrounds, cursor placement, GUI scaling, and Item Highlighter. A Spanish edition is available through the language button at the top of the wiki.

## Building

With JDK 25 available through `JAVA_HOME`:

```powershell
.\gradlew.bat build --no-daemon
```

The 26.1.2 JAR is written to `build/libs/`. To build another supported target, use the JDK declared by that target and run the same command from its `versions/kohs-inventory-tweaks-<minecraft-version>` directory; each JAR is written to that directory's `build/libs/`.

Changes are made in the repository root (Minecraft 26.1.2). The other targets are rebuilt from it, with their own API differences applied:

    python tools/port/port_version.py all

## Verification

    python tools/verify-all-versions.py

Add `--minecraft-version <version>` to check a range target against any version of its range rather than its default; pointing `--source-root` at an overlay target follows it to the sources it compiles.

This checks every source tree against the exact Minecraft jar it compiles against and reports any `@Mixin` target class, injected method, accessor, invoker, or `INVOKE` injection point that no longer exists there. It is the pre-launch check for the failure mode that produces a startup crash after a Minecraft update. Pass `--source-root <directory>` to `tools/verify-mixin-targets.py` to check a single tree.

    python tools/verify-translations.py

This reports any translation key the client asks for that is missing from a language file, and any key present in English but absent from a translation.

    python tools/verify-release-artifacts.py --release 1.2.1

After building all five targets, this checks each release JAR's metadata, mixin classes, Java target and bundled translations, and prints its size and hashes.

[`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) documents which hooks run in the frame loop and what they are allowed to do.

## Support

- Reproducible bugs: [GitHub Issues](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/issues)
- Community and help: [Discord](https://discord.gg/9t2VxEF7UU)

## Repository layout

| Location | Contents |
| --- | --- |
| `src/` | The main Minecraft 26.1.2 implementation. |
| `versions/` | Separate source targets; legacy local artifacts stay excluded. |
| `archive/` | Retired targets (Minecraft 1.21.10 and earlier), kept unchanged. |
| `debug/` | Optional diagnostic companions, separate from the playable mod. |
| `docs/` | English/Spanish wiki, player reports, audits and release notes. |
| `tools/` | Mixin, translation, artifact and cursor-contract checks. |

Start with the [documentation index](docs/README.md). Build output and local
Minecraft instances remain outside Git; keep installable JARs out of the source tree.

## License

Distributed under the [MIT License](LICENSE). Copyright © 2026 zymekoh.

## Credits

Made by **zymekoh**.

<p align="center">
  <img src="src/main/resources/assets/kohs_inventory_tweaks/icon.png" width="360" alt="KoHs Inventory Tweaks logo">
</p>

<h1 align="center">KoHs Inventory Tweaks</h1>

<p align="center">
  <strong>The successor to KoHs Inv Cursor, rebuilt for modern Minecraft inventory rendering.</strong>
</p>

<p align="center">
  <a href="https://modrinth.com/mod/kohs-inv-cursor"><img alt="Modrinth" src="https://img.shields.io/badge/Modrinth-Download-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white"></a>
  <a href="https://github.com/kerlycanelita/KoHs-Inventory-Tweaks"><img alt="GitHub repository" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/issues"><img alt="Report an issue" src="https://img.shields.io/badge/Issues-Report_an_issue-c93c7a?style=for-the-badge&logo=githubissues&logoColor=white"></a>
  <a href="https://discord.gg/9t2VxEF7UU"><img alt="Discord" src="https://img.shields.io/badge/Discord-9t2VxEF7UU-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="WIKI.md"><img alt="English wiki" src="https://img.shields.io/badge/%F0%9F%93%96-Wiki-7c3aed?style=for-the-badge"></a>
</p>

KoHs Inventory Tweaks is a Fabric client-side mod that hooks into inventory-screen preparation and rendering without replacing server inventory logic. It provides deterministic cursor placement, guarded rapid input, configurable visuals, and a responsive interface while preserving vanilla interactions.

This repository contains maintained implementations for **Minecraft 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, and 1.21.10**. Previous KoHs Inv Cursor versions are not part of this project.

## Compatibility

| Minecraft | Fabric API | Java | Source |
|---|---|---:|---|
| **26.2** | **0.157.0+26.2** | **25+** | `versions/kohs-inventory-tweaks-26.2` |
| **26.1.2** | **0.155.2+26.1.2** | **25+** | Repository root |
| **26.1.1** | **0.145.4+26.1.1** | **25+** | `versions/kohs-inventory-tweaks-26.1.1` |
| **26.1** | **0.145.1+26.1** | **25+** | `versions/kohs-inventory-tweaks-26.1` |
| **1.21.11** | **0.141.6+1.21.11** | **21+** | `versions/kohs-inventory-tweaks-1.21.11` |
| **1.21.10** | **0.138.4+1.21.10** | **21+** | `versions/kohs-inventory-tweaks-1.21.10` |

Both builds require Fabric Loader 0.19.3 or newer. Mod Menu is optional and recommended.

The mod does not need to be installed on the server.

## Technical features

- **Cursor Landing:** stores independent normalized coordinates for the player inventory, single chest, double chest, Shulker Box, Ender Chest, and barrel. Individual container types can fall back to vanilla cursor behavior.
- **Center Mouse Fix:** monitors only the player inventory for a short period after opening and corrects unexpected centering events without dragging, affecting other containers, or continuously forcing the pointer.
- **SuperFastInventory:** opens the inventory immediately and preserves an almost simultaneous offhand key press. The swap runs through Minecraft's vanilla action on the real slot under the pointer after the screen is ready.
- **Customization:** composes player-inventory and compatible-container textures at runtime using RGB palettes, opacity controls, static or animated backgrounds, and resource-pack-aware sources.
- **GUI Scaler:** scales the player inventory—including slots, items, text, and the player model—from 65% to 175%, with adaptive limits based on available space. An optional, default-enabled switch applies the selected scale to supported chest, Shulker Box, barrel, and Ender Chest screens.
- **Item Highlighter:** stores per-item colors, optional HUD hotbar highlighting, and dynamic activation based on Minecraft's calculated hovered slot.
- **Safe persistence:** sanitizes every setting and writes it through atomic replacement to config/kohs_inventory_tweaks.json.

## Installation

1. Install Fabric Loader for your supported Minecraft version.
2. Install the matching Fabric API release.
3. Place the matching KoHs Inventory Tweaks JAR in the instance's mods directory.
4. Optionally install Mod Menu to access configuration through the mod list.
5. Join a world or server before opening the configuration menu; previews require an active player and loaded resources.

Read the [English wiki](WIKI.md) for complete instructions covering colors, backgrounds, cursor placement, GUI scaling, and Item Highlighter. A Spanish edition is available through the language button at the top of the wiki.

## Building

    $env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
    .\gradlew.bat clean build

The 26.1.2 JAR is written to `build/libs/`. To build another supported target, run the same command from its `versions/kohs-inventory-tweaks-<minecraft-version>` directory; each JAR is written to that directory's `build/libs/`.

## Support

- Reproducible bugs: [GitHub Issues](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/issues)
- Community and help: [Discord](https://discord.gg/9t2VxEF7UU)

## License

Distributed under the [MIT License](LICENSE). Copyright © 2026 zymekoh.

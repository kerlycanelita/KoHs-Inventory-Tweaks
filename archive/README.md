# Archived targets

[Back to overview](../README.md)

Since 26 September 2026 KoHs Inventory Tweaks supports **Minecraft 1.21.11 and
later**. The targets below are no longer built, tested or fixed. They were moved
here unchanged, so nothing was deleted and their history is intact.

| Directory | Minecraft | State when archived |
| --- | --- | --- |
| `versions/kohs-inventory-tweaks-1.21.10` | 1.21.10 | Last release `1.0.8+mc1.21.10`. The tree also carries the unreleased 1.0.10 fast-open fix. |
| `versions/kohs-inventory-tweaks-1.21.9` | 1.21.9 | Build scaffolding only, no sources. Never released. |
| `versions/kohs-inventory-tweaks-1.21.6-1.21.8` | 1.21.6–1.21.8 | 1.0.4 source work. Never released. |
| `versions/kohs-inventory-tweaks-1.21.2-1.21.5` | 1.21.2–1.21.5 | 1.0.4 range build. Never released. |
| `versions/kohs-inventory-tweaks-1.21-1.21.1` | 1.21–1.21.1 | 1.0.4 range build. Never released. |
| `debug/kohs-inventory-debug-1.21.10` | 1.21.10 | Diagnostic companion of the 1.21.10 target. |
| `gradle/minecraft-versions.properties` | 1.21–1.21.11 | Yarn and Fabric matrix read only by the old range builds and local scaffolding. |

The 1.21.10 releases stay downloadable from Modrinth as archived versions.

`tools/verify-all-versions.py` only walks `versions/`, so these trees are no
longer part of the mixin sweep. To check one anyway:

    python tools/verify-mixin-targets.py --source-root archive/versions/kohs-inventory-tweaks-1.21.10

To bring a target back, move its directory back with `git mv` and restore its
whitelist line in `.gitignore`.

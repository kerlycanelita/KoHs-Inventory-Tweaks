# kohs-inv-cursor documentation

[Back to overview](../README.md)

## Guides

- [English wiki](wiki/WIKI.md)
- [Wiki en español](wiki/WIKI_ES.md)
- [Player reports and verified engine behavior](reports/README.md)
- [Performance constraints](PERFORMANCE.md)
- [Porting behavior specification](PORTING_BEHAVIOR_SPEC.md)
- [Audits](audits/)
- [Release records](releases/)
- [Diagnostic companions](../debug/README.md)

## Root build

| Setting | Value |
| --- | --- |
| Minecraft | `26.1.2` |
| Mod version | `1.0.9+mc26.1.2` |
| Loader | Fabric `0.19.3` |
| Loom | `1.17-SNAPSHOT` |
| JDK | 25 |

Official Minecraft names for the 26.x root target.
The source of truth is [gradle.properties](../gradle.properties) and
[build.gradle](../build.gradle); each additional target declares its own dependencies.

## Working folders

Run build commands from the repository root unless a target's instructions say
otherwise. `build/`, `.gradle/` and `run/` hold local build or game state and are
excluded from Git. Preserve saves, configuration and logs when organizing files.
Audits describe the version and checks recorded at the time; they do not imply
that every later change has been tested in a running game.

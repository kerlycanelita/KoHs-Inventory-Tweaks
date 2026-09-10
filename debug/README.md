# KoHs Inventory Debug companions

[Back to overview](../README.md)

These optional client-only projects record diagnostic traces for the matching
KoHs Inventory Tweaks target. They are built separately and are not included
in the main mod JAR.

| Minecraft | Project | JDK |
| --- | --- | --- |
| 1.21.10 | [1.21.10 companion](kohs-inventory-debug-1.21.10/README.md) | 21 |
| 1.21.11 | [1.21.11 companion](kohs-inventory-debug-1.21.11/README.md) | 21 |
| 26.1 | [26.1 companion](kohs-inventory-debug-26.1/README.md) | 25 |
| 26.1.1 | [26.1.1 companion](kohs-inventory-debug-26.1.1/README.md) | 25 |
| 26.1.2 | [26.1.2 companion](kohs-inventory-debug-26.1.2/README.md) | 25 |
| 26.2 | [26.2 companion](kohs-inventory-debug-26.2/README.md) | 25 |

Build the matching main mod first, then run from the repository root:

```powershell
.\gradlew.bat -p debug/kohs-inventory-debug-26.1.2 build --no-daemon
```

Use the equivalent directory and JDK for other targets. Read each companion's
README before running it: recording and the optional singleplayer QA macros
have different behavior. Traces and development worlds stay local under the
companion's ignored `run/` and `build/` directories.

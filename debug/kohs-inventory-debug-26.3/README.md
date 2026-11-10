# KoHs Inventory Debug

Temporary, client-only diagnostic companion for KoHs Inventory Tweaks on Minecraft 26.1.2.

The recorder records a bounded real-time trace of relevant physical input, Vanilla key queues,
inventory screen timing, offhand/container actions, cursor placement, configuration changes,
and selected packet metadata. Normal recording is observer-only and does not change input,
cursor coordinates, screens, inventory actions, cooldowns, packets, or server state.

The optional **QA macros** are a separate L2 test laboratory. They generate controlled input
through Minecraft's regular keyboard/mouse handlers and move the Windows cursor to reproduce
fast races. They are hard-blocked outside an integrated singleplayer world and must never be
used on multiplayer. The macros do not directly send packets or call inventory/server actions.

Open Mod Menu, select **KoHs Inventory Debug**, reproduce the problem, press **MARK**, and use
**COPY ALL** to copy the complete report. A persistent session log is also written under
`logs/kohs-inventory-debug/`.

## Aggressive stress lab

The Mod Menu screen includes a separate responsive stress lab. Its deterministic macros cover
latency percentiles, frame-phase jitter, 0–32 ms open/close races, multi-press queue parity,
extreme pointer motion, opening while the pointer moves, both inventory/offhand input orders,
and a longer mixed soak. `aggressive-suite` runs shortened versions of every aggressive test
and emits one `MACRO_SUMMARY` line.

Development autorun example:

```powershell
..\..\gradlew.bat -p . runClient -PdebugMacro=aggressive-suite "-PdebugWorld=KoHs Debug QA"
```

Autorun uses the same integrated-singleplayer and focused-window guards as the UI.

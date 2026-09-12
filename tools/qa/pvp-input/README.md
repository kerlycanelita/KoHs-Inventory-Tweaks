# Inventory response regression lab — Minecraft 26.1.2

This fixture belongs to the local debug companion. It is excluded from the
shipping Inventory Tweaks source sets and JAR. The companion resolves its compile
and runtime dependency from the root `mod_version`, preventing tests from silently
running an older built JAR.

```powershell
.\gradlew.bat build --offline --no-daemon
.\gradlew.bat -p debug/kohs-inventory-debug-26.1.2 runClient --offline --no-daemon -PdebugMacro=pvp-input '-PdebugWorld=KoHs Debug QA' -PdebugExit=true
```

Requires an existing disposable integrated-server world. Download missing assets
with the companion's `downloadAssets` task before using `--offline`. Never run
the lab on a remote server. Input callbacks execute on the client thread, via the
existing poll-job bridge. Worker waits have deadlines; the render thread never
waits for the worker. The client exits when the macro completes or fails.

Coverage:

- One through six fresh inventory presses in a single batch, with exact parity
  and queue consumption; no frame-duration guess about human intent.
- Repeat suppression independently of fast opening; fresh close and opt-out.
- Held-button fast opening on/off; inherited release; pending attack and inventory
  clicks remain queued on conflict. The held-only case models a press already
  consumed by a previous tick; it is not a hardware timing measurement.
- Render-cache control versus current-pointer targeting without any intervening
  render, at GUI 2/3/4, three inventory scales, book open/closed and three slots.
- Mouse side-button and scroll targeting with exactly one coordinate transform.
- A real offhand transaction at the new target followed by close in the same
  batch, confirmed by the integrated server. Fixture slots 9, 10 and 40 are
  restored in `finally`. No production automation or packet injection is added.
- Configuration copy/equality, presets and menu navigation at three GUI scales.
- PNG captures of the three option categories in the companion's `run/screenshots`.

Success requires `PVP_INPUT_SUMMARY ... failures=0`, `MACRO_COMPLETE macro=pvp-input`
and no `PVP_INPUT_FAIL` or `MACRO_ABORT`. A successful Gradle exit alone is not a
passing test. These are synthetic callback regressions, not end-to-end hardware
latency benchmarks or proof of multiplayer/modpack compatibility.

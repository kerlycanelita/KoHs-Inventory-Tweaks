# One-shot cursor regression fixture (26.1.2)

This source is for the local **KoHs Inventory Debug** companion, not the shipping
Inventory Tweaks JAR. It requires that companion's DebugCollector, input invokers,
singleplayer guard and CloseHotbarRegressionLab poll-job bridge. The companion
currently lives in the local untracked `debug/kohs-inventory-debug-26.1.2` tree.

Integration in that companion:

- Add this directory to `sourceSets.client.java.srcDir` and add the root built
  JAR as `clientCompileOnly` as well as `clientRuntimeOnly`.
- Register macro `cursor-contract` to call `CursorContractLab.run` from the lab
  worker. Call `onOpened` at RETURN of CursorLandingController.onScreenOpened,
  and `onWarp` at HEAD of CursorLandingController.warp, using the debugger's
  existing observer mixin. Never install the fixture into the main mod.
- The bridge `CloseHotbarRegressionLab.atPoll` must be package-visible and drained
  at HEAD of SuperFastInventoryController.afterInputPoll. Jobs are singleplayer
  and focus guarded and wait only on the lab worker, never on the render thread.
- Use a 1440x1000 window so vanilla can actually select GUI 2x/3x/4x. Preserve
  both the requested and effective scale in the report.

From the repository root:

```powershell
.\gradlew.bat build --offline --no-daemon
.\gradlew.bat -p debug/kohs-inventory-debug-26.1.2 build runClient --offline --no-daemon -PdebugMacro=cursor-contract '-PdebugWorld=KoHs Debug QA' -PdebugExit=true
```

The named world must already exist in that disposable development directory.
Do not move the physical mouse during the approximately ten-second measurement
phase. The test intentionally relocates the desktop cursor inside the game;
external mouse input invalidates later-position assumptions and must not be
misreported as a mod defect. No multiplayer execution is allowed.

Expected result: `CURSOR_CONTRACT_SUMMARY openings=48/48; checks=1092; failures=0`
and no `MACRO_ABORT`. A successful Gradle exit alone is **not** a passing test:
the client auto-exits even if its macro fails. Settings are restored in finally;
the fixture does not save the temporary mod configuration or manipulate items.

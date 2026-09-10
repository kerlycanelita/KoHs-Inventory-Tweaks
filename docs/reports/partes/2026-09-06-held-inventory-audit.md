# Held inventory key and PvP input audit (26.1.2)

User request: audit rapid PvP opening, one-shot centering, abrupt mouse motion,
longer sessions and holding the inventory binding. Scope: the root 26.1.2 tree.

## Hypotheses and evidence before production changes

The existing tests emitted PRESS/RELEASE pairs, never GLFW_REPEAT. A held key
could therefore toggle the inventory despite those tests passing. This was a
hypothesis when the audit started, not an observed player-session diagnosis.

Exact-version source inspected in Loom's
`minecraft-clientonly-deobf-26.1.2-sources.jar`:

- `KeyboardHandler.keyPress` forwards both PRESS (1) and REPEAT (2) to
  `Screen.keyPressed`, and queues both through `KeyMapping.click` in gameplay.
- `AbstractContainerScreen.keyPressed` closes on a matching inventory key.
- `AbstractRecipeBookScreen.keyPressed` gives the recipe search first refusal.
- `Minecraft.setScreen` releases all mappings and initializes the screen.
- `MouseHandler.handleAccumulatedMovement` dispatches a drag if accumulated
  movement and a held mouse button survive into a newly opened screen.

Reproduce the bytecode inspection (PowerShell, substitute the local jar path):

```powershell
javap -c -p -classpath $clientJar net.minecraft.client.KeyboardHandler
javap -c -p -classpath $clientJar net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
javap -c -p -classpath $clientJar net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen
javap -c -p -classpath $clientJar net.minecraft.client.MouseHandler
```

[GLFW documents repeat events](https://www.glfw.org/docs/latest/input_guide.html#input_key)
as distinct from a physical press; their rate comes from keyboard settings.

Baseline `held-inventory` development run, log
`kohs-inventory-debug-20260906-153746.log`: 8 opens succeeded, but all 8
failed to keep the same screen through 12 repeat callbacks. Three of the 8
close-and-hold cycles reopened. 11 failed assertions out of 37.

## Intended changes and trust boundary

- Restrict repeat suppression to the unshared inventory binding with Super Fast
  Inventory enabled, in gameplay or the ordinary player inventory. Preserve
  fresh presses/releases, other keys, other GUIs, recipe-search editing and
  server-controlled inventories. There is no time-based debounce.
- Reject ambiguous multiple queued inventory presses rather than reclaiming a
  press previously handed to Vanilla.
- Clear inherited movement at the synchronous landing boundary for fallback
  opens as well as fast opens; never correct the pointer on future frames.
- Give debugger decisions a monotonic revision so an old early-open decision
  cannot be reported as a failure of an unrelated later poll.

Cursor positioning is local presentation (L0). Repeat suppression intentionally
removes automatic hold-generated close packets; it is **not** strict wire
equivalence for that hold sequence (L2 under the audit taxonomy). Fresh physical
presses still take the native action path. Existing early opening can change
when subsequent manual actions reach the server; this audit does not promise
identical packet timing, anticheat approval or network acceleration. No new
packet sender, transaction replay, rotation, cooldown or offhand automation is
introduced. Server-only checks see normal inventory messages, not local cursor
coordinates; inspecting installed mods requires additional client cooperation.

## Validation

The targeted development run `kohs-inventory-debug-20260906-155439.log`
completed on an offline integrated server with the root 26.1.2 jar. It covered
eight held-key cycles: 8/8 opens succeeded, every cycle remained on the same
screen for all 12 repeat callbacks (`sameOpening=12/12`), and the fresh press
after release still closed the inventory. The macro summary was
`openAttempts=8; openSuccess=8; openMisses=0; assertions=40; assertionFailures=0`.

The extended `aggressive-suite` run
`kohs-inventory-debug-20260906-155603.log` completed with 248/248 opens,
0 misses, 288 assertions and 0 failures. It included abrupt pointer movement,
inventory/offhand overlap, frame jitter, burst presses, long holds and the
held-repeat phase (`sameOpening=12/12` for all four cycles). There were no macro
aborts, cursor-repeat warnings or fast-poll decision mismatches.

The production build, translation verifier and mixin-target verifier all pass;
the latter resolved 67 member references and 9 invoke points against the exact
26.1.2 client jar.

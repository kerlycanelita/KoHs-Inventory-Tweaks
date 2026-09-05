# Runtime cost of the inventory tweaks

KoHs Inventory Tweaks hooks paths that Minecraft executes once per slot, once
per blit, or once per frame. This document records which hooks are hot, what
each one is allowed to do, and how to re-check the guarantees after a port.

The reference implementation is the Minecraft 26.1.2 tree at the repository
root. Every other target under `versions/` mirrors the same structure.

## Hooks that run in the frame loop

| Hook | Frequency | Rule |
|---|---|---|
| `GuiMixin#extractSlot` | 2x per hotbar slot, every frame, in normal gameplay | must cost nothing while no item highlight is configured |
| `AbstractContainerScreenMixin#extractSlot` | 2x per visible slot, every frame a container is open | one map lookup, no allocation |
| `GuiGraphicsExtractorMixin#blit` | every GUI blit of the whole client | rejects on configuration state before touching the screen |
| `GuiGraphicsExtractorMixin#blitSprite` | every sprite blit | screen type test first |
| `InventoryTextureManager#textureFor` | once per customized surface, every frame | recomposes only when the style key or animation frame changes |
| `InventoryGuiScaler#appliedScale` | several times per frame per screen | arithmetic only |
| `CompatibilityIssueManager#isFeatureAvailable` | reached from all of the above | single array read |

## What the hot paths must not do

**No conflict scanning per call.** `CompatibilityIssueManager` resolves feature
availability once, when the issue list is published at initialization, into a
`boolean[]` indexed by `CompatibilityFeature`. `isFeatureAvailable` reads that
array. It previously opened a stream over the issue list, and entered a
`synchronized` initializer, on every call - roughly 32 ns and one stream
allocation per query, with a few hundred queries per frame.

**No registry key strings per slot.** `ItemHighlighterController` keeps a
`Map<Item, ItemHighlight>` built from the published configuration and rebuilt
only when `ConfigStore` publishes a new instance, which is the only way the
highlight list can change. With no highlights configured the map is empty and
the lookup returns immediately. The previous path built an item id string and
scanned the highlight list for every slot of every frame, including the ten HUD
hotbar slots during ordinary gameplay.

**No slot geometry per pixel.** Composing a customized surface walks 65,536
pixels. Slot membership is now a precomputed `boolean[]` per layout instead of
up to ninety rectangle tests per pixel. This matters most with an animated
background, where the composition runs again whenever the GIF advances.

**No filesystem probe per frame.** A custom background is detected as edited
through its last-modified time. That probe is throttled to twice a second and
refreshed immediately on a resource reload or a configuration change, instead of
running one `Files.getLastModifiedTime` per rendered frame per surface.

**No slot list per blit.** `screenTextureFor` used to build a slot list and a
signature string for every blit of a container screen. The signature is now
resolved once per opened screen, and the whole hook exits on a cheap
configuration test when nothing is customized.

## Scaled surfaces and deferred elements

`InventoryGuiScaler.beginScaledSurface` / `endScaledSurface` own every scaled
pose the mod pushes. They also record the active scale, because Minecraft draws
the deferred tooltip in `Screen#extractRenderStateWithTooltipAndSubtitles`,
after the scaled pose has been popped. A tooltip anchor captured in surface
coordinates is therefore mapped back to screen coordinates in
`GuiGraphicsExtractor#setTooltipForNextFrameInternal`; without that step the
tooltip drifts away from the pointer by up to several hundred pixels at the
extremes of the scale range.

`ScreenBackgroundMixin` resets the recorded depth at the head of every screen
extraction so a foreign mod that cancels a scaled pass cannot leak depth into
the next frame.

## Re-checking a tree

```bash
python tools/verify-all-versions.py
```

That sweep runs `tools/verify-mixin-targets.py` against the root tree and every
directory under `versions/`. For a single tree:

```bash
python tools/verify-mixin-targets.py --source-root versions/kohs-inventory-tweaks-26.2
```

The verifier proves, against the exact Minecraft jar Loom compiled the tree
against, that every `@Mixin` target class exists, every injected method,
`@Accessor` field and `@Invoker` method exists, and every `INVOKE` injection
point is really called from inside the method being injected into. That last
check is the one that catches the class of defect behind the 26.2 startup crash
fixed in 1.0.5: a target that still exists somewhere in the class, but no longer
at the injection site.

A green sweep is not a substitute for opening the game. It proves the mixins
apply; it does not prove the tweaks behave. After a port, still check in game:
cursor landing per container type, the GUI scaler at both ends of its range with
tooltips hovered, an animated background, and the offhand swap during a fast
inventory open.

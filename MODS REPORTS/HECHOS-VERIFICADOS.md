# Hechos verificados del motor — Minecraft 26.1.2

Cada linea se comprobo contra el jar deobfuscado real, no contra la memoria ni
contra la documentacion. Al subir de version hay que volver a pasar los
comandos: los offsets cambian, y a veces cambia el comportamiento.

Jar de referencia:

```
~/.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar
```

---

## 1. Las devoluciones de GLFW se ejecutan EN LINEA durante `pollEvents`

**Verificado el 2026-09-05. Es el hecho cuya suposicion equivocada causo la
regresion de ese dia, asi que va el primero.**

`KeyboardHandler` y `MouseHandler` publican sus devoluciones con
`Minecraft#execute`, lo que invita a pensar que quedan encoladas hasta
`runAllTasks`. No es asi:

```
BlockableEventLoop.execute            si scheduleExecutables() -> schedule ; si no -> doRunTask (en linea)
BlockableEventLoop.scheduleExecutables    return !isSameThread()
ReentrantBlockableEventLoop.scheduleExecutables   return runningTask() || super.scheduleExecutables()
```

`Minecraft` extiende `ReentrantBlockableEventLoop` y no redefine
`scheduleExecutables`. Durante `pollEvents` estamos en el hilo del juego
(`isSameThread` cierto, luego `super` falso) y fuera de toda tarea
(`runningTask` falso). Ambas falsas, luego **`doRunTask`: se ejecuta al momento**.

Consecuencia practica: al volver de `RenderSystem.pollEvents()` ya se han
atendido todas las pulsaciones y todas las muestras de cursor de ese fotograma.
Ese es el punto mas temprano posible para decidir con el lote completo.

```bash
javap -c -p -cp "$JAR" net.minecraft.util.thread.BlockableEventLoop | sed -n '/public void execute/,/return/p'
javap -c -p -cp "$JAR" net.minecraft.util.thread.ReentrantBlockableEventLoop | sed -n '/scheduleExecutables/,/ireturn/p'
javap -cp "$JAR" net.minecraft.client.Minecraft | head -2   # confirma la superclase
```

---

## 2. Orden del bucle principal

Verificado el 2026-09-05.

```
Minecraft.run()
     90  RenderSystem.pollEvents()          <- aqui se atiende la entrada (ver 1)
    103  runTick(!oomFlag)

Minecraft.runTick(boolean)
    111  processQueuedPackets()
    124  runAllTasks()                      <- tareas de OTROS hilos, no la entrada
    245  tick()                             <- solo cuando salta el tick de 20 TPS
    406  MouseHandler.handleAccumulatedMovement()
    420  renderFrame()
```

El booleano de `runTick` solo se pone a cierto en el `catch` de
`OutOfMemoryError`, asi que en juego normal `runAllTasks` siempre se alcanza.

Una pantalla abierta antes del offset 245 hace que `tick()` no llame a
`handleKeybinds`, porque este solo corre con `overlay == null && screen == null`.

```bash
javap -c -p -cp "$JAR" net.minecraft.client.Minecraft > mc.txt
awk '/public void run\(\);/,0' mc.txt | head -60
awk '/private void runTick\(boolean\);/,0' mc.txt | grep -nE 'runAllTasks|Method tick:\(\)V|handleAccumulatedMovement|renderFrame|processQueuedPackets'
```

---

## 3. `handleKeybinds` consume exactamente 17 asignaciones con `consumeClick`

Verificado el 2026-09-05. Importa porque cualquier apertura anticipada deja
varada toda accion encolada que este metodo habria consumido.

```
keyAdvancements  keyAttack(x2)  keyChat     keyCommand   keyDrop
keyHotbarSlots   keyInventory   keyPickItem(x2)          keyQuickActions
keySmoothCamera  keySocialInteractions      keySpectatorHotbar
keySwapOffhand   keyToggleGui   keyTogglePerspective
keyToggleSpectatorShaderEffects              keyUse(x2)
```

`SuperFastInventoryController#queuedVanillaActions` las cubre todas menos
`keyInventory`, que es la propia. **Los atajos de otros mods no aparecen**: los
consume el propio mod en su tick, no `handleKeybinds`.

```bash
awk '/private void handleKeybinds\(\);/,0' mc.txt | grep -oE 'Options\.key[A-Za-z]+:|KeyMapping\.consumeClick'
```

---

## 4. La rama de apertura del inventario en vanilla

Verificada el 2026-09-05. La ruta rapida la reproduce instruccion por
instruccion.

```java
while (keyInventory.consumeClick()) {
    if (gameMode.isServerControlledInventory()) player.sendOpenInventory();
    else { tutorial.onOpenInventory(); setScreen(new InventoryScreen(player)); }
}
```

---

## 5. `MouseHandler#onMove` escribe `xpos`/`ypos` siempre

Verificado el 2026-09-05. No comprueba si hay pantalla abierta ni si el raton
esta capturado; solo acumula `accumulatedDX/DY` cuando la ventana esta activa.
`releaseMouse` fija el centro fisico y llama a `grabOrReleaseMouse`, y **no**
usa `setIgnoreFirstMove`.

Combinado con el hecho 1, la posicion que se escribe al abrir sobrevive: no
quedan muestras en cola que puedan pisarla.

```bash
javap -c -p -cp "$JAR" net.minecraft.client.MouseHandler | sed -n '/private void onMove/,/^  public/p'
```

---

## 6. `KeyMapping#matches(KeyEvent)` ignora los modificadores

Verificado el 2026-09-05. Compara el valor KEYSYM, o el scancode si la
asignacion es de tipo SCANCODE. Nada mas. Sostener Ctrl o Shift no impide que
case, asi que coincide con lo que `KeyMapping.click` encola.

```bash
javap -c -p -cp "$JAR" net.minecraft.client.KeyMapping | sed -n '/public boolean matches/,/ireturn/p'
```

---

## 7. `AbstractContainerScreen#isHovering` usa una banda de 18x18

Verificado el 2026-09-05 por lectura del comportamiento observado.

```java
mouseX -= leftPos; mouseY -= topPos;
return mouseX >= x - 1 && mouseX < x + w + 1
    && mouseY >= y - 1 && mouseY < y + h + 1;
```

Para la fila 0 del almacen (`y = 84`) la banda empieza en **83**, no en 84. El
centro de la pantalla cae justo en ese pixel, que es lo que hace tan fragil
apuntar por el centro.

---

## 8. `AbstractRecipeBookScreen#extractRenderState` no llama a `super`

Verificado en septiembre de 2026. Reparte hacia `extractContents`, de modo que
un inyector sobre `AbstractContainerScreen#extractRenderState` **nunca se aplica**
a las pantallas con recetario, el inventario del jugador incluido.

## 9. A held inventory key can toggle screens through GLFW_REPEAT

Verified against the 26.1.2 client sources and a development run on 2026-09-06.
`KeyboardHandler.keyPress` passes action 1 (PRESS) and 2 (REPEAT) to
`screen.keyPressed`; with no screen, either can reach `KeyMapping.click`.
`AbstractContainerScreen.keyPressed` closes on the inventory mapping regardless
of which action produced the KeyEvent (the KeyEvent itself has no action field).
The automatic keyboard repeat can therefore alternate close and open without
a second physical press. Our previous tests emitted only PRESS/RELEASE.

```powershell
$clientJar = 'C:\Users\KoH\.gradle\caches\fabric-loom\26.1.2\minecraft-client.jar'
javap -c -p -classpath $clientJar net.minecraft.client.KeyboardHandler
javap -c -p -classpath $clientJar net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
```

The recipe book's search field can consume the same key before container
closure, so repeat filtering must preserve that text-editing context.
See [the reproduction and boundary analysis](partes/2026-09-06-held-inventory-audit.md).

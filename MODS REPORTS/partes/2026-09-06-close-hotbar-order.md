# El inventario no cierra y la ender pearl acaba en una casilla

- **Fecha:** 2026-09-06, verificado y cerrado el 2026-09-07
- **Version del mod:** 1.0.7+mc26.1.2
- **Version de Minecraft:** 26.1.2
- **Estado:** real, corregido — con una parte que es comportamiento de vanilla

## La queja, sin traducir

> los jugadores detectan que el inventario se demora en abrir y cerrar
>
> sienten aveces que el inventario cierra lento y tambien eso les afecta por
> ejmplo en un pvp rapido me reportaron que al abrir el inventario y colocarse
> el totem muy rapido el jugador pensando que el inventraio ya cerro le da a la
> tacla donde tiene la ender pearl y como no se cerro el inventario la
> enderpearl se coloco en el slot del inventario y no cerro

## Hipotesis inicial

Escrita antes de mirar el codigo: que el arreglo de repeticiones de tecla
(`d183b1a`) se estuviera tragando una pulsacion **fresca** de cierre al
confundirla con la repeticion de la que abrio.

**Falsa.** `suppressInventoryKeyRepeat` empieza con
`if (action != GLFW.GLFW_REPEAT ...) return false`. Una pulsacion fresca nunca
entra ahi.

Segunda hipotesis, tambien escrita antes de comprobarla: que al cerrar con la
tecla se encolara un clic y la ruta rapida **reabriera** el inventario en el
mismo sondeo. Era plausible porque el booleano que decide si se encola se lee
**despues** de despachar la tecla a la pantalla, con una lectura fresca de
`this.minecraft.screen` (offset 818).

**Tambien falsa**, y por poco: hay una salida temprana en el offset 680 que lo
impide. Anotado como hecho 9.

## Premisas que hay que verificar

| Premisa | En HECHOS-VERIFICADOS? | Verificada |
|---|---|---|
| Cerrar desde `keyPressed` no encola clic | no lo estaba | cierta — hecho 9 |
| Vanilla abre una vez por clic encolado | no lo estaba | cierta — hecho 10 |
| El filtro de repeticiones solo afecta a `GLFW_REPEAT` | no | cierta, leyendo `d183b1a` |
| KoHs no hace trabajo caro al cerrar | no | cierta: `onScreenRequested(null)` limpia estado y sale |

## Investigacion

El cierre es **sincrono** y no lo toca este mod:
`AbstractContainerScreen.keyPressed -> onClose -> LocalPlayer.closeContainer`,
todo dentro de la propia devolucion de GLFW. No hay nada que acelerar ahi y no
se encontro trabajo caro de KoHs en esa ruta. La sensacion de «cierra lento» no
tiene un mecanismo detras.

Lo que si es real son dos cosas distintas que el jugador vive como una sola.

**Mecanismo 1 — la tecla de hotbar llega antes que el cierre.** Si la tecla de
la pearl y la tecla de inventario caen en el mismo sondeo y la primera llega
antes, la pantalla sigue abierta y `checkHotbarKeyPressed` hace el intercambio
normal de contenedor: la pearl se va a la casilla apuntada. El inventario cierra
justo despues. El jugador ve moverse la pearl, deduce que no habia cerrado, y
cuenta las dos cosas juntas. Reproducido 8 de 8 veces en el laboratorio del
2026-09-06 con el orden invertido; los 8 cerraron sincronicamente al llegar la E.

**Mecanismo 2 — las dos pulsaciones se pierden como una.** Si dos pulsaciones de
inventario caen antes de que se construya la apertura, vanilla encola dos clics
y el bucle `while (consumeClick())` abre dos veces (hecho 10). La segunda, que
el jugador queria como cierre, se pierde y el inventario queda **abierto**.
Reproducido 8 de 8 veces, con el inventario aun abierto a los 120 ms. Este es el
que produce literalmente el «no cerro», y el que deja la pantalla puesta para
que el mecanismo 1 ocurra despues.

Vanilla tiene el mismo defecto; solo cambia la ventana, un tick de 50 ms frente
a un sondeo de unos 5 ms.

## Conclusion

Real, no es alucinacion. Pero el cierre no es lento: lo que falla es que una de
las dos pulsaciones se pierde, y que el orden dentro de un mismo sondeo decide
si la tecla de hotbar intercambia o selecciona.

## Cambios aplicados

| Archivo | Que cambia | Commit |
|---|---|---|
| `inventory/SuperFastInventoryController.java` | dos pulsaciones frescas del mismo sondeo se anulan en vez de abrir dos veces | ver commit del 2026-09-07 |

La cancelacion exige las cuatro condiciones a la vez: exactamente dos
pulsaciones frescas de este sondeo, exactamente dos clics en cola, funcion
disponible y tecla no compartida. No reclama clics heredados de sondeos
anteriores ni toca la cola de ninguna otra asignacion.

## Justificacion

Se corrige el mecanismo 2 y **no** el 1.

El 2 es una perdida de entrada: el jugador pulso dos veces y solo se atendio una.
Devolverle el alternar es restituir lo que hizo.

El 1 no se toca aunque se podria: dentro de un mismo sondeo caben unos 5 ms, y
nadie decide en 5 ms «intercambia esta casilla al hotbar» y ademas «cierra». Se
podria reordenar por esa razon. No se hace porque intercambiar al hotbar con las
teclas numericas es una mecanica legitima y central, y corregir el orden que
tecleo el jugador es justo la clase de listeza que genera el siguiente informe de
fallo. Queda anotado como opcion consciente, no como olvido.

La cancelacion **no es equivalente a vanilla** para esa secuencia: vanilla
abriria. Se acepta porque solo actua con Super Fast Inventory encendido, que es
una ruta a la que el jugador ha optado explicitamente, y porque honra el
alternar que pidio.

## Verificado / no verificado

Laboratorio `close-hotbar` ejecutado en mundo real, antes y despues del arreglo:

| Informe | Ordenados/invertidos | Doble pulsacion |
|---|---|---|
| `20260906-230543` (linea base) | passed=32/32 | cases=8; **lateOpenings=8** |
| `20260907-100841` (con arreglo) | passed=32/32 | cases=8; **lateOpenings=0** |

Los ocho casos de doble pulsacion dejaban el inventario abierto y ahora ninguno
lo hace, sin mover los 32 casos ordenados e invertidos. `maxCloseCallback` medido
en 16344us, incluida la instrumentacion del propio macro dentro del sondeo.

Se ejecuta con:

```
./gradlew.bat -p debug/kohs-inventory-debug-26.1.2 runClient --offline     -PdebugMacro=close-hotbar -PdebugWorld="KoHs Debug QA" -PdebugExit
```

El informe queda en `run/logs/kohs-inventory-debug/`, no en `latest.log`.

Las 32 advertencias `CURSOR_SETTLE` con error de 146px son un artefacto del
laboratorio, no un defecto: el objetivo registrado es el centro que coloca Center
Mouse Fix y la posicion real es la casilla del totem, a donde el propio macro
mueve el cursor a proposito. La espera de casilla apuntada paso en los 32 ciclos,
asi que el puntero que el juego seguia era el correcto.

Verificado ademas: bytecode real de `KeyboardHandler#keyPress` (hechos 9 y 10);
compilacion; tabla de decision de 9 casos contrastando vanilla contra la ruta
corregida, con el caso clave reproducido sin la correccion.

No verificado:

- Que el jugador deje de percibir «cierre lento». No hay mecanismo detras de esa
  sensacion, asi que puede persistir aunque el defecto real este corregido.
- El mecanismo 1 sigue presente por decision, y volvera a aparecer si el jugador
  pulsa la tecla de hotbar antes que la de cierre.
- Cuatro o mas pulsaciones en un mismo sondeo siguen abriendo. Es fisicamente
  inalcanzable, pero la regla no es simetrica y conviene saberlo.

# Una pulsacion cedida a vanilla se recuperaba despues

- **Fecha:** 2026-09-05
- **Version del mod:** 1.0.7+mc26.1.2
- **Version de Minecraft:** 26.1.2
- **Estado:** real, corregido

## Origen

No hay queja literal. Salio de la auditoria pedida asi:

> auditaras el mod por completo y te haras decenas de preguntas como al correr
> muy rapido y hacer movimientos bruscos al mismo tiempo las funciones de
> inventory tweaks se aplican correctamente

Encaja, eso si, con el «se ghostea» que los jugadores venian describiendo al
ponerse el totem en peleas rapidas.

## Premisas que hay que verificar

| Premisa | En HECHOS-VERIFICADOS? | Verificada |
|---|---|---|
| Los fotogramas corren mucho mas rapido que el tick que vacia la cola | no | cierta: a 144 fps caben 7 lotes por tick |
| `handleKeybinds` no se llama con una pantalla abierta | no lo estaba | cierta — hecho 2 |
| Los atajos de otros mods no aparecen en la cola de vanilla | no lo estaba | cierta — hecho 3 |

## Investigacion

Cuando un lote contiene la tecla de inventario junto a otra accion, la apertura
se cede entera a vanilla y el clic se queda en su cola. Pero esa cola solo la
vacia el tick, y entre tick y tick caben varios lotes con el clic todavia dentro.

Cada uno de esos lotes volvia a evaluar el mismo clic. La comprobacion de
conflicto fisico solo mira el lote actual, asi que bastaba una pulsacion
cualquiera —una tecla de movimiento sirve— para que no encontrara nada y la ruta
anticipada se quedara con la pulsacion ya entregada.

Para acciones de vanilla el dano quedaba contenido: su clic tambien sigue
encolado y la segunda comprobacion lo ve. Para un atajo de otro mod no (hecho 3).
Y al abrirse la pantalla, `handleKeybinds` deja de llamarse (hecho 2), de modo
que la accion cedida queda varada hasta que el jugador cierra el inventario y
entonces se dispara tarde.

Banco de pruebas ejecutando el mismo escenario con y sin la guarda:

```
sin la guarda: lote 1 -> vanilla-fallback ; lote 2 -> early-open    <- recupera lo cedido
con la guarda: lote 1 -> vanilla-fallback ; lote 2 -> not-evaluated
```

## Cambios aplicados

| Archivo | Que cambia | Commit |
|---|---|---|
| `inventory/SuperFastInventoryController.java` | la decision pertenece al lote que contiene la pulsacion | `a6583d5` |

Una sola condicion: si el lote actual no observo la pulsacion de inventario, el
clic encolado ya fue juzgado y no se vuelve a juzgar.

## Justificacion

El campo `inventoryPhysicalInputNanos` ya existia y ya se limpiaba en cada
salida, pero solo se usaba para instrumentacion. Leerlo en la decision es el
cambio minimo que expresa la regla correcta.

Se descarto ampliar la lista de acciones encoladas para incluir atajos de otros
mods: no hay forma de saber cuando los consume su propio tick.

## Coste conocido

La guarda hace que los repliegues se cumplan siempre. Un jugador que aporrea
ataque mientras abre pasa a esperar al tick donde antes recibia, por el defecto,
la apertura anticipada. Es correcto, pero **es mas lento que el comportamiento
roto**, y conviene tenerlo presente si llega una queja de lentitud.

## Verificado / no verificado

Verificado: tabla de decision con y sin la guarda; compilacion; verificador de
mixins; arranque del cliente en un mundo.

No verificado: que el ghosteo del totem desaparezca en combate real.

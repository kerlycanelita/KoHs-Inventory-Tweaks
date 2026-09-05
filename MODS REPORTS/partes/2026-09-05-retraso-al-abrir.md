# Retraso al abrir el inventario

- **Fecha:** 2026-09-05
- **Version del mod:** 1.0.7+mc26.1.2
- **Version de Minecraft:** 26.1.2
- **Estado:** regresion propia, revertida

## La queja, sin traducir

> un jugador que hace opvo muy rapido nota que hay cierto delay ala hora de
> abrir el inventario o dice que no sabe que es exactamente pero como que antes
> de estos commits estaba mejor que puede ser

## Hipotesis inicial

Sospecha primera: la guarda de pulsacion cedida (`a6583d5`) hace que los
repliegues a vanilla se cumplan de verdad, y un jugador que aporrea ataque
mientras abre pasa a esperar al tick donde antes recibia la apertura anticipada.
Habria sido un coste real y correcto.

Resulto ser la otra, y la culpa no era del comportamiento sino de la premisa.

## Premisas que hay que verificar

| Premisa | En HECHOS-VERIFICADOS? | Verificada |
|---|---|---|
| Las devoluciones de GLFW se aplazan a `runAllTasks` | no lo estaba | **FALSA** — hecho 1 |
| `runAllTasks` corre despues de `processQueuedPackets` | no lo estaba | cierta — hecho 2 |

## Investigacion

El jar instalado en la instancia del jugador esta fechado el 2026-09-05 a las
13:47, que es la hora del commit `bca5630`. Confirmado: juega con los cuatro
commits, incluido `75cc3f9`.

`75cc3f9` movio el enganche de la decision desde justo despues de
`RenderSystem.pollEvents()` hasta justo despues de `runAllTasks()`. Se apoyaba
en que las devoluciones de entrada quedaban encoladas hasta ese vaciado. Al
comprobarlo contra el jar (hecho 1) resulta que se ejecutan **en linea dentro
del propio sondeo**.

Con eso caen las dos justificaciones a la vez:

- «Se gana un fotograma de latencia» — falso. El sondeo ya habia atendido las
  pulsaciones cuando la decision se tomaba en el punto original.
- «Se evita que muestras de cursor en cola pisen el puntero» — falso. Esas
  muestras tambien se aplicaron en linea, antes.

Lo que si era cierto es el coste. El punto nuevo esta despues de
`processQueuedPackets` y de `runAllTasks` (hecho 2), asi que cada apertura
espera a que terminen los dos. En un servidor cargado el procesado de paquetes
no es gratis.

## Conclusion

Real, y causada por el propio mod. La queja del jugador era literalmente exacta:
antes de esos commits estaba mejor.

## Cambios aplicados

| Archivo | Que cambia | Commit |
|---|---|---|
| `mixin/MinecraftInputDrainMixin.java` | eliminado | `e3f79d5` |
| `mixin/MinecraftInputPollMixin.java` | restaurado | `e3f79d5` |
| `kohs_inventory_tweaks.client.mixins.json` | vuelve al mixin original | `e3f79d5` |
| `inventory/SuperFastInventoryController.java` | javadoc restaurado | `e3f79d5` |
| `CHANGELOG.md` | entrada retirada | `e3f79d5` |

## Justificacion

Se revierte entero en vez de ajustarlo. Sin las dos premisas el cambio no tiene
ningun beneficio, solo un enganche estrictamente mas tardio. El punto original
es el mas temprano posible una vez el lote completo esta atendido.

Las otras tres correcciones de la auditoria no colgaban de esta premisa y se
mantienen.

## Verificado / no verificado

Verificado: bytecode real de `BlockableEventLoop`, `ReentrantBlockableEventLoop`
y `Minecraft`; compilacion; verificador de mixins (63 referencias, 9 puntos
INVOKE).

No verificado: que el retraso haya desaparecido para el jugador. El jar con la
reversion existe en `build/libs` pero no se instalo en su perfil.

## Que habria evitado esto

Un solo comando `javap` sobre `BlockableEventLoop.execute` antes de escribir la
justificacion. La premisa sonaba razonable —`Minecraft#execute` esta ahi, en el
codigo, delante de los ojos— y por eso mismo no se comprobo. **Que una premisa
parezca obvia es precisamente cuando hay que comprobarla**, porque nadie la va a
cuestionar despues.

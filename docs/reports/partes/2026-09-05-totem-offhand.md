# El totem no entra en la offhand desde el inventario

- **Fecha:** 2026-09-05
- **Version del mod:** 1.0.7+mc26.1.2
- **Version de Minecraft:** 26.1.2
- **Estado:** real, es configuracion — no hace falta cambiar codigo

## La queja, sin traducir

> al colocar el totem con la offhand con el boton en el inv notan aveces
> lentitud o que aveces no funciona investiga si es real o una alucinacion

## Hipotesis inicial

Que Cursor Landing estuviera desactivado por el gate de compatibilidad (Herzium
y RawInputBuffer estan instalados), dejando el cursor en el centro de vanilla.

La primera mitad resulto **falsa** y la segunda **cierta**, por otro motivo.

## Premisas que hay que verificar

| Premisa | En HECHOS-VERIFICADOS? | Verificada |
|---|---|---|
| El gate de compatibilidad apaga Cursor Landing en esa instancia | no | **FALSA** — el log no registra ninguna incidencia |
| La tecla de offhand actua sobre la casilla apuntada | no | cierta |
| La banda de `isHovering` empieza en `y-1` | no lo estaba | cierta — hecho 7 |

## Investigacion

Instancia: `ModrinthApp/profiles/1.21..11111111`.

El log del cliente **no registra ninguna incidencia de compatibilidad**, asi que
Cursor Landing esta disponible pese a Herzium y RawInputBuffer. La hipotesis del
gate era falsa.

La configuracion real del jugador:

```
superFastInventory   = true
centerMouseFix       = true
inventory            = <ausente>    <- sin punto de aterrizaje
inventoryLandingItem = <ausente>    <- sin objeto de aterrizaje
inventoryGuiScalerEnabled = true
inventoryGuiScale         = 2.2952
```

Sin punto ni objeto, `shouldPlaceCursor` sigue siendo cierto por Center Mouse
Fix, pero `overrideReleasePosition` devuelve `null` a proposito y **vanilla
centra el cursor**. Donde cae ese centro dentro del panel:

```
1920x1080 -> panel (88.0, 83.0)     1366x768 -> panel (88.5, 83.0)
2560x1440 -> panel (88.5, 83.0)     1280x720 -> panel (88.0, 83.0)
```

Siempre fila 0 del almacen, columna 5. Y `y = 83` es el **primer pixel aceptado**
de esa fila (hecho 7): un pixel mas arriba y no hay casilla apuntada.

El escalador del inventario no lo cambia: escala alrededor del centro, y la
transformacion inversa de la coordenada del raton deja el punto central igual.

## Conclusion

Real, no es alucinacion. El mecanismo:

1. La tecla de offhand intercambia **la casilla apuntada**, no el totem.
2. El cursor aterriza siempre en el mismo sitio, fila 0 columna 5, porque no hay
   punto ni objeto configurados.
3. Si el totem no esta en esa casilla, la tecla no hace nada util.
4. **Al abrir el recetario el panel se desplaza** (`left = 177 + (gw - 176 - 200) / 2`),
   y ese mismo centro pasa a apuntar la columna 1. La misma tecla, otra casilla,
   segun si el libro esta abierto. De ahi el «a veces».
5. La «lentitud» es el viaje del raton del centro al totem, largo porque hay
   guiScale 3 con el escalador a 2.295.

## Cambios aplicados

Ninguno. La funcion que lo resuelve ya existe en el jar que el jugador tiene:
**objeto de aterrizaje**, en el modal de Cursor Landing, pestana Inventario. Se
fija sosteniendo el totem y pulsando el boton. Nunca se activo.

## Justificacion

No se toca codigo porque el comportamiento es el correcto: sin punto ni objeto,
Center Mouse Fix pide el centro de vanilla y eso es lo que hace. El defecto es
que la funcion que lo arregla no es evidente para el jugador.

Pendiente de decidir, no aplicado: que la pestana de Cursor Landing diga en que
casilla va a aterrizar el centro cuando no hay nada configurado. Convertiria un
«a veces no funciona» en algo visible antes de la pelea.

## Verificado / no verificado

Verificado: la configuracion real del jugador; la aritmetica del centro para
siete resoluciones; la ausencia de incidencias en su log.

No verificado: que fijar el objeto de aterrizaje lo resuelva en su experiencia.
Requiere que lo active y juegue.

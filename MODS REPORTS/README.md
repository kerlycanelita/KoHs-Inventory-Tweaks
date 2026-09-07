# MODS REPORTS

Registro de quejas de jugadores, cambios aplicados y la evidencia que los
sostiene. Existe por un motivo concreto: el 5 de septiembre de 2026 se aplico un
cambio basado en un comportamiento del motor que se dio por sabido y resulto ser
falso. El cambio no arreglaba nada y anadia retraso. Lo detecto un jugador, no
las pruebas.

## La regla

**Toda afirmacion sobre como se comporta Minecraft que sirva para justificar un
cambio tiene que estar en [HECHOS-VERIFICADOS.md](HECHOS-VERIFICADOS.md) con su
comando de verificacion, o verificarse antes de tocar el codigo.**

No vale recordarlo. No vale que suene razonable. No vale que otro archivo del
proyecto lo afirme. Si no hay un comando que lo demuestre contra el jar real, la
afirmacion no existe todavia.

Corolario: si un arreglo se apoya en dos premisas, las dos necesitan su linea.
El error de septiembre tenia dos justificaciones y ambas colgaban de la misma
premisa no verificada, asi que las dos cayeron juntas.

## Que se guarda aqui

- `HECHOS-VERIFICADOS.md` — comportamiento del motor comprobado contra el jar,
  con el comando para volver a comprobarlo cuando cambie la version.
- `partes/` — un parte por queja o investigacion. Plantilla en
  [partes/PLANTILLA.md](partes/PLANTILLA.md).

## Como se usa antes de un cambio

1. Escribe la queja en un parte nuevo, con las palabras del jugador.
2. Anota la hipotesis **antes** de mirar el codigo, para poder equivocarte por
   escrito.
3. Busca en `HECHOS-VERIFICADOS.md` lo que tu hipotesis da por cierto. Lo que no
   este, verificalo y anadelo.
4. Solo entonces cambia codigo.
5. Cierra el parte con lo que se verifico y, sobre todo, **con lo que no se
   pudo verificar**.

## Indice

| Fecha | Parte | Estado |
|---|---|---|
| 2026-09-05 | [Retraso al abrir el inventario](partes/2026-09-05-retraso-al-abrir.md) | Regresion propia, revertida |
| 2026-09-05 | [El totem no entra en la offhand](partes/2026-09-05-totem-offhand.md) | Real, es configuracion |
| 2026-09-05 | [Pulsacion cedida que se recuperaba](partes/2026-09-05-pulsacion-cedida.md) | Corregido |
| 2026-09-06 | [El inventario no cierra y la pearl acaba en una casilla](partes/2026-09-06-close-hotbar-order.md) | Corregido, con parte que es vanilla |
| 2026-09-06 | [Held inventory key and PvP input audit](partes/2026-09-06-held-inventory-audit.md) | Repeat-toggle reproduced and corregido; regression passed |

<p align="center">
  <a href="WIKI.md"><img alt="Read in English" src="https://img.shields.io/badge/Language-English-7c3aed?style=for-the-badge"></a>
</p>

# Wiki de KoHs Inventory Tweaks

Esta guía corresponde exclusivamente a **KoHs Inventory Tweaks 1.0.1 para Minecraft 26.1.2**. El mod es el sucesor de **KoHs Inv Cursor** y fue reconstruido para la nueva versión del juego.

## Contenido

- [Requisitos](#requisitos)
- [Abrir la configuración](#abrir-la-configuración)
- [Interfaz principal](#interfaz-principal)
- [Cursor Landing](#cursor-landing)
- [Inventory Tweaks](#inventory-tweaks)
- [Customization](#customization)
- [Fondos personalizados](#fondos-personalizados)
- [GUI Scaler](#gui-scaler)
- [Item Highlighter](#item-highlighter)
- [Resource packs](#resource-packs)
- [Guardado y archivos](#guardado-y-archivos)
- [Solución de problemas](#solución-de-problemas)

## Requisitos

- Minecraft Java Edition 26.1.2.
- Fabric Loader 0.19.3 o superior.
- Fabric API para 26.1.2.
- Java 25 o superior.
- Mod Menu es opcional, pero recomendado para acceder a la configuración.

El mod funciona del lado del cliente. Un servidor Vanilla no necesita instalarlo.

## Abrir la configuración

1. Entra a un mundo o servidor.
2. Pulsa **Y**, la tecla predeterminada del menú de configuración.
3. Puedes cambiarla desde la pantalla principal del mod o desde los controles de Minecraft.

También puedes abrir el menú desde **Mods → KoHs Inventory Tweaks → Configuración** si tienes Mod Menu instalado.

La configuración no se abre desde el menú principal. En ese caso se muestra un aviso porque las previews necesitan el jugador, los modelos de objetos y los resource packs ya cargados.

## Interfaz principal

El inventario real del jugador aparece en el centro. Los grupos de funciones están separados en dos paneles desplazables:

- **Behavior:** Cursor Landing e Inventory Tweaks.
- **Appearance:** Customization, Item Highlighter y GUI Scaler.

El selector inferior permite escoger la textura base:

- **Applied:** utiliza la textura suministrada por los resource packs activos.
- **Vanilla:** utiliza directamente la textura original incluida por Minecraft.

Los colores y fondos del mod se componen encima de la fuente elegida. Cambiar esta opción actualiza también las previews.

## Cursor Landing

Cursor Landing permite escoger el punto exacto donde aparecerá el cursor al abrir una pantalla compatible.

### Configurar una posición

1. Abre **Cursor Landing**.
2. Selecciona Inventory, Chest, Ender Chest o Barrel.
3. Para Chest, escoge **×1** o **×2** para diferenciar cofre simple y doble.
4. Haz clic dentro de la preview en la posición deseada.
5. Pulsa **Save & Exit**.

Las coordenadas se guardan de forma normalizada, por lo que se adaptan al tamaño de ventana y a la escala GUI.

### Activar o desactivar contenedores

Chest, Ender Chest y Barrel tienen interruptores independientes. Si uno está desactivado, ese contenedor conserva el cursor Vanilla aunque exista una posición guardada.

**Reset All** elimina todas las posiciones y reactiva los contenedores. Si sales con `Esc` teniendo cambios pendientes, el mod muestra una advertencia antes de descartarlos.

## Inventory Tweaks

### Center Mouse Fix

Minecraft o algún mod puede producir un evento de centrado mientras se abre el inventario del jugador. Center Mouse Fix mantiene una ventana de verificación de aproximadamente 100 ms y restaura una sola vez la posición configurada si detecta ese evento.

No genera efecto de arrastre, no bloquea los movimientos posteriores del jugador y no afecta cofres, Ender Chests, barriles ni otros contenedores. También funciona con la posición central Vanilla del inventario cuando no existe una posición personalizada.

### SuperFastInventory

SuperFastInventory elimina la espera innecesaria al crear la pantalla local del inventario y protege las combinaciones rápidas de inventario y offhand:

- Conserva durante 125 ms una pulsación de offhand realizada casi al mismo tiempo que la tecla de inventario.
- Espera a que Minecraft haya calculado el slot real bajo el cursor.
- Ejecuta una única acción Vanilla `SWAP` con el botón de offhand sobre ese slot.
- Cancela la operación si hay un objeto transportado por el cursor, el jugador es espectador, la pantalla cambió o el servidor controla la apertura.

No duplica objetos, no automatiza clicks repetidos y no modifica paquetes para exceder las reglas Vanilla.

## Customization

La preview se encuentra a la izquierda. Pulsa su selector para alternar entre:

- Inventory.
- Single Chest.
- Double Chest.
- Barrel.
- Ender Chest.

Todas las configuraciones están en la columna desplazable derecha y afectan al inventario y a los contenedores genéricos compatibles.

### Cambiar el marco del inventario

1. Localiza **Inventory frame**.
2. Escoge un color en la paleta.
3. Ajusta **Opacity** entre 0 y 255.

Una opacidad de `0` vuelve transparente la capa de marco; `255` la vuelve completamente visible.

### Cambiar los marcos de slots

1. Localiza **Slot frames**.
2. Escoge el color.
3. Ajusta su opacidad.

Esta capa modifica la apariencia del slot sin recolorear el modelo del objeto que contiene.

### Restablecer

**Reset** devuelve colores, opacidades, fuente de textura y fondo personalizado a sus valores iniciales.

## Fondos personalizados

Selecciona **Choose file** dentro de **Custom inventory background**. Antes de importar cualquier archivo se abre una pantalla de recorte obligatorio con proporción **176:166**.

- Arrastra la imagen para encuadrarla.
- Usa el control de zoom.
- Pulsa **Apply Crop** para procesarla.

### Formatos y límites

| Tipo | Formatos | Límites |
|---|---|---|
| Imagen | PNG, JPG, JPEG, BMP | Máximo 32 MB y 2048 px por dimensión |
| Animación | GIF | Máximo 32 MB, 2048 px por dimensión y 100 frames |
| Video | MP4, MOV con contenido compatible con JCodec | 1–10 segundos, mínimo 176×166, máximo 64 MB y 2048 px |

Los videos se decodifican localmente a 10 FPS y se guardan como un GIF en bucle. No se sube el archivo a servicios externos.

Los fondos importados se almacenan en:

```text
config/kohs_inventory_tweaks/backgrounds/
```

El slider **Background opacity** controla solamente el fondo personalizado.

## GUI Scaler

GUI Scaler modifica exclusivamente el inventario del jugador. No cambia la escala global de Minecraft, la hotbar ni otros contenedores.

1. Abre **GUI Scaler**.
2. Lee y acepta la advertencia. Puedes marcar **Do Not Show Again**.
3. Activa el interruptor superior.
4. Ajusta el slider entre **65 % y 175 %**.

El inventario, los slots, objetos, textos y modelo del jugador se escalan como una sola unidad. El máximo efectivo se reduce automáticamente si la ventana o el libro de recetas no tienen espacio suficiente.

Al desactivar la función, el inventario vuelve a obedecer la escala GUI Vanilla.

## Item Highlighter

### Añadir un objeto

1. Abre **Item Highlighter**.
2. Usa el buscador del catálogo Vanilla.
3. Haz clic en un objeto. Se añadirá al panel izquierdo usando su render activo.
4. Haz clic en el objeto del panel izquierdo para abrir su editor.

Se pueden guardar hasta 256 objetos diferentes.

### Colores

- **Slot background:** color detrás del objeto.
- **Item border:** borde exterior del resaltado.

La textura y el modelo del objeto no se recolorean.

### Hotbar

Activa **Hotbar** para mostrar el resaltado del objeto también en la hotbar del HUD.

### Dynamic

Con **Dynamic** activo, los objetos coincidentes permanecen Vanilla mientras el cursor no esté sobre uno de ellos. Al colocar el cursor sobre un ejemplar, todos los objetos configurados con ese mismo identificador se iluminan; al retirarlo vuelven inmediatamente al aspecto Vanilla.

La detección usa el `hoveredSlot` calculado por Minecraft antes de renderizar los slots, por lo que funciona tanto en las previews como en inventarios reales.

### Done, Reset y Remove item

- **Done:** guarda y vuelve al selector.
- **Reset:** conserva el objeto, pero restaura sus colores y opciones.
- **Remove item:** elimina ese objeto del resaltador.
- **Reset All:** elimina toda la lista de objetos configurados.

## Resource packs

Las previews y los objetos utilizan los recursos actualmente activos:

- La opción **Applied** carga la GUI suministrada por el resource pack de mayor prioridad.
- **Vanilla** omite cambios de packs para la textura base del inventario.
- Las capas de color y el fondo de KoHs Inventory Tweaks siempre se aplican encima de la base elegida.
- Item Highlighter utiliza el modelo y la textura activa de cada objeto; solamente modifica el slot y su borde.

Después de recargar resource packs, las texturas compuestas se invalidan y se generan nuevamente.

## Guardado y archivos

Los cambios se guardan automáticamente al utilizar los controles y también al cerrar con los botones de confirmación. El archivo principal es:

```text
config/kohs_inventory_tweaks.json
```

Antes de escribir, el mod valida rangos, IDs de objetos, nombres de archivo y coordenadas. El JSON se escribe primero en un temporal y después se reemplaza de forma atómica para reducir el riesgo de corrupción.

Para realizar una copia de seguridad, guarda el JSON y la carpeta `config/kohs_inventory_tweaks/backgrounds/`.

## Solución de problemas

### La configuración no abre desde el menú principal

Es intencional. Entra a un mundo o servidor para que existan un jugador y recursos cargados.

### El cursor queda Vanilla en un cofre

Comprueba que el interruptor Chest esté activado y que hayas guardado una posición para el tipo correcto: ×1 o ×2.

### El fondo no se importa

Verifica formato, tamaño, resolución, duración y número de frames según la tabla anterior. Algunos archivos MOV o MP4 usan códecs que JCodec no puede decodificar.

### El inventario no alcanza el 175 %

El límite se adapta a la ventana para evitar que slots o botones queden fuera de la pantalla. El libro de recetas visible reduce aún más el máximo seguro.

### Dynamic no aparece

Confirma que el objeto fue añadido, que Dynamic está activado en su editor y que el cursor está sobre un slot que contiene exactamente ese objeto.

### Reportar un error

Incluye la versión de Minecraft, Fabric Loader, Fabric API, lista de mods, resource packs activos y pasos exactos para reproducirlo en [GitHub Issues](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/issues).

También puedes solicitar ayuda en [Discord](https://discord.gg/9t2VxEF7UU).

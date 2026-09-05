<p align="center">
  <a href="WIKI.md"><img alt="Read in English" src="https://img.shields.io/badge/Language-English-7c3aed?style=for-the-badge"></a>
</p>

# Wiki de KoHs Inventory Tweaks

Esta guía corresponde a **KoHs Inventory Tweaks 1.0.x para Minecraft 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11 y 1.21.10**. El mod es el sucesor de **KoHs Inv Cursor**.

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

- Minecraft Java Edition 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11 o 1.21.10.
- Fabric Loader 0.19.3 o superior.
- Fabric API correspondiente a la versión de Minecraft seleccionada.
- Java 25 o superior para Minecraft 26.x; Java 21 o superior para Minecraft 1.21.11 y 1.21.10.
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
2. Selecciona Inventory, Chest, Shulker Box, Ender Chest o Barrel.
3. Para Chest, escoge **×1** o **×2** para diferenciar cofre simple y doble.
4. Haz clic dentro de la preview en la posición deseada.
5. Pulsa **Save & Exit**.

Las coordenadas se guardan de forma normalizada, por lo que se adaptan al tamaño de ventana y a la escala GUI.

### Activar o desactivar contenedores

Chest, Shulker Box, Ender Chest y Barrel tienen interruptores independientes. Si uno está desactivado, ese contenedor conserva el cursor Vanilla aunque exista una posición guardada.

**Reset All** elimina todas las posiciones y reactiva los contenedores. Si sales con `Esc` teniendo cambios pendientes, el mod muestra una advertencia antes de descartarlos.

## Inventory Tweaks

### Center Mouse Fix

Center Mouse Fix entrega a `MouseHandler#releaseMouse` la posición personalizada o centrada Vanilla exactamente una vez por cada apertura del inventario del jugador. No refina la posición durante la inicialización, no verifica frames renderizados ni aplica interpolación o correcciones posteriores.

No genera efecto de arrastre, no bloquea los movimientos posteriores del jugador y no afecta cofres, Ender Chests, barriles ni otros contenedores. Una posición configurada aparte en Cursor Landing sigue siendo solamente una colocación visual local y nunca cambia una acción del inventario.

### Inventario superrápido

Al activarlo, la pulsación física de la tecla del inventario —o un botón del mouse reasignado— construye inmediatamente el `InventoryScreen` local en lugar de esperar al siguiente tick del cliente. Consume únicamente el clic lógico de inventario que Vanilla acaba de encolar para impedir una apertura duplicada. Si offhand, hotbar, ataque, uso, soltar o recoger ya están encolados en ese tick, KoHs no adelanta la apertura y deja que Vanilla procese el lote completo. No encola offhand, no inventa clics, no reintenta acciones, no reinicia cooldowns ni envía paquetes directamente. Los inventarios controlados por el servidor siempre conservan su ruta Vanilla.

Esto adelanta la apertura local y una posible interacción posterior hasta un tick. Los tipos, contenido y orden de paquetes, la validación de slots y los manejadores siguen siendo Vanilla, aunque el servidor puede observar el tiempo naturalmente anterior de un clic hecho después de que aparezca la pantalla adelantada.

### Eliminar absolutamente todas las animaciones del inventario

Esta opción utiliza un frame estático para fondos animados, elimina animaciones visuales propias del inventario y suprime el brillo animado de objetos encantados mientras se dibujan los slots.

No modifica la lógica del servidor, la propiedad de objetos ni las transacciones. La hotbar del HUD, los objetos sostenidos, las entidades y el mundo conservan su render Vanilla.

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

GUI Scaler modifica el inventario del jugador sin cambiar la escala GUI global de Minecraft ni la hotbar. Su pantalla **Affect Containers** puede aplicar la misma escala seleccionada a cofres simples, cofres dobles, Shulker Boxes, barriles y Ender Chests.

1. Abre **GUI Scaler**.
2. Lee y acepta la advertencia. Puedes marcar **Do Not Show Again**.
3. Activa el interruptor superior.
4. Ajusta el slider entre **65 % y 315 %**. Una instalación nueva y Restablecer comienzan en **200 %**.

En **Affect Containers** puedes previsualizar cada contenedor compatible. Con su interruptor desactivado, la preview permanece al 100 % Vanilla. Al activarlo, la preview y los contenedores reales usan la escala elegida. Esta opción viene activada por defecto en una instalación nueva y puede desactivarse en cualquier momento.

El inventario, los slots, objetos, textos y modelo del jugador se escalan como una sola unidad. El máximo efectivo se reduce automáticamente si la ventana o el libro de recetas no tienen espacio suficiente.

Al desactivar el scaler principal, el inventario y los contenedores compatibles vuelven a obedecer la escala GUI Vanilla.

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

## Visibilidad de jugadores

Abre **Customization**, baja hasta **Visibilidad de jugadores** y selecciona **Configurar**. Incluye resaltado del modelo mediante paleta, iluminación local, distancia y pulso opcional. Su preview extrae la skin y el equipo actuales del jugador y los muestra caminando sin modificar la entidad real.

El efecto se ejecuta únicamente mientras el inventario del jugador está abierto y siempre fuera de su panel. Los bloques siguen ocultando jugadores y nunca utiliza un contorno visible a través de paredes. Las opciones dependientes permanecen ocultas mientras Visibilidad de jugadores está desactivada; **Intensidad de profundidad del jugador** aparece solamente después de activarla.

Perfiles, Resaltador inteligente y el catálogo general de Herramientas avanzadas ya no forman parte de la interfaz actual. Item Highlighter aplica solamente reglas explícitas elegidas por el usuario.

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

### El inventario no alcanza el 315 %

El límite se adapta a la ventana para evitar que slots o botones queden fuera de la pantalla. El libro de recetas visible reduce aún más el máximo seguro.

### Dynamic no aparece

Confirma que el objeto fue añadido, que Dynamic está activado en su editor y que el cursor está sobre un slot que contiene exactamente ese objeto.

### Reportar un error

Incluye la versión de Minecraft, Fabric Loader, Fabric API, lista de mods, resource packs activos y pasos exactos para reproducirlo en [GitHub Issues](https://github.com/kerlycanelita/KoHs-Inventory-Tweaks/issues).

También puedes solicitar ayuda en [Discord](https://discord.gg/9t2VxEF7UU).

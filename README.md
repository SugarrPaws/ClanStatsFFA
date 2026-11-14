# ClanStats FFA

Mod de **cliente** para Fabric (Minecraft Java **1.21.11** y **26.3**) pensado para FFA de CubeCraft.
*English version below — [English](#english).*

- Separas jugadores específicos en **clanes**. El nombre del clan puede tener **espacios y símbolos** (`Los Pros`, `[ES] Team #1`, `Águilas.GG`).
- Cuenta **kills y muertes solo entre miembros de clanes** (las kills contra randoms no cuentan para el clan).
- **Top de jugadores de cada clan** por kills o KD, y **total de cada clan** (kills, muertes, KD y enfrentamientos clan contra clan).
- Aparte, **conteo de kills y muertes de todos los jugadores de la lobby**.
- **Menú visual** (tecla **J**) con la cabeza de cada jugador (también de los que no están en la sala), quién está **conectado o desconectado** y un contador por clan.
- **Jugadores en sala**: todos los de la lobby con sus kills y muertes totales, ordenados por kills o KD.
- **32 colores** para los clanes o cualquier color con código hexadecimal (`#FF8800`). El color se ve en el menú, el HUD, el chat, en los **nombres de la lista de Tab** y en el **nombre encima de cada jugador** (solo el color, sin etiqueta). Funciona junto con mods de tiers como **TierTagger** y **QTiers**: se ven el tier y el color a la vez.
- Botón para **activar o pausar el mod** y otro para **ocultar a los desconectados** de las listas (sus stats se siguen guardando).
- Los jugadores se guardan siempre con su **UUID**: si alguien se cambia el nombre, sigue en su clan y sus kills siguen siendo suyas.
- Textos en **español o inglés** (menú, HUD, chat y resumen de Discord). Por defecto sigue el idioma del juego.
- HUD en pantalla, comandos `/clanes ...` (o `/clans ...` en inglés) y un **resumen con formato de Discord**.

Como no puedes instalar nada en el servidor de CubeCraft, el mod **lee los mensajes de kills del chat** y lleva la cuenta en tu PC. No envía nada al servidor de Minecraft. En internet solo consulta a Mojang: la API pública para saber el UUID de un jugador que agregas cuando no está en la sala, y las skins de los miembros desconectados para dibujar su cabeza (lo mismo que hace el juego con las cabezas de jugador).

---

## 1. Conseguir el `.jar`

Elige una de estas dos formas. Las dos descargan Minecraft y las librerías de Fabric la primera vez (tarda unos minutos).

### Opción A: con GitHub (no instalas nada)

1. Crea una cuenta en <https://github.com> y un repositorio nuevo (puede ser privado).
2. En el repositorio: **Add file → Upload files** y arrastra **todo el contenido** de esta carpeta. La subida por la web se salta la carpeta `.github`: créala a mano con **Add file → Create new file**, nombre `.github/workflows/build.yml`, y pega el contenido de ese archivo. Dale a **Commit changes**.
3. Ve a la pestaña **Actions**. La compilación arranca sola (unos 3-6 minutos).
4. Entra a la ejecución terminada (palomita verde) y, abajo en **Artifacts**, descarga `clanstats-mc1.21.11` (la de Lunar 1.21.11) o `clanstats-mc26.3`. Es un `.zip` con el `.jar` adentro.

Si sale una cruz roja, abre el paso que falló y copia el error: con eso se corrige rápido.

### Opción B: compilar en tu PC

1. Instala **JDK 25** (Eclipse Temurin: <https://adoptium.net>). Sirve para las dos versiones.
2. Abre una terminal dentro de `versions/mc1.21.11` (o `versions/mc26.3`).
3. Windows: `gradlew.bat build` · Mac/Linux: `./gradlew build`
4. El mod queda en `build/libs/clanstats-1.3.0+mc1.21.11.jar` (o `...+mc26.3.jar`).

## 2. Instalar

### En Lunar Client (1.16.5 o más nuevo)

1. En el launcher de Lunar abre el selector de versiones (botón de la izquierda) y elige **1.21.11** con **Fabric**.
2. Abre los mods de esa versión (botón de abajo a la derecha, pestaña **Mods**) y **arrastra el `.jar`** de ClanStats. Si tenías la versión anterior, bórrala primero (no dejes dos ClanStats).
3. **No agregues Fabric API**: Lunar ya la trae incluida.
4. Entra a CubeCraft y pulsa **J** para abrir el menú.

### En el launcher normal de Minecraft

1. Instala **Fabric Loader** para tu versión (<https://fabricmc.net/use>).
2. Descarga **Fabric API** de la **misma versión** de Minecraft (Modrinth) y ponla en `.minecraft/mods`.
3. Pon el `.jar` de ClanStats en `.minecraft/mods`.

## 3. El menú (1.21.11)

Se abre con la tecla **J**, con `/clanes menu` o, si tienes **Mod Menu**, con el botón de configuración de ClanStats.

**Izquierda, clanes**
- Escribe un nombre (hasta 24 caracteres, con espacios y símbolos) y pulsa **Crear** (o Enter).
- Cada clan muestra cuántos miembros están en la sala: `Lobos  3/5` = 3 conectados de 5.
- **Color** abre la paleta (ver abajo). **Borrar clan** pide un segundo clic para confirmar.

**Derecha, miembros del clan elegido**
- Arriba, la fila **Total** con las kills (K), muertes (M) y KD del clan **entre clanes**, y cuántos están **en sala** y cuántos **fuera**.
- Cada miembro con su cabeza (la skin real aunque no esté en la sala), sus K/M/KD entre clanes y un punto **verde = en la sala**, **gris = desconectado** (el nombre también sale en gris). **x** lo quita del clan.
- **Añadir por nombre (registra uuid)**: escribe el nombre (Tab completa con los de la sala) y pulsa **Añadir**. Se guarda con el UUID de su cuenta: si está en la sala se agrega al instante; si no, se busca en Mojang y se agrega cuando responde. **Si el nombre no es de ninguna cuenta, no se guarda** y te avisa.
- Al pasar el mouse por un jugador, abajo se ve su UUID y si está conectado.

**Botones debajo del panel derecho**
- **Añadir desde la lista**: los jugadores de la sala que **no tienen clan** (con **+** para añadirlos) y, al final, los que ya están en el clan elegido (con ✔).
- **Jugadores en sala**: **todos** los de la sala, con su clan en color y sus kills y muertes **totales** (contra cualquiera), ordenados por kills o KD.

**Colores**: **Color** muestra una paleta de 32 colores; un clic lo cambia. Abajo puedes escribir un código hexadecimal (`#FF8800`) y pulsar **Aplicar** (o Enter). **« Color** vuelve a los miembros.

**Abajo**
- **HUD**: qué muestra el HUD (clanes, lobby, ambos o nada).
- **Mod: Activado / Desactivado**: pausa el mod. Desactivado no cuenta kills ni muestra el HUD ni los colores; tus clanes y stats se quedan guardados y el menú se sigue abriendo para volver a activarlo.
- **Desconectados: sí / no**: muestra u oculta a los miembros que no están en la sala, en el menú y en el HUD. Sus stats se siguen guardando y cuentan para el total del clan y el resumen de Discord.
- **Opciones…**: abre las opciones (ver abajo).
- **Copiar Discord**, **Reiniciar datos** (pide un segundo clic; los clanes no se borran), **Ordenar** (kills o KD, también para el HUD) y **Listo**.

**Opciones…**
- **Tecla del menú** y **Tecla del HUD**: clic y pulsa la tecla nueva (Esc cancela, Retroceso la deja sin tecla).
- **Idioma**: Auto → Español → English.
- **Color de clan en Tab y jugador: Activado / Desactivado**: el color del clan en la lista de Tab y en el nombre encima de cada jugador.

Todo lo del menú también se puede hacer con comandos. En la versión 26.3 por ahora solo hay comandos.

## 4. Uso con comandos

```
/clanes crear Lobos rojo
/clanes crear "Los Tigres" #3FA7FF
/clanes agregar Lobos Pepe123 Juan_MX Ana_99
/clanes agregar "Los Tigres" Leo Kira_GG
```

- **Nombres de clan con espacios, tildes o símbolos van entre comillas** cuando después vienen más cosas: `/clanes agregar "Los Tigres" Leo`. Cuando el clan es lo último del comando no hacen falta: `/clanes borrar Los Tigres`. El autocompletado (Tab) ya pone las comillas.
- **Tecla K**: cambia el HUD (clanes + lobby → apagado → solo clanes → solo lobby). **Tecla J**: abre el menú. Las dos se cambian desde el menú o en Opciones → Controles → Asignación de teclas (categoría ClanStats).
- El HUD va arriba a la izquierda (el scoreboard de CubeCraft está a la derecha). Se oculta con F1.
- Si te tapa mucho: `/clanes hud escala 0.75` o `/clanes hud top 2`.

| Comando | Qué hace |
|---|---|
| `/clanes menu` | Abre el menú visual. |
| `/clanes crear <clan> [color]` | Crea un clan. Color: nombre (`rojo`, `azul_oscuro`, `celeste`, `lavanda`...) o código (`#FF8800`). |
| `/clanes agregar <clan> <jugadores...>` | Agrega uno o varios por nombre (separados por espacio o coma), siempre con su UUID. Si estaba en otro clan, lo mueve. Si un nombre no existe, no se guarda. |
| `/clanes quitar <jugador>` | Lo saca de su clan. |
| `/clanes color <clan> <color>` | Cambia el color (nombre en español o inglés, o `#RRGGBB`). |
| `/clanes renombrar <clan> <nuevo nombre>` | Cambia el nombre del clan. |
| `/clanes borrar <clan>` | Borra el clan (sus kills no se pierden). |
| `/clanes lista` | Clanes y miembros: blanco = en la sala, gris = desconectado. |
| `/clanes uuid <jugador>` | Muestra el UUID guardado de un jugador. |
| `/clanes top [kills\|kd]` | Ranking de cada clan + totales + enfrentamientos (solo kills entre clanes). |
| `/clanes lobby [kills\|kd]` | Ranking de toda la lobby. |
| `/clanes stats <jugador>` | Kills/muertes en la lobby, entre clanes, mejor racha y fuego amigo. |
| `/clanes copiar` | Copia el resumen para Discord (máx. 2000 caracteres) y lo guarda en `resumen-discord.txt`. |
| `/clanes copiar completo` | Copia el resumen completo, sin límite de largo (`resumen.txt`). |
| `/clanes idioma auto\|es\|en` | Idioma del mod (auto = el del juego). |
| `/clanes activar` / `/clanes desactivar` | Activa o pausa el mod (pausado no cuenta kills ni muestra HUD ni colores; los datos se guardan). |
| `/clanes colores on\|off` | Color de clan en el Tab y encima de los jugadores. |
| `/clanes desconectados ver\|ocultar` | Muestra u oculta a los desconectados en el menú y el HUD (sus stats cuentan igual). |
| `/clanes reset` | Pone todo en cero (pide confirmar). La sesión anterior se guarda en `historial/`. Los clanes se mantienen. |
| `/clanes deshacer` | Quita la última kill registrada. |
| `/clanes kill <asesino> <víctima>` / `/clanes muerte <víctima>` | Agrega una kill o muerte a mano. |
| `/clanes hud off\|clanes\|lobby\|ambos` | Modo del HUD. |
| `/clanes hud top <n>` / `toplobby <n>` | Cuántos jugadores mostrar por clan / en la lobby. |
| `/clanes hud escala <0.3-3>` / `posicion <x> <y>` / `lado izquierda\|derecha` / `orden kills\|kd` / `fondo on\|off` | Apariencia del HUD. |
| `/clanes fuegoamigo separar\|contar` | Si las kills entre miembros del **mismo** clan cuentan o no (por defecto no cuentan y salen como "FF"). |
| `/clanes debug on\|off` | Muestra en el HUD las últimas kills detectadas y guarda los mensajes del chat en `debug.log`. |
| `/clanes probar <mensaje>` | Te dice si ese mensaje se detecta como kill y quién mató a quién. |
| `/clanes patron agregar <plantilla>` | Le enseña un mensaje nuevo. Ej.: `/clanes patron agregar {K} convirtió a {V} en polvo` |
| `/clanes heuristica on\|off` | Detección automática de mensajes desconocidos (ver abajo). |

Los mismos comandos existen en inglés con `/clans` (`/clans create`, `/clans add`, `/clans list`, `/clans language`, `/clans enable`, `/clans colors`, `/clans offline`...). Los dos funcionan siempre; los mensajes salen en el idioma elegido.

## 5. Idioma

- Por defecto (**Auto**) el mod usa **español** si el juego está en cualquier variante de español y **inglés** si está en cualquier otro idioma. Si cambias el idioma del juego, el mod cambia solo.
- Para fijarlo: **Opciones… → Idioma** en el menú (Auto → Español → English) o `/clanes idioma es` / `/clans language en`.
- Cambia todo: menú, HUD, mensajes del chat, **resumen de Discord** y resumen completo.

## 6. Colores

32 colores con nombre (en español o inglés, con o sin tildes):

`rojo` · `rojo_oscuro` · `carmesi` · `coral` · `naranja` · `dorado` · `amarillo` · `lima` · `verde` · `verde_oscuro` · `esmeralda` · `oliva` · `menta` · `turquesa` · `aqua` · `cian` · `celeste` · `azul` · `azul_rey` · `azul_oscuro` · `lavanda` · `violeta` · `morado` · `magenta` · `rosa` · `rosa_claro` · `marron` · `beige` · `blanco` · `gris` · `gris_oscuro` · `negro`

O cualquier otro con código hexadecimal: `#FF8800`, `#3FA7FF`, `#f80`... El color del clan se ve en el menú, el HUD, los mensajes del chat, en la **lista de Tab** y en el **nombre encima de cada jugador**. En esos dos lugares solo cambia el color del nombre: el rango y lo que agregan el servidor u otros mods se quedan igual. Se apaga en **Opciones… → Color de clan en Tab y jugador** o con `/clanes colores off`.

**Mods de tiers (TierTagger, QTiers):** funcionan junto con ClanStats. Cada mod agrega lo suyo al mismo nombre, así que se ven el tier y el nombre con el color del clan a la vez, en el Tab y encima del jugador.

## 7. Jugadores por UUID

- Solo se agregan **por nombre** y siempre quedan con el **UUID** de su cuenta.
- Si el jugador **está en la sala**, el UUID sale de la lista de Tab al instante.
- Si **no está**, se busca en la API de Mojang. Mientras busca, el menú dice "Buscando a ... en Mojang". Si la cuenta existe, se agrega; **si no existe, no se guarda** y te avisa (así no quedan nombres mal escritos). Si no hay conexión con Mojang, tampoco se guarda: prueba otra vez.
- Cada vez que un miembro aparece en el Tab con **otro nombre pero el mismo UUID**, el mod actualiza el nombre y te avisa: "Juan_MX ahora se llama Juan_2 — sigue en Lobos".
- Las kills se guardan con el UUID de cada jugador, así que las que hizo con el nombre viejo y con el nuevo se suman juntas.
- La cabeza de un miembro que no está en la sala se dibuja con su skin real, descargada con su UUID (la primera vez tarda un momento y mientras tanto se ve la skin por defecto).

## 8. Resumen para Discord

**Copiar Discord** (en el menú) o `/clanes copiar` deja en el portapapeles un mensaje así (en el idioma elegido); en Discord solo pegas con **Ctrl+V**:

````
**⚔️ ClanStats · FFA** — 13/11/2026 21:40
-# Sesión desde 13/11 19:05 · muertes: 152 · jugadores: 37

**🏆 Clanes** (solo kills entre miembros de clanes)
```
#   Clan          K     M     KD
1   Lobos        12     5   2.40
2   Los Tigres    5    12   0.42
```
**Enfrentamientos:** Lobos **12-5** Los Tigres

**👥 Miembros** (kills/muertes · KD)
```
Lobos
  1 Juan_MX               7/2   3.50
  2 Pepe123               5/3   1.67
```

**🌍 Lobby** (top 10 de 37, todas las kills)
```
  1 Juan_MX           [Lobos]      15/4   3.75
```
````

Las tablas van en bloques de código para que se vean alineadas. Nunca pasa de **2000 caracteres** (el límite de un mensaje de Discord): si hay muchos jugadores, muestra menos por lista. El resumen sin recortar está en `/clanes copiar completo` y en `historial/` al reiniciar.

## 9. Cómo cuenta

- **Clanes ("solo entre ellos")**: una kill cuenta para los clanes solo si el asesino **y** la víctima están en clanes **distintos**. Si alguien de un clan mata a un random (o al revés), no cuenta para el clan, pero sí para la lobby.
- **Fuego amigo**: kills entre miembros del mismo clan; por defecto se muestran aparte ("FF") y no suman. Con `/clanes fuegoamigo contar` sí suman.
- **Lobby** y **Jugadores en sala**: todas las kills y muertes que aparecen en tu chat, incluidas las muertes sin asesino (vacío, caída).
- **KD** = kills ÷ muertes (si no tiene muertes, KD = kills).
- Todo se recalcula desde el registro de kills: si agregas a alguien a un clan tarde, sus kills anteriores se cuentan igual.
- La sesión se guarda sola y sobrevive a reinicios del juego hasta que la reinicies (botón del menú o `/clanes reset`).

## 10. Cómo detecta las kills

1. Ya conoce las **87 kill messages de CubeCraft** (incluidas las de Plus, aunque traigan emotes en el nombre), las de Minecraft vanilla y patrones generales en inglés y español ("X fue asesinado por Y", "Y mató a X", "X was defeated by Y").
2. Revisa que los nombres estén en la **lista de jugadores (Tab)**, y **ignora el chat de jugadores**: nadie puede inventar kills escribiendo "X was slain by Y".
3. **Heurística**: si llega un mensaje que no conoce con exactamente dos jugadores conectados, lo cuenta como kill (sale con "(?)" en modo debug). Se apaga con `/clanes heuristica off`.

**Si juegas CubeCraft en español** u otro idioma, algunos mensajes pueden no reconocerse. Para revisarlo:

1. `/clanes debug on` y juega un rato: el HUD muestra las últimas kills detectadas.
2. Si una kill no aparece, búscala en `config/clanstats/debug.log` (o cópiala del chat) y pruébala: `/clanes probar <mensaje>`.
3. Enséñale el formato: `/clanes patron agregar {V} fue destruido por {K}` (`{V}` = víctima, `{K}` = asesino, `*` = cualquier texto).

**Límite importante:** solo cuenta lo que llega a **tu** chat. Si desactivas el chat, o si CubeCraft solo te muestra algunas kills (por ejemplo, estando en party), esas no se pueden contar.

## 11. Archivos (`config/clanstats/` dentro de la carpeta del juego)

| Archivo | Contenido |
|---|---|
| `config.json` | Clanes, miembros (nombre + UUID), colores, idioma, opciones del HUD y plantillas propias (se puede editar a mano con el juego cerrado). |
| `sesion.json` | Registro de kills de la sesión actual. |
| `historial/` | Sesiones anteriores (texto + JSON) guardadas al reiniciar. |
| `resumen-discord.txt` | Último resumen para Discord. |
| `resumen.txt` | Último resumen completo. |
| `config.v1-respaldo.json` | Copia de la configuración de la versión 1.0 (solo si actualizaste desde ahí). |
| `debug.log` | Mensajes del chat analizados (solo con debug activo). |

Al actualizar desde la 1.1.0 no se pierde nada: los clanes, miembros, colores y la sesión se leen igual.

## 12. ¿Está permitido en CubeCraft?

El mod solo **lee el chat y dibuja en tu pantalla** (el HUD, el menú y el color de los nombres en tu propio Tab): no manda paquetes, no automatiza nada y no da ventaja en el combate, como los mods de HUD o contadores de kills. Aun así, revisa las reglas de mods de CubeCraft antes de usarlo; la responsabilidad es de cada jugador.

## 13. Estado

- La versión 1.1.0 se probó dentro del juego en Lunar Client 1.21.11.
- La versión 1.2.0 ya se usó dentro del juego en Lunar.
- Las novedades de la 1.3.0 (Opciones, activar/desactivar, ocultar desconectados, color encima de los jugadores y compatibilidad con TierTagger y QTiers) están probadas con objetos de Minecraft simulados: el nombre se pinta sin tocar el tier, el rango ni los íconos de otros mods.
- El **color encima de los jugadores** depende de cómo muestre los nombres el servidor: si el servidor dibuja los nombres con su propio sistema en vez del nombre normal de Minecraft, ahí no se verá el color (en el Tab sí). Si la cabeza de un desconectado no carga, se ve la de por defecto.

## 14. Estructura del proyecto

```
common/                      código compartido por todas las versiones
  core/                      Java puro: detección de kills, estadísticas, UUID, colores, textos, HUD, resúmenes, guardado
  mod/                       comandos /clanes y /clans, eventos de Fabric, mensajes de chat
  resources/assets/clanstats/texts/   textos en español (es.json) e inglés (en.json)
versions/mc1.21.11/          entrada para 1.21.11 + menú visual (ClanStatsScreen, ClanStatsOptionsScreen) + color en el Tab y en los nombres — Java 21
  src/main/java/.../mixin/   color de clan en la lista de Tab y encima de los jugadores (con @ModifyReturnValue, compatible con otros mods)
  src/modmenuApi/            interfaces de Mod Menu solo para compilar (no van en el .jar)
versions/mc26.3/             entrada para 26.3 (sin menú por ahora) — Java 25
.github/workflows/build.yml  compila las dos versiones en GitHub
```

Para otra versión 26.x: copia `versions/mc26.3`, cambia `minecraft_version` y `fabric_api_version` en `gradle.properties` y el rango `"minecraft"` en `src/main/resources/fabric.mod.json`.

---

## English

ClanStats FFA is a **client-side** Fabric mod (Minecraft Java 1.21.11 and 26.3) for CubeCraft FFA. It reads kill messages from your chat and keeps the score on your PC — nothing is sent to the Minecraft server.

- Group specific players into **clans** (names can have spaces and symbols). Kills and deaths count **only between members of different clans**; there's also a **whole-lobby** count.
- **Menu** (key **J**, `/clans menu` or Mod Menu): clans on the left; on the right the selected clan's members with their heads, online/offline status and a **Total** row; **Add from the list** (lobby players without a clan) and **Players in lobby** (everyone with their total kills/deaths, sorted by kills or KD).
- **Add by name**: players are always saved with their account **UUID**. If they aren't in the lobby, the UUID is looked up on Mojang; names that don't exist are **not saved**.
- **32 colors** or any hex code (`#FF8800`), shown in the menu, HUD, chat, the **Tab list** and the **name above each player** (only the name color changes, no tag). Works together with tier mods like **TierTagger** and **QTiers**: you see the tier and the clan color at the same time.
- **Mod: On/Off** button (paused it doesn't count kills or show the HUD or colors; data is kept) and **Offline: shown/hidden** (hides offline members from the lists; their stats still count). **Options…** has the menu and HUD keys, the language and the clan color toggle.
- **Spanish or English**: follows the game language by default (**Auto**); change it with the menu's **Language** button or `/clans language auto|es|en`. The Discord summary follows the chosen language too.
- Commands: `/clans create <clan> [color]`, `/clans add <clan> <players...>`, `/clans remove <player>`, `/clans color <clan> <color>`, `/clans rename <clan> <new name>`, `/clans delete <clan>`, `/clans list`, `/clans top [kills|kd]`, `/clans lobby [kills|kd]`, `/clans stats <player>`, `/clans copy [full]`, `/clans reset`, `/clans hud ...`, `/clans enable|disable`, `/clans colors on|off`, `/clans offline show|hide`, `/clans friendlyfire separate|count`, `/clans debug on|off`, `/clans test <message>`, `/clans pattern add <template>`. Clan names with spaces go in **quotes** when more arguments follow: `/clans add "The Pros" Steve`. The Spanish commands (`/clanes ...`) work too.
- Install on Lunar Client: pick **1.21.11 + Fabric**, open the Mods tab and drop the `.jar` there (Lunar already includes Fabric API). Build it with GitHub Actions (see section 1) or `./gradlew build` inside `versions/mc1.21.11` with JDK 25.

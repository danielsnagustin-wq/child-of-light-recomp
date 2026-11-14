# FASE 4 — Controles reales del título (Xbox 360): investigación en internet

- **Fecha:** 2026-11-13
- **Alcance:** solo lectura. Ningún archivo existente fue modificado; este archivo es nuevo.
- **Objetivo:** determinar qué botón del mando de Xbox 360 usa realmente el juego, para limpiar el overlay táctil del port (hoy: LT, LB, RB, RT, D-pad, stick izquierdo, stick derecho, A, B, X, Y, `<<` `>>` y engranaje de ajustes).
- **Contexto:** la v1.2 ya arrancó en el teléfono real (Helio G99 / Mali-G57 MC2): logo la editora, título y Loading funcionan.

## 0. Método y regla de traducción PlayStation → Xbox

La mayoría de las fuentes describen los controles con botones de PlayStation (el juego salió en PS3/PS4/360/One el mismo día). La equivalencia posicional es estándar y se usa en todo el documento:

| PlayStation | Xbox 360 |
|---|---|
| ✕ (abajo) | **A** |
| □ (izquierda) | **X** |
| ○ (derecha) | **B** |
| △ (arriba) | **Y** |
| L2 | **LT** |
| R2 | **RT** |
| L1 | **LB** |
| R1 | **RB** |
| Stick izquierdo / derecho | Stick izquierdo / derecho |

**Advertencia importante:** ninguna fuente consultada publica una tabla de controles **exclusiva de Xbox 360**. La fuente con tabla por plataforma (GamePressure) cubre PC, **Xbox One** y PS4. Xbox 360 y Xbox One son el mismo juego y el mismo motor (el motor); se asume el mismo esquema en 360, pero eso es una **inferencia, no un dato verificado directamente**. Se marca como tal donde aplica.

**StrategyWiki:** buscado explícitamente — **no existe página del título en StrategyWiki** (la búsqueda solo devolvió otros juegos). No se pudo usar como fuente.

## 1. Fuentes consultadas

| # | Fuente | URL | Qué aporta |
|---|---|---|---|
| F1 | **Manual oficial** del título (PDF de la editora, servido en Steam) | https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/256290/manuals/ChildOfLight-en.pdf?t=1694554365 | Lista de acciones de Aurora e Igniculus (Move, Jump, Fly, Dash, Interact, Glow), habilidades de Igniculus (curar, cegar, ralentizar en combate), comandos de combate (Act, Potions, Swap, Flee), menú de pausa (Inventory, Oculi, Skills, Party, Map, Confessions). Los iconos de botón del PDF se pierden en la extracción de texto: el manual **no nombra botones con letras** en el texto extraíble, solo las acciones. |
| F2 | **GamePressure — el título: Controls** (tabla PC / Xbox One / PS4) | https://www.gamepressure.com/child-of-light/controls/zfa149 | Tabla de acciones por plataforma. Los botones son **imágenes**; se descargaron y se identificaron visualmente (ver §2). Es la única fuente con el mapa botón→acción por plataforma. |
| F3 | **GameFAQs — Review Xbox 360** ("The best fairy tale since Snow White!") | https://Gamefaqs.gamespot.com/xbox360/730866-child-of-light/reviews/162392 | Describe controles con nomenclatura PS (✕ saltar, □ interactuar, L2 brillo, 2º jugador con R2), combate (✕ confirmar, ○ volver) y la frase clave: "the controls utilize very few of the buttons on the controller". |
| F4 | **GameFAQs — Review PS3/Stadia** ("Engrossing, Beautiful, Outstanding") | https://gamefaqs.gamespot.com/ps3/730868-child-of-light/reviews/157966 | "You really only ever use three buttons (X, square, and L2) and the two sticks in the whole game." ✕ = saltar, volar, subir/bajar de plano, **dash**; □ = acción/hablar/entrar; L2 = brillo de Igniculus. |
| F5 | **Eggplante — el título Review** | https://www.eggplante.com/2014/07/10/child-light-review/ | "Igniculus can be moved with the right stick on any platform… only be moved with the right stick on Xbox 360, Xbox One and PS3". 2º jugador controla a Igniculus en local en todas las versiones salvo Vita. |
| F6 | **GameFAQs — Guide and Walkthrough Xbox 360/One, por CanWizard** | https://gamefaqs.gamespot.com/xbox360/730866-child-of-light/faqs/69461 | No toca controles ("the manual will probably help you with controls and button layouts, which is a subject I will not be touching"). Confirma el menú de pausa como guía de objetivo ("open up the PAUSE MENU"). |
| F7 | **Video Games Blogger — Confessions Locations Guide** | https://www.videogamesblogger.com/2014/05/02/child-of-light-confessions-locations-guide.htm | "To read the Confessions, press the **start/pause button/key** to go to the Pause menu". Verifica **Start = menú de pausa**. |
| F8 | **TrueAchievements — Story walkthrough Act 3** | https://www.trueachievements.com/game/Child-of-Light/walkthrough/5 | "select **Flee from the Action Wheel**"; enviar Oculi a un amigo: abrir menú → Oculi → pulsar el botón de regalo (icono no transcrito en el texto). |
| F9 | **dtgre — How to donate an Oculi to a friend** | https://www.dtgre.com/2014/06/child-of-light-how-to-donate-oculi-to.html?m=0 | En PS3, regalar Oculi = **□ (Square)** sobre el Oculi → lista de amigos → elegir cantidad con izquierda/derecha → ✕ confirmar. Es decir, en Xbox sería **X** (no Y ni ningún botón extra). |
| F10 | **Quacksouls — The Girl and the Firefly (walkthrough)** | https://quacksouls.github.io/aurora/girl-and-firefly/ | "Surprise Strike": poner a Igniculus sobre la cabeza del enemigo y **mantener el brillo**, acercar a Aurora al torso y soltar → ataque por sorpresa. Curar: Igniculus sobre Aurora + mantener brillo. |
| F11 | **Outcyders — el título Review** | https://www.outcyders.net/review/child-of-light-review | Combate: atacar (físico o mágico con MP), **defender**, hechizos buff/debuff, pociones, cambiar personaje, **huir**; todo como opciones de menú, no como botones dedicados. |
| F12 | **Wikipedia — el título** | https://en.wikipedia.org/wiki/Child_of_Light | "The player can freely move Igniculus to open chests that Aurora cannot reach, as well as shine his light on an enemy to slow it down or on an ally to heal them." |

## 2. El mapa botón → acción, verificado con las imágenes de GamePressure (F2)

La tabla de F2 muestra los botones como iconos PNG. Se descargaron de `gamepressure.com` y se identificaron visualmente uno por uno:

**Xbox One — Aurora** (3 acciones, sin iconos extraíbles en esa sección, pero simétricas a PS4):
- Movement / push objects → stick izquierdo
- Grab objects / interaction → **X** (equivalente de □ en PS4, verificado abajo)
- Fly / jump → **A** (equivalente de ✕ en PS4, verificado abajo)

**Xbox One — Igniculus** (iconos identificados visualmente):
- Movement → **stick derecho** (icono "R" con stick)
- Grab objects / interaction → **RT** (icono "RT")
- Light up → **LT** (icono "LT")

**PS4 — Aurora** (control cruzado, confirma la equivalencia):
- Movement / push objects → stick izquierdo
- Grab objects / interaction → **□ (Square)**
- Fly / jump → **✕**

**PS4 — Igniculus** (control cruzado):
- Movement → **stick derecho** (icono "R")
- Grab objects / interaction → **□ (Square)**
- Light up → **L2**

**Diferencia entre plataformas a tener en cuenta:** en PS4, la interacción de Igniculus es el **mismo botón** que la de Aurora (□); en Xbox One, GamePressure la asigna a **RT**, un botón distinto del de Aurora (X). No se encontró ninguna fuente que explique o confirme esta diferencia para Xbox 360. Para 360 se adopta el mapa de Xbox One (misma familia Xbox), marcado como **inferido de Xbox One, no verificado en 360**.

**Acciones que aparecen en el manual (F1) y en F2 pero NO en la tabla de botones de F2:** "Accept an action" y "Decline an action" solo se listan en PC (teclas). En consola, confirmar/cancelar son A/B por la descripción de F3 (ver §4).

## 3. Exploración

| Acción | Botón Xbox 360 | Verificación |
|---|---|---|
| Mover a Aurora (andar/correr, empujar objetos/cajas) | **Stick izquierdo** | F1 (Move), F2 (Movement/push objects), F3, F4 |
| Saltar | **A** | F3 (✕ = jump), F4 (✕ = jump) |
| Volar (tras obtener el don, ~1 h de juego; mantener para ascender, soltar para descender) | **A** (mantener/pulsar) | F1 (Fly), F2 (Fly/jump), F3 ("X being used to jump or to lift off from the ground once you get the flying ability"), F4 ("X is the main button, used to jump, fly, go up or down a plane") |
| Dash (carrera rápida) | **A** (mismo botón que saltar) | F1 lista "Dash" como acción de Aurora; F4: "X is the main button, used to jump, fly, go up or down a plane, **dashing**". **El gesto exacto (doble pulsación, mantener, etc.) NO está verificado en ninguna fuente consultada.** |
| Interactuar (hablar, abrir cofres normales, entrar por puertas, tirar de palancas) | **X** | F2 (Grab objects / interaction), F3 (□ = talk, enter doorways, interact), F4 (□ = "your action button… talking or going in and out of buildings") |
| Mover a Igniculus (flota libre por toda la pantalla, sin límites de terreno) | **Stick derecho** | F1 (Move), F2 (Movement), F3, F4, F5 ("only be moved with the right stick on Xbox 360, Xbox One and PS3") |
| Brillo de Igniculus — MANTENER (iluminar zonas oscuras, abrir contenedores especiales, activar interruptores de luz, cegar enemigos para pasar sin combatir, recoger deseos/orbes lejanos) | **LT** (mantener) | F1 (Glow; "blind dark creatures by glowing near them"), F2 (Light up, icono LT), F3 ("By holding down L2 Igniculus will shine his light to either open certain containers, activate some switches or blind enemies"), F4 ("Igniculus glows when you press L2") |
| Interactuar con Igniculus (abrir cofres fuera del alcance de Aurora, recoger objetos/stardust lejanos) | **RT** (Xbox One; en PS4 es □) | F1 (Interact, columna Igniculus), F2 (icono RT en Xbox One). **Verificado solo en Xbox One; en 360 inferido.** |
| Atacar fuera de combate | **No existe.** Los combates empiezan al tocar a un enemigo (o con "Surprise Strike", F10). No hay botón de ataque en exploración. | F1 ("A battle is triggered when Aurora touches a dark creature"), F10, F11 |
| Esquivar / rodar fuera de combate | **No existe.** No hay esquiva en exploración. "Dodge" es una estadística (probabilidad), no una acción (F1, Party Member Stats). | F1 |
| Abrir menú de pausa | **Start** | F6, F7 ("press the start/pause button/key to go to the Pause menu") |

## 4. Combate

El combate es por **menús**: cuando un personaje llega a la zona de Cast de la Timeline, el tiempo se pausa y se elige una acción en un menú (F1, "Casting on the Timeline"). **No hay botones dedicados de ataque, defensa ni huida**: todo son opciones del menú de batalla (F1 "Battle Commands", F8 "Action Wheel", F11).

| Acción | Botón Xbox 360 | Verificación |
|---|---|---|
| Navegar por el menú de batalla / elegir objetivo | **Stick izquierdo o D-pad** (ver §6, no verificado explícitamente) | F1 ("Select"), convención general |
| Confirmar acción / elegir Act, Potions, Swap, Flee | **A** | F3: "you use X to confirm it" (✕ = A); F1 ("Confirm") |
| Volver / cancelar selección | **B** | F3: "Circle to back out" (○ = B); F1 ("Back") |
| Atacar (físico o habilidad mágica) | Opción **Act** del menú → elegir habilidad → elegir objetivo. Sin botón dedicado. | F1 ("When you select «Act,» you will have access to all unlocked skills"), F11 |
| Defender | Opción de acción dentro del menú (defender evita la interrupción y acelera el siguiente avance en la Timeline). Sin botón dedicado. | F1 ("an action (an attack, defense, or spell) must be chosen"); F4-review Stadia: "you can instead choose to defend, which will prevent you from being interrupted" |
| Usar poción | Opción **Potions** del menú | F1 |
| Cambiar personaje en combate | Opción **Swap** del menú (2 en batalla, resto en reserva) | F1, F11 |
| Huir | Opción **Flee** del menú ("Action Wheel") | F1 ("Selecting «Flee» will allow you to flee the battle"), F8 |
| Igniculus en combate: mover | **Stick derecho** (igual que en exploración) | F3: "When in battle, Igniculus is controlled in exactly the same way"; F1 ("Slowing Down Foes during Battle") |
| Igniculus en combate: ralentizar enemigo (brillo sobre el enemigo) | **LT** (mantener) | F1 ("Igniculus can slow down a foe by glowing during a battle"), F3, F4-review Stadia ("When hovered over an enemy and glowing, he will slow their movement") |
| Igniculus en combate: curar aliado (brillo sobre el aliado) | **LT** (mantener) | F1 ("Igniculus can heal allies by glowing near them"), F3, F10 |
| Pausar durante el combate | **Start** | Convención; no verificado con fuente específica para combate (el manual solo describe el menú de pausa en general) |

## 5. Menús (pausa, inventario, Oculi, habilidades, grupo, mapa, confesiones)

El menú de pausa enlaza a Inventory, Oculi, Skills, Party, Map of Lemuria y Confessions (F1). La navegación es por menús con selección.

| Acción | Botón Xbox 360 | Verificación |
|---|---|---|
| Abrir / cerrar menú de pausa | **Start** | F7, F6 |
| Navegar por las opciones | **Stick izquierdo o D-pad** | No verificado con fuente explícita para el título; es la convención universal y la única entrada direccional del juego. Ver §6. |
| Confirmar / entrar | **A** | F3 (batalla; mismo botón de confirmación del juego). Para menús de pausa en general, **no hay fuente explícita** que lo nombre; se asume por coherencia con F1 ("Confirm") y F3. |
| Volver / cancelar / salir del menú | **B** | F3 (○ = back); F1 ("Back"). Misma salvedad que Confirmar. |
| Cambiar de pestaña/sección con LB/RB | **NO VERIFICADO.** Ninguna fuente menciona LB/RB para cambiar de pestaña en el menú de pausa ni en ningún otro sitio. | — |
| Regalar Oculi a un amigo (logro "Through the Looking Glass") | **X** (no Y): en el menú Oculi, sobre el Oculi, pulsar el botón de regalo; en PS3 es □ = X en Xbox. | F9 (PS3: "press square"), F8 ("press the Gift button"). Requiere uPlay y no es jugable offline; irrelevante para el overlay diario. |

## 6. Igniculus como 2º jugador (cooperativo local, mando 2)

| Acción (jugador 2) | Botón en el mando 2 (Xbox 360) | Verificación |
|---|---|---|
| Mover a Igniculus | **Stick izquierdo** del mando 2 | F3: "if connecting a second controller it can be used to control him with the left stick"; F5 (2º jugador controla a Igniculus en local en todas las versiones salvo Vita) |
| Brillo de Igniculus (glow) | **RT** del mando 2 (R2 en PS) | F3: "shine the light with R2". **Tensión sin resolver:** en 1P el brillo es LT/L2 (F2, F3, F4) y la interacción es RT/R2 (F2). F3 asigna el brillo del 2º jugador a R2, es decir, al botón que en 1P es "interactuar". Ninguna fuente aclara si en 2P el RT hace brillo e interactuar a la vez, o si el mapa cambia. El manual (F1) solo dice: "To activate Two Player mode, use the mouse or a second controller. The second player will have control of Igniculus", sin nombrar botones. |
| Interactuar con Igniculus (mando 2) | **No verificado** | Ninguna fuente lo nombra para el 2º jugador. |
| Mando 1 en 2P | Aurora: stick izquierdo + A + X, igual que en solitario. El stick derecho y LT/RT del mando 1 quedan libres (Igniculus ya no es suyo). | F3, F5 |

**Consecuencia para el port:** el port es de **un solo jugador en un teléfono**; el modo 2P no aplica al overlay. Lo único que importa del §6 es que confirma que Igniculus solo necesita **movimiento + brillo (+ interactuar)**: nada más.

## 7. Botones sin función: LB, RB, LT/RT (matiz), D-pad, L3/R3, Start, Back, Y

| Botón | Función exacta o "no se usa" | Veredicto |
|---|---|---|
| **LB** | **Sin función en ninguna fuente.** No aparece en F1 (acciones), F2 (tabla), F3/F4 (reviews) ni en ninguna búsqueda ("LB RB switch tabs" sin resultados). | No se usa (según toda la evidencia disponible). |
| **RB** | **Sin función en ninguna fuente.** Igual que LB. | No se usa (según toda la evidencia disponible). |
| **LT** | Brillo de Igniculus (mantener). Esencial. | Se usa — ver §3/§4. |
| **RT** | Interactuar con Igniculus (Xbox One, F2). En 2P, brillo según F3 (tensión sin resolver, §6). | Se usa — ver §3/§6. |
| **D-pad** | **Sin función verificada explícitamente en el título.** Ninguna fuente lo nombra para este juego. Por convención de la época y del género, navega menús; si mueve a Aurora en exploración es **no verificado**. | No verificado; probable solo menús. |
| **L3** (click stick izq.) | **Sin función en ninguna fuente.** | No se usa (según toda la evidencia disponible). |
| **R3** (click stick der.) | **Sin función en ninguna fuente.** | No se usa (según toda la evidencia disponible). |
| **Start** | Menú de pausa. | Se usa — ver §5. |
| **Back** | **Sin función en ninguna fuente.** Ninguna fuente lo nombra para el título. (En 360, Back suele no hacer nada en este juego; eso es conjetura, no dato.) | No verificado; casi seguro sin uso. |
| **Y** | **Sin función en ninguna fuente.** No aparece en ninguna tabla ni descripción. F2 lista solo 3 acciones de Aurora (mover, interactuar, saltar/volar) y 3 de Igniculus (mover, interactuar, brillo): Y no es ninguna. | No se usa (según toda la evidencia disponible). |
| **Botón Xbox Guide** | Abre la guía de Xbox (sistema), no es del juego. | Fuera del juego; no va en el overlay. |

**¿Existe algún botón del mando de Xbox que el juego NO use nunca?** Sí, según toda la evidencia reunida: **Y, LB, RB, L3, R3 y (casi seguro) Back** no tienen ninguna función asignada en el juego. F4 lo resume: "You really only ever use three buttons (X, square, and L2) and the two sticks in the whole game" (en Xbox: **A, X y LT** + los dos sticks), más Start para la pausa y A/B para confirmar/volver en menús. F3 coincide: "the controls utilize very few of the buttons on the controller".

## 8. Tabla definitiva: botón → función en el juego original → ¿necesario en el overlay?

Leyenda de "necesario": **SÍ** = el juego es incompletable o muy incómodo sin él; **NO** = quitarlo no impide jugar.

| Botón del overlay actual | Función en el juego original (Xbox 360) | ¿Necesario en el overlay? |
|---|---|---|
| **Stick izquierdo** | Exploración: mover a Aurora, empujar objetos. Menús/combate: navegar opciones. | **SÍ** — movimiento principal; sin él no se juega. |
| **Stick derecho** | Exploración y combate: mover a Igniculus (libre por toda la pantalla). | **SÍ** — sin él no hay cofres lejanos, ni interruptores de luz, ni curación/ralentí en combate. |
| **A** | Exploración: saltar, volar (mantener), dash (mismo botón). Menús/combate: confirmar. | **SÍ** — el botón más usado del juego. |
| **B** | Menús/combate: volver/cancelar. (En exploración no tiene función descrita.) | **SÍ** — sin él no se puede salir de un submenú ni cancelar una selección de combate. |
| **X** | Exploración: interactuar (hablar, cofres, puertas, palancas). Menús: regalar Oculi (botón "Gift"). | **SÍ** — sin él no se abren cofres ni se habla ni se entra a sitios. |
| **Y** | **Sin función verificada en ninguna fuente** (no aparece en F1, F2, F3 ni F4). | **NO** — quitar. Es el candidato nº 1 a eliminar. |
| **LB** | **Sin función verificada en ninguna fuente.** | **NO** — quitar. |
| **RB** | **Sin función verificada en ninguna fuente.** | **NO** — quitar. |
| **LT** | Mantener: brillo de Igniculus (iluminar, abrir contenedores, interruptores, cegar enemigos, curar aliados, ralentizar enemigos en combate). | **SÍ** — esencial; es la mitad del juego (puzles + combate). Debe ser **mantener**, no pulsación simple. |
| **RT** | Interactuar con Igniculus (Xbox One, F2): abrir cofres y recoger objetos fuera del alcance de Aurora. En 2P, brillo (F3, sin resolver). | **SÍ, con reserva** — mantener por ahora: es la única vía de interactuar a distancia con Igniculus. Si al probar en el teléfono RT no hace nada en 360 (el dato es de Xbox One), entonces pasa a NO. |
| **D-pad** | **Sin función verificada explícitamente en el título.** Por convención: navegar menús. En exploración, no verificado si mueve a Aurora. | **SÍ, con reserva** — mantener por navegación de menús/combate (alternativa discreta al stick analógico). Es el más prescindible de los "SÍ": si falta espacio, el stick izquierdo ya navega menús. |
| **Start** | Menú de pausa (Inventario, Oculi, Habilidades, Grupo, Mapa, Confesiones). | **SÍ** — sin él no hay inventario ni habilidades ni guardado de Oculi. Puede ser un botón pequeño, pero debe existir. |
| **Back** | **Sin función verificada en ninguna fuente.** | **NO** — quitar (B ya hace "volver" en menús). |
| **Engranaje (ajustes)** | No es un botón del juego: abre los ajustes del propio overlay. | **SÍ** — propio del port; no es del juego pero el overlay lo necesita (escala/opacidad). Puede ir pequeño o en el menú de pausa del port, pero no quitar la función. |
| **`<<` `>>`** | **No verificado / no encontrado.** En el código del overlay del repo (`tools/rexauto/android/app/src/main/java/com/rexauto/port/gamepad/PadGeometry.java`) **no existen botones `<<` `>>`**: solo hay sticks, D-pad, A/B/X/Y, LB/RB/LT/RT, Back, Start, engranaje (ID_MENU) y la pestaña de restaurar (ID_TAB, solo en modo oculto). No se pudo determinar a qué corresponden en la APK v1.2 ni qué botón de Xbox emulan. | **SIN DICTAMEN** — punto abierto para el coordinador: verificar en la APK qué hacen hoy antes de decidir. Si son "cambiar de pestaña" o "rebobinar", no corresponden a ningún botón del juego original. |

### Overlay mínimo resultante (propuesta)

**SÍ (10):** stick izquierdo, stick derecho, A, B, X, LT, RT, D-pad (reservado), Start, engranaje.
**NO (5):** Y, LB, RB, Back, y `<<` `>>` pendientes de verificar en la APK.
**No existen en el overlay actual y tampoco hacen falta:** L3/R3 (el código del overlay ni siquiera implementa click de sticks; el juego no los usa).

Eso deja el overlay en **los 2 sticks + 3 botones de acción (A/B/X) + 2 gatillos (LT/RT) + D-pad + Start + engranaje**, que es exactamente el conjunto que las fuentes describen como "todo el juego" (F4: tres botones y dos sticks; + Start/pausa + B/volver).

## 9. Cosas que NO se pudieron verificar (declarado explícitamente)

1. **Tabla de controles exclusiva de Xbox 360:** no existe en las fuentes encontradas. Todo lo de 360 se infiere de Xbox One (F2) + descripciones PS traducidas (F3/F4). El mapa RT=interactuar de Igniculus es de Xbox One; en 360 podría ser distinto (en PS4 es □/X). **Verificar en el teléfono con la v1.2/1.3.**
2. **Gesto exacto del Dash:** se sabe que es el botón A (F4), pero no si es doble pulsación, mantener, o pulsar en carrera. No verificado.
3. **D-pad en exploración:** ninguna fuente dice si mueve a Aurora o solo navega menús. No verificado.
4. **Confirmar (A) / Volver (B) en menús de pausa:** verificado explícitamente solo para combate (F3). Para menús de pausa es coherente pero no citado. No verificado directamente.
5. **Back:** ninguna fuente le asigna función; "no se usa" es inferencia por ausencia, no dato positivo.
6. **2º jugador, brillo en RT vs LT:** F3 (R2 = brillo) contradice en apariencia a F2 (RT = interactuar, LT = brillo en 1P). Sin resolver; irrelevante para el port de 1 jugador.
7. **Botones `<<` `>>` del overlay:** no están en el código del repo; su función actual en la APK no se pudo verificar desde aquí.
8. **L3/R3:** "no se usan" es inferencia por ausencia en todas las fuentes, no dato positivo.
9. **Y/LB/RB:** lo mismo que (8): ninguna fuente les asigna función; la frase de F4 ("only three buttons and the two sticks") es la evidencia más fuerte de que no se usan.

## 10. Referencia al overlay actual (solo lectura, repo)

- `tools/rexauto/android/app/src/main/java/com/rexauto/port/gamepad/PadGeometry.java` — IDs de controles: `ID_STICK_L/R`, `ID_DPAD`, `ID_A/B/X/Y`, `ID_LB/RB/LT/RT`, `ID_BACK`, `ID_START`, `ID_MENU` (engranaje), `ID_TAB` (pestaña de restaurar en modo oculto). No hay `<<` `>>`.
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/gamepad/VirtualPadView.java` — LT/RT son gatillos analógicos con rampa (`startTrigger`); LB/RB son pulsación simple; los sticks no tienen click (sin L3/R3); el engranaje abre `PadSettingsDialog`.
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/gamepad/PadInputBridge.java` — los botones espejan el orden SDL3 `SDL_GAMEPAD_BUTTON_*` (A=0, B=1, X=2, Y=3, Back=4, Start=6, L3=7, R3=8, LB=9, RB=10, D-pad=11–14).

## 11. Fuentes (URLs completas)

- F1 Manual oficial (la editora / Steam): https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/256290/manuals/ChildOfLight-en.pdf?t=1694554365
- F2 GamePressure Controls: https://www.gamepressure.com/child-of-light/controls/zfa149
- F3 GameFAQs review Xbox 360: https://Gamefaqs.gamespot.com/xbox360/730866-child-of-light/reviews/162392
- F4 GameFAQs review PS3/Stadia: https://gamefaqs.gamespot.com/ps3/730868-child-of-light/reviews/157966
- F5 Eggplante review: https://www.eggplante.com/2014/07/10/child-light-review/
- F6 GameFAQs walkthrough (CanWizard): https://gamefaqs.gamespot.com/xbox360/730866-child-of-light/faqs/69461
- F7 Video Games Blogger Confessions: https://www.videogamesblogger.com/2014/05/02/child-of-light-confessions-locations-guide.htm
- F8 TrueAchievements walkthrough Act 3: https://www.trueachievements.com/game/Child-of-Light/walkthrough/5
- F9 dtgre Oculi a un amigo: https://www.dtgre.com/2014/06/child-of-light-how-to-donate-oculi-to.html?m=0
- F10 Quacksouls walkthrough: https://quacksouls.github.io/aurora/girl-and-firefly/
- F11 Outcyders review: https://www.outcyders.net/review/child-of-light-review
- F12 Wikipedia: https://en.wikipedia.org/wiki/Child_of_Light
- StrategyWiki: sin página del título (búsqueda sin resultados para este juego).

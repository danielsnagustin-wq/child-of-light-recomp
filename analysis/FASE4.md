# FASE 4 — v1.3: controles verificados, horizontal, aspecto, español y port oficial

Fecha: 2026-11-13. Teléfono de referencia: Helio G99 / Mali-G57 MC2.
Base: v1.2 (HEAD 3435f60, run CI verde, juego arranca en el teléfono).

La v1.2 arrancó en el teléfono real: logo de la editora, pantalla de título,
"Loading". El usuario reportó 3 bugs + 1 petición de diseño. Esta fase los
arregla todos y descarta la home del launcher (dirección "port oficial").

Informes de investigación (solo lectura, previos a la implementación):
- `analysis/FASE4-controles-internet.md` — controles del original
- `analysis/FASE4-locale-oficial.md` — locale de la consola emulada
- `analysis/FASE4-pantalla.md` — orientación + viewport (bugs A y B)

---

## 1. Controles — qué se quitó y por qué

Verificado contra el original (manual oficial de la editora, GamePressure,
GameFAQs, TrueAchievements): el juego usa **tres botones y dos sticks**
(A, X, LT + stick izquierdo + stick derecho) más Start/B para menús.
No hay botón de ataque ni de esquiva fuera de combate: el combate es por
menús (Actuar / Pociones / Cambiar / Huir) con A confirmar / B atrás.

| Botón | Función en el original (360) | En el overlay v1.3 |
|---|---|---|
| Stick izquierdo | Mover a Aurora, empujar objetos, menús | **SÍ** |
| Stick derecho | Mover a Igniculus | **SÍ** |
| A | Saltar / volar (mantener), dash, confirmar | **SÍ** |
| B | Atrás / cancelar | **SÍ** |
| X | Interactuar (cofres, puertas, palancas, dar oculi) | **SÍ** |
| Y | Ninguna en ninguna fuente | **NO — quitado** |
| LB | Ninguna en ninguna fuente | **NO — quitado** |
| RB | Ninguna en ninguna fuente | **NO — quitado** |
| LT | Igniculus: brillar/mantener (luz, cofres especiales, interruptores, cegar enemigos, curar aliados, ralentizar en combate) | **SÍ** |
| RT | Igniculus: interactuar (verificado solo en Xbox One; confirmar en el teléfono para 360) | **SÍ (con reserva)** |
| D-pad | Sin función explícita verificada; navega menús por convención (el stick ya navega menús) | **SÍ (con reserva: el más prescindible de los "sí")** |
| Start (`>>`) | Menú de pausa (Inventario, Oculi, Habilidades, Grupo, Mapa, Confesiones) | **SÍ, recentrado** |
| Back (`<<`) | Ninguna — B ya hace "atrás" en menús y combate | **NO — quitado** |
| Engranaje | Ajustes propios del port (menú oculto) | **SÍ** |
| TAB | Restaurar el overlay cuando está oculto | **SÍ** |

Implementación: solo `PadGeometry.java` — se eliminó la creación de
`out[ID_LB]`, `out[ID_RB]`, `out[ID_Y]`, `out[ID_BACK]`; `ID_START` se
recentró en `midX` (antes `midX + SMALL_GAP`); se eliminó la constante
`SMALL_GAP`. Las constantes `ID_*` siguen declaradas (API pública de la
clase) y los `case` de `VirtualPadView`/`PadTheme` para los IDs quitados
quedan como código muerto inofensivo. Seguro verificado: `onDraw` salta
nulos (`VirtualPadView.java:823`), el hit-test comprueba `mControls[p.control]
!= null` (`:663`, `:704`), `PadGeometry.hitResolve` salta nulos (`:322`).
Ningún test referencia PadGeometry/VirtualPad.

Corrección a un informe intermedio: el worker de controles afirmó que
`<<` `>>` "no existen en el código del overlay" — **falso**. Verificado
en el código real: `VirtualPadView.java:1106` ("Double chevrons:
right-pointing for Start, left-pointing for Back"), `PadGeometry.java:225-228`
(ID_BACK/ID_START), `VirtualPadView.java:561-563` (ID_BACK→BTN_BACK,
ID_START→BTN_START). `>>` = Start (se queda), `<<` = Back (se quita).

---

## 2. Bug A — no gira solo a horizontal. Bug B — título aplastado

### Causa exacta (una sola, cura los dos)

El `sensorLandscape` del manifest **y** el `setRequestedOrientation()` de
`MainActivity` son **sobrescritos por el propio SDL**: al crear la ventana,
SDL llama a Java `SDLActivity.setOrientation(1280, 720, resizable=true,
hint="")` y eso termina en `SCREEN_ORIENTATION_FULL_USER`, que sigue el
ajuste de rotación del usuario en vez de forzar horizontal. El último
escritor gana, y el último escritor es SDL.

Cadena verificada en el código real:
- `SDK/src/ui/rex_app.cpp:337` crea la ventana 1280×720;
  `SDK/src/ui/window_sdl.cpp:141-149` la crea con `SDL_WINDOW_RESIZABLE`.
- `SDL/thirdparty/sdl3/src/video/android/SDL_androidwindow.c:54`
  (`Android_CreateWindow`): `Android_JNI_SetOrientation(w, h, resizable,
  hint)` con hint vacío (nadie fija `SDL_HINT_ORIENTATIONS`).
- `SDL/src/core/android/SDL_android.c:1712-1719` reenvía a
  `SDLActivity.setOrientation` → `setOrientationBis`
  (`APP/java/org/libsdl/app/SDLActivity.java:1114-1189`): hint vacío +
  redimensionable → líneas 1159-1162 → `FULL_USER` → línea 1189
  `setRequestedOrientation(FULL_USER)`.
- Agravante: cada `SDL_SetWindowFullscreen` vuelve a llamar
  (`SDL_androidwindow.c:174`; `window_sdl.cpp:157,182`) — un
  `setRequestedOrientation()` puntual no sirve.

Bug B es consecuencia directa de A: la primera superficie nace en vertical
(1080×2400; en el A17 real 1080×2340), el presentador crea el swapchain en
vertical y dibuja el fotograma 16:9 con letterbox dentro: banda de
**1080×607 px en y=896** (~25 % de la altura), negro arriba y abajo
(`presenter.cpp:857-977`, cálculo verificado). **El presentador NO está
roto**: su matemática de letterbox es correcta para la superficie que le
dan. No se toca el presentador. Matiz honesto: el código centra la banda
(`output_y = 896`, no `0`); si en la captura se veía anclada arriba y no
centrada, eso no lo explica este cálculo — haría falta la captura o el
registro. La cadena de recuperación al rotar SÍ está cableada
(`SDLSurface` → `onNativeResize` → `SDL_EVENT_WINDOW_RESIZED` →
`window_sdl.cpp:464-466` → `presenter.cpp:390-415,1346-1391` → swapchain
recreado en horizontal), por eso forzar la rotación a mano recuperaba el
título.

### Arreglo (dos capas, combinables)

1. **Hint SDL (nativo)** — `app/src/main/cpp/android_main.cpp`, en
   `RunAndroidApp()`, **antes** de `SDL_InitSubSystem(SDL_INIT_VIDEO)`:
   `SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft")` (+
   `#include <SDL3/SDL_hints.h>`; el hint existe en el SDL3 vendido,
   `sdk/rexglue-sdk/thirdparty/sdl3/include/SDL3/SDL_hints.h:3112`, y
   `SDL_SetHint` ya se usa en el SDK). Con hint `"LandscapeLeft"`,
   `setOrientationBis` pone `SCREEN_ORIENTATION_LANDSCAPE` fijo. El hint
   persiste, así que cada `SDL_SetWindowFullscreen` re-aplica lo mismo.
2. **Sobrescritura Java** — `MainActivity.java`: `setOrientationBis`
   sobrescrito (método público de instancia; el comentario de SDL dice
   "This can be overridden") para aplicar la orientación elegida por el
   usuario en los ajustes (`applyOrientation()`, horizontal por defecto)
   en vez de la petición de SDL. Neutraliza **todas** las llamadas de SDL.
   Respeta la elección del usuario: si elige "Vertical" o "Auto" en el
   menú de gráficos, eso es lo que se aplica (el override no fuerza
   horizontal a ciegas).

Lo que NO basta (no hacer solo esto): cambiar solo el manifest (ya estaba
en `sensorLandscape` y SDL lo sobrescribe); mover `applyOrientation()`
antes de `super.onCreate()` (ayuda pero SDL vuelve a poner `FULL_USER`).

---

## 3. Bug C — el juego viene en inglés por defecto

### Causa exacta

El juego **no** usa `XGetLanguage`/`XamGetLanguage`. Lee el idioma con
`ExGetXConfigSetting(category 3, setting 9)` = `XCONFIG_USER_LANGUAGE`
(`autoports/childoflight/port/generated/default/ppc_recomp.249.cpp`,
`sub_82947278`, llamada en ~:16808), y el kernel lo responde con el cvar
**`user_language` (por defecto 1 = inglés)** (`xam_user.cpp:31`;
`xboxkrnl_xconfig.cpp:64-65`). El fallback por región tampoco salva:
`XGetGameRegion` siempre devuelve 0xFFFF (`xam_info.cpp:177-179`) → cae
en inglés. Por eso el log mostraba `plan_logo.en.srt`.

Enum `XLanguage` (`xcontent.h:23-36`): kEnglish=1 … kSpanish=5. El país es
aparte: `user_country` por defecto 103 = US (`xam_locale.cpp:24`); 31 = ES.

**Recursos en español confirmados en los datos del juego** (resuelve el
punto abierto §1.8 del informe de locale): `bundle_x360.ipk` contiene
`localization_spanish.isg.ckd` y `localization_latamspanish.isg.ckd`;
`child_of_light_x360.ipk` contiene subtítulos por cinemática `*.es.srt`
(p. ej. `r00d02_cin060_cs_goodending_bink.es.srt`, junto a .de/.en/.fr/
.it/.ja/.mx/.pt). Con `user_language=5` el juego encuentra sus recursos
en español. (Las voces son solo en inglés: solo existen `sounds_eng.pck`
+ `sounds_sfx.pck`; el idioma es texto/subtítulos.)

### Arreglo (sin código nativo)

`GraphicsSettings.java`: preferencia `language()` con auto-detección del
locale de Android en la primera lectura (`Locale.getDefault().getLanguage()`,
guardada al leerla; "es" si está vacío) + `setLanguage()`; `languageId()`
(es→5, ja→2, de→3, fr→4, it→6, ko→7, pt→9, ru→12, pl→11, resto→1) y
`countryId()` (es→31, resto→103 = valor anterior; solo es está verificado).
`asCvars()` emite `user_language` + `user_country` **antes** de las líneas
`extra` manuales, así que un valor escrito a mano sigue ganando. El camino
ya existía: settings.txt → `android_main.cpp:95-116` ReadSettingsArgs →
`cvar::Init` antes de crear la app.

Endurecimiento del SDK **omitido a propósito en v1.3**: `XGetLanguage_entry`
(`xam_info.cpp:185-198`) fija inglés a mano — hacer que devuelva
`REXCVAR_GET(user_language)` son 2 líneas, pero el juego no llama a esa
función; queda como seguimiento.

---

## 4. Dirección "port oficial" — la home desaparece

Decisión del usuario (2026-11-13 13:22): "Si el port va a ser como si fuera
uno oficial debemos descartar toda esta interfaz". Un port oficial no tiene
pantalla de elegir ISO, ni botón de controlador GPU, ni registros a la
vista: se toca el icono y se entra directo al juego.

### Cambios concretos

- **Manifest**: el intent-filter LAUNCHER pasa de `.SetupActivity` a
  `.MainActivity`. SetupActivity pasa a `exported=false` (la arranca
  MainActivity cuando falta el juego); MainActivity pasa a `exported=true`.
  MainActivity ya era `sensorLandscape` + `singleTask`.
- **SetupActivity reescrita** (920 → ~450 líneas): ya no hay home. Es un
  router + pantalla mínima de importar:
  - `onCreate` → muestra primero el informe de fallo si lo hay
    (`LogsDialog.showCrashReportIfAny`, con continuación al descartar:
    el nativo borra crash.txt al arrancar, así que debe verse antes);
  - si `GameFiles.hasValidGameRoot()` → `launchGame()` + `finish()` sin
    mostrar nada;
  - si no, pantalla mínima (título, línea de edición, una línea de ayuda
    `first_run_help`, dos botones: Elegir juego / Elegir carpeta,
    estado + progreso) y, en builds full, auto-import del game.stfs.
  - `finish(File)` ya no refresca ni muestra toast: tras la puerta de
    Title ID y escribir game_root.txt, lanza el juego directamente.
  - Toda la maquinaria de importación queda intacta y sin tocar
    (`importFile`, `importPackage`, `importTree`, `copyTree`, `findXex`,
    `importStfsFile`, `maybeAutoImportBundledGame`, puerta de Title ID).
- **GraphicsDialog.java (nuevo)**: el diálogo de gráficos movido tal cual
  desde SetupActivity (estático, con `Activity` como parámetro) + el
  diálogo de controlador GPU (Turnip, solo Adreno) + `REQ_PICK_DRIVER`
  y `onDriverPicked()` para el resultado del picker.
- **LogsDialog.java (nuevo)**: el visor de registros + el informe de fallo
  movidos tal cual desde SetupActivity (con sus ayudantes `tail`,
  `shareText`, `copyText`, `logsDir`, `crashFile`, `gameLog`).
  `showCrashReportIfAny(Activity, Runnable onDismiss)` devuelve si mostró
  el informe; el router continúa al descartar.
- **PadSettingsDialog.java** (menú oculto del engranaje, en el juego):
  segunda fila de botones — Gráficos / Registros / Quitar archivos
  (con confirmación; borra gameDir + game_root.txt y cierra la actividad:
  el siguiente toque al icono vuelve a la pantalla de importar) /
  Controlador GPU (solo si `GpuDriver.isAdreno()`). Nada de ajustes se ve
  de camino al juego.
- **MainActivity.java**: `onActivityResult` reenvía el picker de
  controlador a `GraphicsDialog.onDriverPicked` (el diálogo vive ahora
  en el menú dentro del juego).
- **strings.xml**: eliminadas 11 huérfanas de la home (`hero_tagline`,
  `section_import`, `section_settings`, `section_data`, `status_label`,
  `setup_help`, `setup_help_full`, `status_idle`, `status_ready`,
  `status_done`, `play`); añadida `first_run_help` (una línea corta).
  Verificado por grep: cero referencias restantes a las eliminadas.
- **Drawables huérfanos** (archivos que quedan en disco, sin referencias):
  `aurora_hero`, `card_status`, `btn_primary` — el botón primario era
  solo de JUGAR y la home usaba los otros dos. No rompen el build; se
  borran en un push de limpieza aparte (el push por navegador de un
  borrado es otro flujo).

### Qué queda igual

`GameFiles`, `IsoExtractor`, `StfsExtractor`, `TitleDb`, `GpuDriver`
sin cambios. El auto-import de la variante full es el mismo código de
siempre (`maybeAutoImportBundledGame` + `importStfsFile`).

---

## 5. Datos del log apuntados para la v1.3 (sin arreglar)

- **Texturas DXT sin comprimir (4x memoria)**: Mali no tiene formatos BC
  → cada textura DXT se decodifica por shader de carga a RGBA8
  (`texture_cache.cpp:2475`, `kLoadShaderIndexDXT1ToRGBA8`): 4x memoria +
  4x ancho de banda de subida + un pase de decodificación por
  invalidación. El propio comentario del código dice que "en móvil esto
  solo puede dominar el tiempo de frame". Los TODOs de upstream
  (S3TC→ETC2/EAC, S3TC→5551/4444) son investigación, no un arreglo rápido.
  Registrado, no es un bug.
- **Frame saltado** (`command_processor.cpp:2308`): con
  `async_shader_compilation` + `vulkan_async_skip_incomplete_frames`, los
  frames que usan un pipeline placeholder asíncrono se saltan enteros
  (se registra una vez). Esperable en el primer arranque / zonas nuevas;
  no es un bug.

---

## 6. Verificación del build v1.3

### Build 1 — FALLO solo al firmar (no es un error de código)

`analysis/fase4-build-1.log`: BUILD FAILED en 9m 41s en `:app:packageRelease` —
`KeytoolException: Failed to read key rexauto from store ".../ci-release.jks":
No key with alias 'rexauto' found`. Causa: el keystore local (corazón) trae
solo el alias `childoflight<3`, pero `build.gradle.kts:81` dice `rexauto`
(CI lo cambia con sed tras restaurar el secreto, workflow línea 337). Toda
la compilación Java + nativa había salido bien; solo faltaba firmar.

Arreglo: sed local del alias antes del build + revert después. Para que no
vuelva a pasar, los scripts v13 ya lo hacen solos:
- `tools/run-release-build-v13.sh`: sed + `trap` de revert (se quitó el
  `exec` de Gradle para que el trap corra).
- `tools/run-release-build-full-v13.sh`: el sed + el revert viven en su
  `cleanup()` del trap EXIT (probado: tras el build full el archivo volvió
  a decir `rexauto`, verificado por grep).
- `build.gradle.kts` en el árbol sigue diciendo `keyAlias = "rexauto"` —
  el archivo que se pushea NO lleva el alias corazón (CI lo cambia).

### Build 2 — v1.3 normal: BUILD SUCCESSFUL

`analysis/fase4-build-2.log`: BUILD SUCCESSFUL en 53 s (incremental).
- APK: `private/ChildOfLight-recomp-v1.3.apk`, **24.553.811 B**,
  SHA-256 `de5da2673ff7546a90bcd18f79ad74485f73b29b24a28715e6e964bd5af3bae0`.
- `apksigner verify` OK, firmante corazón (CN="el título <3",
  SHA-256 `3768a83d38837f424138c1570f77e111bd1777ff6df049d476e562a46bb189ab`,
  mismo que v1.1/v1.2).
- `aapt2 dump badging`: package `com.rexauto.port.childoflight`,
  versionCode **3**, versionName **1.3**, minSdk 28, targetSdk 35;
  **launchable-activity `com.rexauto.port.MainActivity`** (el icono ya
  abre el juego directo).
- Contenido: libmain.so 55.277.648 B, librexgpu-xenos.so 4.522.600 B,
  librexruntime.so 8.620.280 B, hero_bg.jpg; sin game.stfs (variante
  normal, correcto).
- El hint de orientación está compilado en libmain.so (`strings` encuentra
  `SDL_ORIENTATIONS`, 1 ocurrencia).
- Icono verificado píxel a píxel contra la fuente mdpi de Fase 3
  (nxeslot.jpg): píxeles idénticos (la diferencia de bytes es solo el
  crunch de PNG de aapt2).
- Drive PRIVADO (verificado por conector): file ID
  `19RiCkVw0jO130IeSLs7RR1PDXHL06Peu`, tamaño exacto 24.553.811 B,
  `shared: false`, solo el propietario.
  https://drive.google.com/file/d/19RiCkVw0jO130IeSLs7RR1PDXHL06Peu/view

### Build full — v1.3-full: BUILD SUCCESSFUL

`analysis/fase4-build-full-1.log`: EXIT=0, zipalign OK, firma corazón.
- APK: `private/ChildOfLight-recomp-v1.3-full.apk`, **1.610.016.704 B**,
  SHA-256 `3f636102c23b096aa4a2d116355a80afbbe41d469113e4ac63a1d9f22fef7d91`,
  MD5 `c8cb3c1f51a87958250ef253b84803b6`.
- `apksigner verify` OK (mismo firmante corazón). versionCode 3,
  versionName **1.3-full**; launchable-activity MainActivity.
- `assets/game.stfs`: **una sola entrada, STORED, 1.585.451.008 B**;
  extraída da MD5 `ad88061a06fba28e3434945f37cb3d57` = **exacto al STFS
  original**. (Ojo: la primera extracción a /tmp salió truncada a 295 MB
  porque /tmp estaba al 100 % — no era un fallo de la APK; re-extraída en
  disco grande, MD5 exacto. /tmp limpiado después.)
- Drive PRIVADO, carpeta "el port v1.3-full" (ID
  `1A9o916zgOxtu8RIYEQTfo1pwfRWtFPPY`), **19 archivos, todos
  `shared: false`**, tamaños exactos: 16 partes (.part00–.part15, 100 MiB
  salvo la última de 37.152.704 B) + MD5SUMS.txt + README-v1.3-full.txt +
  la APK normal v1.3. Reensamblado verificado en local antes de subir
  (`cat part* | md5sum` = `c8cb3c1f…`).
  https://drive.google.com/drive/folders/1A9o916zgOxtu8RIYEQTfo1pwfRWtFPPY
- Partes locales en `private/v13-full-parts/` (se conservan por si hay
  que re-subir alguna).

---

## 7. Archivos para pushear (el push lo hace el padre, por navegador)

Modificados:
- `tools/rexauto/android/app/src/main/AndroidManifest.xml`
- `tools/rexauto/android/app/src/main/cpp/android_main.cpp`
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/GraphicsSettings.java`
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/MainActivity.java`
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/SetupActivity.java`
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/gamepad/PadGeometry.java`
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/gamepad/PadSettingsDialog.java`
- `tools/rexauto/android/app/src/main/res/values/strings.xml`

Nuevos:
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/GraphicsDialog.java`
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/LogsDialog.java`
- `analysis/FASE4.md` (este informe)
- `tools/run-release-build-v13.sh` + `tools/run-release-build-full-v13.sh`
- `analysis/FASE4-controles-internet.md`, `analysis/FASE4-locale-oficial.md`,
  `analysis/FASE4-pantalla.md` (investigación; a criterio del padre)

No se pushea: nada del juego (STFS, DLCs, `private/`), ni keystores, ni
`analysis/fase4-build-*.log` (logs locales).

---

## 8. Qué falta por probar en el teléfono (nada de esto está probado en real)

1. Tocar el icono con el teléfono en vertical y la rotación automática
   **apagada**: debe entrar directo en horizontal, sin pantalla de
   importar (variante full) o con la pantalla mínima solo la primera vez
   (normal).
2. La pantalla de título debe llenar la pantalla en horizontal (banda 16:9
   a lo ancho, sin aplastar).
3. Los menús del juego en español (el log debe mostrar `plan_logo.es.srt`
   en vez de `.en.srt`).
4. Controles: solo LT, RT, sticks, D-pad, A, B, X, Start y engranaje;
   confirmar que RT hace lo de Igniculus-interactuar en 360 (reserva del
   informe de controles) y si el D-pad hace falta en menús.
5. Girar el teléfono durante el juego y volver: el swapchain debe seguir
   la rotación sin quedarse en negro ni aplastado. Si el título **sigue**
   aplastado tras forzar la rotación, reabrir con captura +
   `childoflight.log` (buscar `Window size:` de `SDLSurface`).
6. Auto-import de la variante full de principio a fin; "Quitar archivos"
   desde el menú oculto y re-importar.
7. Sin DEVICE_LOST pasados ~4 s (el fix GPU de v1.2 sigue vigente).

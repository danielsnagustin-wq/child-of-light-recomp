# FASE 4 — Locale (español por defecto) + plan "port oficial"

Investigación solo lectura, 2026-11-13. Todo verificado leyendo el código real
(rutas relativas a `~/workspace/col-recomp/`). Nada de memoria.

Contexto: la v1.2 arranca en el teléfono (Helio G99 / Mali-G57 MC2) y el juego
corre, pero en inglés: el log muestra que el juego buscó `plan_logo.en.srt`.
Decisión del usuario: el port debe sentirse oficial — tocar el icono y entrar
directo al juego, sin home del launcher.

---

## 1. LOCALE — dónde se fija el idioma de la consola emulada

### 1.1 Veredicto en una frase

El juego **NO** usa `XGetLanguage` ni `XamGetLanguage`. Lee el idioma con
**`ExGetXConfigSetting(categoría 3, ajuste 9)` = `XCONFIG_USER_LANGUAGE`**, que
el kernel responde con el cvar **`user_language` (por defecto 1 = inglés)**.
Nada lo cambia nunca: ni el locale de Android ni ningún ajuste. Poner
`user_language=5` en `settings.txt` (vía `GraphicsSettings`) da español sin
tocar código nativo.

### 1.2 La función del juego que decide el idioma (código recompilado)

`autoports/childoflight/port/generated/default/ppc_recomp.249.cpp`,
`función `sub_82947278` (líneas ~16780–16860):

1. `ExGetXConfigSetting(3, 9)` — categoría 3 (`XCONFIG_USER_CATEGORY`),
   ajuste 9 (`XCONFIG_USER_LANGUAGE`). Guarda el valor en la pila.
2. Si la llamada falla o el valor es 0 o > 17, descarta el valor y hace
   fallback por región: llama a `XGetGameRegion` y según el resultado devuelve
   2 (japonés, si región es 0x101), 7 (coreano, si región es 0x100) o
   **1 (inglés) en cualquier otro caso**.
3. Si el valor es válido (1–17), devuelve ese valor como idioma del juego.

Verificación de que es la única lectura de idioma del juego (parseo de los
12 `ExGetXConfigSetting` del código recompilado):

| Sitio | Categoría | Ajuste | Qué es |
|---|---|---|---|
| `ppc_recomp.249.cpp:11911` | 2 | 2 | `XCONFIG_SECURED_AV_REGION` (chequeo PAL) |
| `ppc_recomp.249.cpp:11936` | 3 | 10 | `XCONFIG_USER_VIDEO_FLAGS` |
| **`ppc_recomp.249.cpp:16808`** | **3** | **9** | **`XCONFIG_USER_LANGUAGE` ← el idioma** |
| `ppc_recomp.250.cpp:12953–13197` | 3 | 1,2,3,4,5,6,7,12 | zona horaria (bias, nombres, fechas, flags) |
| `ppc_recomp.277.cpp:13605` | 3 | 10 | `XCONFIG_USER_VIDEO_FLAGS` |

Además el juego llama a `XamGetLocaleEx(111, 43)` a través del envoltorio
`sub_82947308` (`ppc_recomp.249.cpp:16878`). Ese es el *locale* (país/región),
no el idioma — ver §1.4.

Búsquedas negativas (verificadas por grep sobre el código generado):
- `__imp__XGetLanguage`: **cero** llamadas en el juego.
- `__imp__XamGetLanguage` / `__imp__XamGetLocale`: **cero** llamadas.
- Literal `plan_logo` o `.srt`: **cero** apariciones en el código generado —
  el nombre del archivo de subtítulos se construye desde tablas internas del
  juego a partir del id de idioma que devuelve `sub_82947278` (inferencia,
  no verificado en el binario: el sufijo `en` corresponde al id 1).

### 1.3 Lado kernel: qué devuelve hoy y de dónde sale

- `tools/rexauto/android/sdk/rexglue-sdk/src/kernel/xboxkrnl/xboxkrnl_xconfig.cpp:64-65`
  (`xeExGetXConfigSetting`, caso `0x0009` `XCONFIG_USER_LANGUAGE`):
  `memory::store_and_swap<uint32_t>(value, REXCVAR_GET(user_language));`
- `tools/rexauto/android/sdk/rexglue-sdk/src/kernel/xam/xam_user.cpp:31`:
  `REXCVAR_DEFINE_UINT32(user_language, 1, "Kernel", "User's language ID");`
  → **valor por defecto 1 = inglés**. Nadie lo reescribe en arranque.
- Enum `XLanguage` en
  `tools/rexauto/android/sdk/rexglue-sdk/include/rex/system/xcontent.h:23-36`:
  `kInvalid=0, kEnglish=1, kJapanese=2, kGerman=3, kFrench=4, kSpanish=5,
  kItalian=6, kKorean=7, kTChinese=8, kPortuguese=9, kSChinese=10, kPolish=11,
  kRussian=12`.
- El mismo cvar alimenta también los textos de logros en español:
  `xam_user.cpp:660-667` y `src/system/kernel_state.cpp:288-295`
  (`db.GetExistingLanguage(user_language)` + `GetStringTableEntry`).

Por qué hoy sale inglés, cadena completa:
`user_language` = 1 (default) → `XConfig(3,9)` = 1 → `sub_82947278` = 1 →
el juego carga recursos `*.en.*` (`plan_logo.en.srt`). El fallback por región
tampoco salvaría nada: `xeXGetGameRegion()` en
`src/kernel/xam/xam_info.cpp:177-179` devuelve siempre `0xFFFF`, y con ese
valor el fallback del juego cae en `li r3,1` (inglés).

### 1.4 El locale (país) es un camino separado — también en inglés hoy

- `src/kernel/xam/xam_locale.cpp:24`:
  `REXCVAR_DEFINE_UINT32(user_country, 103, "Kernel", "User's country ID");`
  103 = **US** (verificado por programa contra la tabla
  `xeXamGetCountryString` del mismo archivo).
- `xeXamGetLocaleEx()` (`xam_locale.cpp:190-211`): país → locale con la tabla
  `xeXamGetLocaleFromCountry`; con 103 (US) da locale **36 = US**
  (`xeXamGetLocaleString`: 31 = ES, 35 = GB, 36 = US — verificado).
  Con país **31 = ES** daría locale **31 = ES**.
- Para coherencia total (idioma + locale) hay que poner los dos cvars:
  `user_language=5` **y** `user_country=31`. El idioma es el que manda para
  los `.srt`; el país/locale afecta a formatos y a lo que el juego pida vía
  `XamGetLocaleEx`.

### 1.5 Dos trampas que NO son la causa (pero conviene endurecer)

- `XGetLanguage` (`src/kernel/xam/xam_info.cpp:185-198`, `XGetLanguage_entry`):
  **ignora por completo los cvars**. Devuelve inglés siempre: tiene
  `game_region = XEX_REGION_NTSCU` hardcodeado (con un TODO "pull from xex
  header") y la rama NTSCU → `kEnglish`. El juego no lo llama hoy, pero es
  una bomba latente: si algún otro título o un parche futuro lo usa, el
  idioma volvería a inglés. Endurecimiento opcional (2 líneas): que devuelva
  `REXCVAR_GET(user_language)` con el fallback por región actual solo si el
  cvar es 0/inválido.
- `XamGetLanguage` (`xam_locale.cpp:421`): es `REX_EXPORT_STUB`, es decir,
  solo registra un `REXKRNL_WARN("STUB")` (`include/rex/hook.h:52-57`) y
  **no escribe ningún valor de retorno en r3** (quedaría basura del registro).
  El juego no lo llama; no tocar salvo que otro título lo necesite.
- No confundir con `init_command.cpp:558,627` (`--language` de
  `rexglue init achievements`): ese flag solo elige en qué idioma se extraen
  los textos de logros al TOML en tiempo de compilación; **no afecta al
  runtime**.

### 1.6 El camino completo del ajuste hasta el núcleo (ya existe, sin JNI)

1. `GraphicsSettings.asCvars()` escribe `settings.txt`
   (`tools/rexauto/android/app/src/main/java/com/rexauto/port/GraphicsSettings.java`,
   `write()` → `GameFiles.settingsFile()`, una línea `clave=valor`).
2. `tools/rexauto/android/app/src/main/cpp/android_main.cpp:95-116`
   (`ReadSettingsArgs`): cada línea → argumento `--clave=valor`
   (las `env.*` van a `setenv`).
3. `android_main.cpp:579-581`: `rex::cvar::Init(argc, argv)` **antes** de
   crear la app. En `src/core/cvar.cpp:582-620` cada cvar registrado se
   expone como opción CLI11 `--nombre` (el nombre es el del macro, `#name`:
   `user_language`, `user_country`), forma `--nombre=valor` aceptada; los
   no reconocidos aún quedan en pendientes y se aplican al registrarse.
4. En runtime, `REXCVAR_GET(user_language)` lee el valor ya fijado.

Conclusión: **basta con que `settings.txt` contenga `user_language=5`**
(idealmente también `user_country=31`) antes de arrancar el juego. No hace
falta tocar C++, ni recompilar el SDK, ni JNI nuevo.

### 1.7 Cambio concreto recomendado

En `GraphicsSettings.java`:

```java
// Nuevo ajuste persistente (SharedPreferences "graphics"), por defecto "es".
public String language() { return prefs.getString("language", "es"); }
public void setLanguage(String v) { prefs.edit().putString("language", v).apply(); }
```

En `asCvars()`, añadir al mapa (antes del bloque de `extra`, para que el
`extra` manual siga pudiendo sobreescribirlo si alguien lo escribe a mano):

```java
m.put("user_language", Integer.toString(languageId(language()))); // "es" -> 5
m.put("user_country", Integer.toString(countryId(language())));   // "es" -> 31
```

con una tablita `languageId`/`countryId` (es→5/31, en→1/103, de→3/…, fr→4,
it→6, ja→2, ko→7, pt→9, ru→12; resto→1/103). `SetupActivity.finish()` y
`launchGame()` ya llaman a `new GraphicsSettings(this).write(this)` antes de
arrancar, así que el `settings.txt` llega actualizado sin más cambios.

Auto-detección en primer arranque (recomendado, sin selector visible — port
oficial no tiene pantalla de ajustes): en el primer `write()` (o al crear
`GraphicsSettings`), si no hay preferencia guardada, usar
`Locale.getDefault().getLanguage()` del sistema Android y mapearlo con la
misma tabla. **Verificado: hoy ningún código Java ni nativo lee
`Locale.getDefault()` / `Resources.getConfiguration()` para el idioma** —
habría que añadirlo; es ~10 líneas en `GraphicsSettings` o en la actividad
router (ver §2).

Opcional, para no depender del selector: un selector de idioma escondido
junto a los ajustes gráficos (§2.4) que escriba la misma preferencia. El
cambio surte efecto al siguiente arranque (el cvar se lee en cada llamada a
`XConfig(3,9)`, pero el juego resuelve su idioma al inicio; no hay
recarga en caliente).

Endurecimiento opcional del SDK (recomendado, 2 archivos, sin riesgo):
- `xam_info.cpp:185-198` (`XGetLanguage_entry`): devolver
  `REXCVAR_GET(user_language)` (validado 1–12, si no el fallback actual).
- Dejar `XamGetLanguage` como stub (el juego no lo usa); si se implementa,
  que devuelva lo mismo que `XGetLanguage`.

### 1.8 Verificación pendiente (no se pudo verificar aquí)

- **Que el juego traiga los recursos en español**: comprobar en los datos
  extraídos que existe `plan_logo.es.srt` (y resto de `*.es.*`). Si el disco
  PAL no trajera español, `user_language=5` no tendría de dónde cargar y el
  juego podría caer a inglés o fallar — hay que confirmarlo en el `game/`
  extraído antes de dar el cambio por bueno. El fallback
  `GetExistingLanguage` del XDBF solo cubre títulos/logros, no los `.srt`.
- Prueba en el teléfono: tras el cambio, el log debe mostrar la búsqueda de
  `plan_logo.es.srt` en lugar de `.en.srt`, y los menús en español.

---

## 2. PLAN "PORT OFICIAL" — tocar el icono y entrar directo al juego

### 2.1 Flujo actual (v1.2, verificado)

- `tools/rexauto/android/app/src/main/AndroidManifest.xml`: la actividad
  LAUNCHER es **`.SetupActivity`** (`exported=true`, `screenOrientation=
  fullUser`); `.MainActivity` es `exported=false`, `singleTask`,
  `sensorLandscape`.
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/SetupActivity.java`
  (~1000 líneas, UI construida por código, sin XML): `onCreate()` construye
  TODA la home — hero con portada (`loadHeroBg`/`loadCover`), tarjeta de
  estado, botón grande **JUGAR** (`launchGame()`), sección importar
  (`pickFile`/`pickFolder`), sección ajustes (`showGraphicsDialog`,
  `showDriverDialog`), sección datos (`showLogsDialog`, `reset`), ayuda al
  pie — y al final llama a `refresh()`, `showCrashReportIfAny()` y
  `maybeAutoImportBundledGame()`.
- `SetupActivity.launchGame()`: escribe `settings.txt` y hace
  `startActivity(MainActivity)`.
- `MainActivity.java` (`onCreate`): si `!GameFiles.hasValidGameRoot(this)`
  → vuelve a `SetupActivity` y `finish()`; si no, arranca SDL y el juego.
  `GameFiles.hasValidGameRoot()` = existe `game_root.txt` y
  `<root>/default.xex` (`GameFiles.java`).
- `BuildConfig.FULL` se define en `app/build.gradle.kts:37`
  (`buildConfigField("boolean", "FULL", rexFull.toString())`).

### 2.2 El auto-import de la variante full YA existe — localizarlo

`SetupActivity.maybeAutoImportBundledGame()` (**líneas ~458–520**):

- Solo si `BuildConfig.FULL`, si NO hay juego válido y si existe el asset
  `game.stfs` (`BUNDLED_GAME_ASSET`, línea 54; `hasBundledGameAsset()`).
- Copia `game.stfs` del APK a `<external>/game.stfs`, lo importa con
  `importStfsFile()` (el mismo código STFS del picker manual) a
  `GameFiles.gameDir()`, y termina en `finish(xex)`.
- `finish(File xex)` (~líneas 700–720): verifica el Title ID contra
  `BuildConfig.TITLE_ID` (rechaza otro juego), escribe `game_root.txt`,
  escribe `settings.txt` con los valores por defecto, y hoy solo hace
  `refresh()` + Toast "listo". **No arranca el juego solo.**

### 2.3 Cambios concretos

#### (a) Tocar el icono → directo al juego, sin home

1. **`AndroidManifest.xml`**: mover el `<intent-filter>` MAIN/LAUNCHER de
   `.SetupActivity` a **`.MainActivity`** y poner `MainActivity
   android:exported="true"`; `SetupActivity android:exported="false"` (sin
   intent-filter). `MainActivity` ya es el guardián: sin juego válido
   redirige a `SetupActivity`.
2. **`SetupActivity.onCreate()`**: convertirla en **router transitorio, sin
   UI**: lo primero, `if (GameFiles.hasValidGameRoot(this)) { launchGame();
   finish(); return; }`. Si hay juego, el usuario nunca ve nada (la
   actividad no llega a inflar la home). Si no hay juego, sigue al flujo
   de primer arranque (§2.3b/§2.3c). `MainActivity` redirigiendo a
   `SetupActivity` solo ocurre en ese caso, así que el bucle es imposible.
3. **Borrar de `SetupActivity` toda la construcción de la home** en
   `onCreate()`: hero (`loadHeroBg`/`loadCover`, `hero_tagline`), tarjeta de
   estado (`status`, `progress` como vista fija), botón JUGAR
   (`primaryButton`/`playBtn`), etiquetas de sección, botones de importar /
   gráficos / driver / registros / reset, texto de ayuda (`setup_help`,
   `setup_help_full`), y los helpers `button()`, `primaryButton()`,
   `sectionLabel()`, `label()`, `refresh()` (su lógica de
   visibilidad/estado se reparte entre el router y la pantalla mínima).
   Quedan sin uso los drawables `aurora_hero`, `card_status`,
   `btn_primary`, `btn_secondary` y los strings `hero_tagline`, `edition`,
   `status_ready`, `status_idle`, `play`, `setup_help`, `setup_help_full`
   (revisar `res/values/strings.xml` antes de borrarlos; varios se
   reutilizan en la pantalla mínima, §2.3c).
4. `launchGame()` queda como está (escribe `settings.txt` y arranca
   `MainActivity`); `MainActivity` no cambia salvo el manifest (su
   `applyOrientation()` y el resto, intactos). Nota: la orientación es
   otro bug de Fase 4 — `MainActivity` fija `sensorLandscape` pero la
   `SetupActivity` saliente era `fullUser`; con el router transitorio el
   arranque pasa por `MainActivity` desde el principio.

#### (b) Variante full: auto-import en primer arranque y luego directo al juego

1. Reutilizar `maybeAutoImportBundledGame()` tal cual (ya existe y funciona
   en v1.2). En el router, llamarla cuando `BuildConfig.FULL` y no hay juego
   válido.
2. Cambiar **`finish(File xex)`**: añadir un parámetro o una bandera
   `autoLaunch`: cuando el import fue automático (full), tras escribir
   `game_root.txt` y `settings.txt`, llamar a `launchGame()` y `finish()`
   en vez de `refresh()` + Toast. El import manual (§2.3c) usa el mismo
   camino (ver §2.3c, punto 2).
3. La UI durante el auto-import: hoy es la tarjeta de estado + `ProgressBar`
   de la home. Sustituirla por una **pantalla mínima de progreso**
   (splash a pantalla completa: fondo del juego + barra de progreso +
   texto de estado, reutilizando `post()`/`postProgress()` y el `progress`
   existente). Es la única pantalla visible en full, y solo la primera vez
   (~1,6 GB de copia/extracción). Si falla, mostrar el error con opción de
   reintentar (el `fail()` actual, sin home detrás).
4. Nada más que hacer en full: en arranques posteriores `hasValidGameRoot`
   es true y el router va directo al juego.

#### (c) Variante normal: pantalla mínima de importar SOLO la primera vez

1. `SetupActivity` conserva **solo** esto, en una pantalla mínima (un
   título, un texto corto, dos botones, progreso, error):
   - `pickFile()` / `importFile()` (ISO / XBLA / `default.xex`),
   - `pickFolder()` / `importTree()` (carpeta ya extraída, incl. GoD),
   - `importPackage()` / `importStfsFile()` (STFS/CON/LIVE/PIRS),
   - `copyTree()`, `findXex()`, `displayName()`, `setBusy()`/`post()`/
     `postProgress()`, `fail()`,
   - `finish(File xex)` con el Title-ID gate (rechaza otro juego).
2. Cambiar `finish()`: tras el import correcto, **arrancar el juego
   directamente** (`launchGame()` + `finish()`), sin quedarse en ninguna
   pantalla ni pedir que se pulse JUGAR. El usuario toca el icono, importa
   una vez, y entra al juego; la segunda vez ya no ve esta pantalla nunca
   (el router del §2.3a la salta).
3. Borrar de esta pantalla: hero, estado, JUGAR, ajustes, registros,
   reset, ayuda larga. El texto de ayuda se reduce a una línea (qué archivo
   elegir); el `setup_help` largo se sustituye por un string corto nuevo.
4. `onActivityResult()` (`REQ_PICK_FILE`/`REQ_PICK_TREE`) se mantiene.
   `REQ_PICK_DRIVER` desaparece con el diálogo de driver (§2.3d).

#### (d) Ajustes escondidos y registros solo si algo falla

Dónde queda cada cosa hoy y dónde debe quedar:

| Hoy en la home | Queda en… |
|---|---|
| `showGraphicsDialog()` (preset, fps cap, escala, efecto, modo vídeo, orientación, vsync, letterbox, tolerante, cvars extra) + `GraphicsSettings` (SharedPreferences `"graphics"` → `settings.txt`) | **Menú escondido dentro del juego.** Ya existe el punto de entrada: el engranaje del mando virtual (`PadGeometry.ID_MENU = 13`, `VirtualPadView.java:657,690,806,1034`) abre hoy `PadSettingsDialog` (tamaño/opacidad/hápticos del mando). Añadir ahí una sección o un segundo botón "Gráficos y rendimiento" que abra el mismo `showGraphicsDialog()` (mover el método a una clase propia, p. ej. `GraphicsDialog.show(activity)` reutilizable desde `SetupActivity` —ahora solo para el primer arranque— y desde el juego). Los cambios se aplican al siguiente arranque (los cvars se leen al crear el runtime). El diálogo de driver (`showDriverDialog`, solo Adreno vía `GpuDriver.isAdreno()`) va en ese mismo menú escondido, no en la pantalla de importar. |
| `showLogsDialog()` (crash + `profile.txt` + `<proyecto>.log`) y `showCrashReportIfAny()` (`<logs>/crash.txt`, escrito por el signal handler de `android_main.cpp`) | **Solo si algo falla.** `showCrashReportIfAny()` se mantiene en el router: si existe `crash.txt`, mostrar el diálogo una vez (ya lo hace, y lo renombra a `crash.prev.txt` al descartar). El visor de registros completo pasa al menú escondido del juego (misma entrada que los gráficos), no a ninguna pantalla principal. `profile.txt` (profiler de `android_main.cpp`) igual. |
| `reset()` (borra `game_root.txt` + carpeta `game/`) | Menú escondido, detrás de confirmación (ya la tiene: `reset_confirm`). No en la pantalla de importar. |
| Idioma (§1.7) | Sin UI visible: auto-detección del locale de Android en primer arranque + cvar en `settings.txt`. Selector solo dentro del menú escondido, si se quiere. |

Archivos que cambian (resumen):

- `AndroidManifest.xml` — LAUNCHER pasa a `.MainActivity`.
- `SetupActivity.java` — se convierte en router + pantalla mínima de
  importar + auto-import full; se borra ~60% del archivo (toda la home y
  los diálogos, que se mueven o se eliminan).
- Nuevo o movido: `GraphicsDialog` (el actual `showGraphicsDialog()`),
  accesible desde el menú del juego; `PadSettingsDialog` / `VirtualPadView`
  (añadir la entrada de gráficos al engranaje `ID_MENU`).
- `GraphicsSettings.java` — añadir el ajuste de idioma (§1.7); el resto
  intacto (ya escribe `settings.txt`, que es el canal de cvars).
- `MainActivity.java` — sin cambios de lógica (solo el manifest); su
  `onCreate` ya redirige a `SetupActivity` cuando falta el juego.
- `GameFiles.java`, `IsoExtractor.java`, `StfsExtractor.java`,
  `TitleDb.java`, `GpuDriver.java` — sin cambios (la importación y el
  Title-ID gate se reutilizan tal cual).
- `res/values/strings.xml` — borrar los strings huérfanos de la home y
  añadir el texto corto de la pantalla mínima; `res/drawable` — revisar
  `aurora_hero`, `card_status`, `btn_primary`, `btn_secondary` si quedan
  sin uso.

### 2.4 Riesgos / notas para la implementación

- El router no debe mostrar ni un flash de UI: `launchGame()` + `finish()`
  antes de `setContentView()`. En full sin juego, la única UI es el
  progreso del auto-import.
- `MainActivity` es `singleTask`: si el usuario sale del juego con atrás y
  vuelve a tocar el icono, Android reanuda la tarea existente (no se
  reinicia el juego). Comportamiento deseable para un port oficial.
- El informe de crash (`crash.txt`) se borra al arrancar el nativo
  (`android_main.cpp`: "a fresh launch… start clean"), así que el router
  debe leerlo/mostrarlo **antes** de lanzar `MainActivity` (hoy ya lo hace
  así en `onCreate`, antes de `launchGame()` — mantener ese orden).
- La pantalla mínima de importar debe seguir sin pedir permisos: todo va
  por Storage Access Framework (`ACTION_OPEN_DOCUMENT` /
  `ACTION_OPEN_DOCUMENT_TREE`) y almacenamiento propio de la app, como hoy.
- Fuera de alcance de este plan (otros trabajos de Fase 4): el bug de
  orientación (no gira solo a horizontal) y el aspecto aplastado del título;
  el plan no los toca salvo el cambio de actividad inicial, que sí afecta
  al orden en que se fija la orientación.

---

## Fuentes (archivo:línea, todo leído)

- Idioma del juego: `autoports/childoflight/port/generated/default/ppc_recomp.249.cpp`
  `sub_82947278` (~16780–16860, llamada `ExGetXConfigSetting(3,9)` en :16808);
  resto de llamadas XConfig parseadas en `ppc_recomp.249/250/277.cpp`;
  `XamGetLocaleEx(111,43)` en `ppc_recomp.249.cpp:16878` (`sub_82947308`).
- Kernel: `tools/rexauto/android/sdk/rexglue-sdk/src/kernel/xboxkrnl/xboxkrnl_xconfig.cpp:64-65,80`
  (USER_LANGUAGE / USER_COUNTRY); `src/kernel/xam/xam_user.cpp:31`
  (`user_language`, default 1); `src/kernel/xam/xam_locale.cpp:24`
  (`user_country`, default 103), `:96-99` (tabla `"es"` = id 5), `:190-211`
  (`xeXamGetLocaleEx`); `src/kernel/xam/xam_info.cpp:177-198`
  (`xeXGetGameRegion` = `0xFFFF`, `XGetLanguage_entry` hardcodeado a inglés);
  `include/rex/system/xcontent.h:23-36` (enum `XLanguage`);
  `include/rex/hook.h:52-57,76-80` (stub sin retorno);
  `src/core/cvar.cpp:582-620` (`cvar::Init`, `--nombre=valor`);
  `include/rex/cvar.h:430` (`REXCVAR_DEFINE_UINT32`, nombre = `#name`).
- Android: `tools/rexauto/android/app/src/main/AndroidManifest.xml`;
  `app/src/main/java/com/rexauto/port/SetupActivity.java`
  (home en `onCreate`, `maybeAutoImportBundledGame` ~:458-520,
  `finish` ~:700-720, `launchGame`, diálogos de gráficos/driver/logs/reset);
  `MainActivity.java` (redirect si no hay juego, `applyOrientation`);
  `GameFiles.java` (`hasValidGameRoot`, `settingsFile`);
  `GraphicsSettings.java` (`asCvars`, `write`);
  `app/src/main/cpp/android_main.cpp:95-116` (`ReadSettingsArgs`),
  `:579-581` (`cvar::Init`);
  `gamepad/VirtualPadView.java:657,690,806,1034` (engranaje `ID_MENU` →
  `PadSettingsDialog.show`); `gamepad/PadGeometry.java:45` (`ID_MENU = 13`);
  `app/build.gradle.kts:37` (`BuildConfig.FULL`);
  `app/src/main/res/values/strings.xml` (strings de la home).

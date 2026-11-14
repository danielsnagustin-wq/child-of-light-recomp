# Fase 3 — El juego arranca en Mali-G57 (v1.2) + variante full privada

**Fecha:** 2026-11-13
**Entrada:** APK v1.1 (Fase 2) — primera prueba en el teléfono real del usuario.
**Resultado v1.2: ✅ APK CONSTRUIDA Y FIRMADA** (build local, BUILD SUCCESSFUL).
**Resultado variante full: ⏳ EN CONSTRUCCIÓN** (ver § "Variante full" al final).

## 1. El error en el teléfono (primera prueba real, 12:03)

Al pulsar JUGAR, el usuario solo veía los controles táctiles + fondo negro y
la app se cerraba ("zaz pa fuera"). El log `childoflight.log` (6 intentos,
todos iguales):

```
Mali-G57 MC2 doesn't support vertexPipelineStoresAndAtomics, which is required
for Vulkan GPU emulation parity
Couldn't choose a compatible Vulkan physical device or initialize a Vulkan logical device
Unable to create graphics provider
Graphics presentation setup failed: C0000001
OnInitialize failed
```

**Causa exacta** (verificada en el código real del SDK, no de memoria):
`tools/rexauto/android/sdk/rexglue-sdk/src/ui/vulkan/vulkan_device.cpp`,
`CreateIfSupported(with_gpu_emulation=true)` exigía a lo duro
`independentBlend`, `fragmentStoresAndAtomics` y `vertexPipelineStoresAndAtomics`
(líneas 114–123, `return nullptr` sin alternativa). El Mali-G57 MC2 (Helio G99)
NO tiene `vertexPipelineStoresAndAtomics`. Los cvars
`vulkan_require_vertex_pipeline_stores_and_atomics` /
`vulkan_require_fragment_stores_and_atomics` EXISTÍAN (líneas 25–33) pero el
código NO los consultaba ("Deprecated and ignored for parity").

**Segundo muro, descubierto antes de que el usuario lo chocara:** en
vulkan.gpuinfo.org, dos informes independientes del Mali-G57 MC2 (Woxter X70 y
TECNO KJ7, deviceID 0x90930010) dicen:

| Función | Mali-G57 MC2 |
|---|---|
| `independentBlend` | sí (el log lo confirma: pasó ese chequeo) |
| `fragmentStoresAndAtomics` | **sí** |
| `vertexPipelineStoresAndAtomics` | **no** |
| `geometryShader` | **sí** |
| `fillModeNonSolid` | **no** |

`fillModeNonSolid` también falta, y su chequeo (con cvar, por defecto `true`)
era el siguiente `return nullptr` tras relajar el de vertex stores — exactamente
lo que le pasó a Gears of War 2 (tras relajar vertex stores, se paró en
`fillModeNonSolid`). Se relajaron los dos en el mismo parche, no en dos rondas.

**Lo que el teléfono también dijo:** la interfaz del launcher rexauto por
defecto es muy mala (fondo negro, inglés, botones grises). Rediseñada en esta
misma fase (ver § 4).

## 2. Investigación (internet + agentes)

- **Skate 3 Android v0.2.0** hizo EXACTAMENTE este fix y lo documentó:
  "Mali GPUs are no longer turned away… That requirement was never as hard as
  the check enforcing it. The emulated GPU path already handles the feature
  being missing — it routes vertex memexport through compute shaders… The
  device is accepted now, and a draw that genuinely needs it fails where it
  happens instead of at startup. On a GPU that has the feature nothing
  changes at all."
- **Gears of War 2 v0.3.8 (SDK patch 0062):** mismo fix; después chocó con
  `fillModeNonSolid` y lo relajó también. Luego corre en Poco X6 Pro
  (Mali-G615) y Dimensity 8300 (Mali-G720), "still slow".
- **Pinyon Shift (Forza Horizon 1):** con
  `vulkan_require_vertex_pipeline_stores_and_atomics=false` corre en
  Mali-G710; ningún vertex shader con memory export en el log.
- **Zandatsu:** en G99 el renderer NATIVO pierde el dispositivo a los ~4 s
  (causa desconocida) y el automático pasa al EMULADO. Ir al nativo como
  "solución" va contra la única evidencia disponible. El emulado es el refugio.
- **Legends-Recomp issue #7:** mismo GPU, cero comentarios, sin fix.
- **NFSMW 0.8.0:** la premisa "fix G99 en modo emulado" era incorrecta —
  NFSMW usa renderer nativo y su fix Mali fue quitar el requisito `shaderInt64`
  (v0.3.7) + vía OpenGL ES 3.2 (0.8.0). No aplica aquí.
- **ReXGlue upstream v0.10.1 (2026-11-06):** solo 4 cambios, nada sobre Mali
  ni estas features.
- **Xenia (origen del código):** el traductor SPIR-V tiene una rama explícita
  para cuando falta la feature ("If false, vertex memory exports use a compute
  shader instead"). El rechazo en arranque es un chequeo añadido en la capa
  ReXGlue, no una necesidad del traductor.

## 3. El arreglo (parche SDK `rexglue-sdk-v0.10.0-mali-vertex-stores.patch`)

Nuevo parche en `tools/rexauto/android/patches/` (registrado en
`tools/android_sdk.sh`, que ahora aplica 10 parches). Dos archivos:

**`src/ui/vulkan/vulkan_device.cpp`:**
- El cvar `vulkan_require_vertex_pipeline_stores_and_atomics` pasa de
  `true` ("Deprecated and ignored") a **`false` por defecto**, con descripción
  nueva. El chequeo duro ahora lo consulta (como ya hacían geometryShader /
  fillModeNonSolid): si el cvar es `true` y falta la función → `nullptr`; si
  es `false` → WARN y se sigue con el fallback a compute shaders.
- El cvar `vulkan_require_fill_mode_non_solid` pasa de `!REX_PLATFORM_MAC`
  a **`false` por defecto**, mismo patrón: si falta la función → WARN y se
  sigue con el fallback a solid fill (el `pipeline_cache.cpp` ya maneja la
  ausencia por pipeline; un juego 2D nunca usa modos no sólidos).
- `XE_UI_VULKAN_FEATURE(...)` (línea ~640) ya era seguro: solo pide la función
  si el hardware la tiene (`enabled = supported`).

**`src/graphics/vulkan/command_processor.cpp` (`SetupContext`):**
- El chequeo duro de vertex stores ahora consulta el cvar; si es `false` →
  WARN y se sigue (el fallback ya existía: línea ~798 añade el stage COMPUTE
  cuando falta la función; `spirv_translator.h:672` traduce a compute cuando
  falta).
- NO se tocó: el chequeo de `fragmentStoresAndAtomics` (Mali SÍ la tiene),
  el chequeo de `geometryShader` (Mali SÍ lo tiene), ni el chequeo por draw
  de vertex memexport (línea ~3635: un draw que use vertex memexport falla
  individualmente, no la app entera — comportamiento correcto).

**En un GPU que SÍ tiene las funciones no cambia nada:** se siguen pidiendo y
usando igual que antes. La relajación solo se nota cuando el hardware no las
tiene.

**Lección de proceso (parche limpio):** el primer `git diff` del parche salió
CONTAMINADO con dos hunks de los 9 parches Android ya aplicados (el
`#include <atomic>` y el bloque diagnóstico de memexport en
`command_processor.cpp`), porque `git diff` compara contra el HEAD upstream y
los 9 parches viven sin commitear. Se regeneró correctamente con un commit
temporal del estado 9-parches como base + `git diff` + `git reset --mixed`, y
se verificó end-to-end (checkout → 9 parches → `git apply --check` →
`git apply` → SHA-256 idénticos a los archivos de trabajo).

**Riesgo residual (medible en el teléfono):** si algún shader de Child of
Light usa vertex memexport, esos draws fallarán (error por draw en el log) en
vez de tumbar la app. Es improbable en un juego 2D (Pinyon Shift no encontró
ningún vertex memexport en FH1), y el log del teléfono lo dirá.

## 4. Rediseño de la interfaz (misma APK v1.2)

La home del launcher rexauto por defecto → home estilo el título, en
español, hecha por un agente dedicado y verificada por mí (archivos, recursos
y cableado; la prueba visual es en el teléfono):

- **Archivos:** `SetupActivity.java` (675 → 886 líneas con el auto-import,
  ver § 5), `res/values/strings.xml` (67 strings, todo en español, incluidos
  los diálogos de gráficos y registros), `res/values/styles.xml`
  (fondo #10131D, acento turquesa #75D6CE), 4 drawables XML nuevos
  (`aurora_hero`, `btn_primary`, `btn_secondary`, `card_status`).
- **Diseño:** héroe con aurora en degradado (sin imágenes con copyright),
  título a 30sp, tarjeta de estado ("✓ Juego instalado" / "Falta importar el
  juego"), botón **▶ JUGAR** grande (56dp, degradado turquesa → azul hielo),
  secciones "IMPORTAR EL JUEGO" / "AJUSTES" / "REGISTROS Y DATOS". Paleta:
  azul hielo #8CCBEB, azul lavanda #6578B8, lavanda #A99ACD, turquesa #75D6CE,
  plata #DCE8F5, fondo #10131D. **Nada de amarillo/dorado** (el ámbar
  0xFFFFD27F del estado y el rojo #8B1A1A del tema, fuera; errores en rosa
  suave #E8A0B0).
- **Funciones intactas:** los 7 botones siguen haciendo lo mismo (JUGAR,
  elegir juego, elegir carpeta, gráficos, driver, registros, quitar archivos).
- **Botón "Controlador GPU" (Turnip/Adreno): NO aparece en Mali**
  (`GpuDriver.isAdreno() ? VISIBLE : GONE` en `refresh()`). En Adreno aparece
  como antes. La función sigue en el código.
- **Bug preexistente corregido de paso:** en `fail()`, `refresh()` se llamaba
  DESPUÉS de poner el error y lo tapaba al instante (el error nunca se veía).
  Ahora `refresh()` va primero y el mensaje queda visible.
- **No verificado visualmente** (sin emulador ni teléfono aquí): proporciones
  en 1080x2400 y contraste solo se confirman en el teléfono.

## 4b. Icono original del juego + fondo oficial en la home (v1.2 reconstruida)

El usuario pidió el icono ORIGINAL del juego (2026-11-13 12:30). El arte
estaba dentro del propio juego: el paquete `nxeart` (STFS LIVE,
`private/extracted/nxeart`) extraído con `tools/extract.py` a
`private/nxeart-out/` contiene `nxebg.jpg` (1920x1080, fondo oficial) y
`nxeslot.jpg` (420x320, carátula/tile oficial de Xbox 360). Nada de arte
inventado ni de internet: es el arte oficial que venía dentro del juego.

**Icono del launcher** (desde `nxeslot.jpg`, verificado visualmente: la
silueta de Aurora con la corona + el orbe de Igniculus arriba, logo "CHILD OF
LIGHT" abajo):
- Recorte centrado a cuadrado **320x320** (x 50..370), **sin deformar**.
- 5 densidades, redimensionado LANCZOS, PNG verificado (tamaño exacto +
  contenido real, no vacío): mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144,
  xxxhdpi 192.
- Los iconos genéricos de rexauto quedan respaldados en
  `private/rexauto-icons-backup/mipmap-*/` (nunca en el repo).
- El proyecto NO usa icono adaptativo (sin `mipmap-anydpi-v26`, sin
  `roundIcon` en el manifiesto): solo los 5 PNG. En Android 16 el launcher
  aplica su propia máscara a los PNG legacy.

**Fondo de la home** (`nxebg.jpg`, Aurora de espaldas con el pelo rojo, la
corona, la espada e Igniculus brillando ante el castillo en la niebla):
- El archivo vive como `src/main/assets/hero_bg.jpg` (463.162 B, JPEG
  original sin recomprimir).
- `SetupActivity`: el héroe ahora carga `hero_bg.jpg` con `loadHeroBg()`; si
  existe, va en un `FrameLayout` con `ImageView` `CENTER_CROP` (recorte
  centrado, sin deformar) + velo oscuro `0x66000000` para que el título
  blanco se lea sobre la ilustración. Si el asset falta, se mantiene el
  degradado aurora dibujado por código (`aurora_hero.xml`). El resto del
  héroe (título, edición, tagline) no cambia.
- La miniatura `cover.png` de 72dp (`loadCover()`) queda como estaba (no
  existe `cover.png`, devuelve null): con el fondo oficial ya no hace falta.

**Consecuencia: la v1.2 se reconstruyó** (mismo versionCode 2 / versionName
1.2, nuevo contenido: icono + fondo). La primera v1.2 subida a Drive
(SHA `97ce6f06…`) queda sustituida por la reconstruida (ver tabla § 5).
El usuario aún no había instalado la primera v1.2, así que no hay dos
v1.2 circulando en su teléfono.

## 5. La APK v1.2

| Dato | Valor verificado |
|---|---|
| Archivo | `ChildOfLight-recomp-v1.2.apk` |
| Tamaño | **24.557.075 B (~23,4 MiB)** |
| SHA-256 | **`83db499587d6730de99b80e9ef838f9dfda277a0d3cf428159148da6d998a64b`** |
| Paquete | `com.rexauto.port.childoflight`, versionCode 2, versionName 1.2 |
| Firma | keystore corazón, alias `childoflight<3`, `apksigner verify` OK (cert SHA-256 `3768a83d…`, mismo que la v1.1) |
| Build | **BUILD SUCCESSFUL en 42s** (log `analysis/fase3-build-2.log`, 45 tareas, 7 ejecutadas, 38 up-to-date), exit 0 — tercer intento; ver "Lección de proceso" abajo |
| Drive (PRIVADO, sin compartir) | Carpeta `Child-of-Light-recomp` — https://drive.google.com/file/d/1zyCauf1H9Iqs49K7lz7bfTEKhjmoYI2w/view?usp=drivesdk — mismo enlace que la primera v1.2 (contenido actualizado en el mismo archivo, ID sin cambios) |
| Contenido | `libmain.so` 55.277.616 B, `librexgpu-xenos.so` 4.522.600 B, `librexruntime.so` 8.620.280 B, `libc++_shared.so`, `assets/xbox360_titles.json`, **`assets/hero_bg.jpg` 463.162 B** |
| Icono | los 5 PNG de `res/` son **píxel a píxel idénticos** a los iconos nuevos generados desde `nxeslot.jpg` (48/72/96/144/192) — verificado por comparación de píxeles, no solo por tamaño |

**Lección de proceso (reconstrucción de la v1.2, 3 intentos):** el primer
intento murió en `:app:compressReleaseAssets` con `Java heap space` y el
segundo igual, pese a subir el heap del daemon a 1536m y a marcar los JPG
como `noCompress`. La causa real era otra: el build full matado a mitad
había dejado `game.stfs` (1,5 GB) en `app/build/intermediates/assets/`, y el
siguiente build intentaba comprimirlo. Al borrar `intermediates/assets/` +
`intermediates/compressed_assets/`, el tercer intento salió verde en 42 s.
El script `run-release-build-full.sh` ahora limpia esos intermediates antes
de empezar y en su trap de salida, para que un build matado no envenene el
siguiente. (El `noCompress += "jpg"` y el heap 1536m se quedan: correctos y
útiles, aunque no eran la causa.)

**El parche está DENTRO de la APK (verificado, no de palabra):** las cadenas
"compute-shader memexport fallback", "solid-fill fallback" y
"vulkan_require_vertex_pipeline_stores_and_atomics=false" aparecen 1 vez cada
una en `librexruntime.so` de la v1.2 y **0 veces en la v1.1**. Además
`libmain.so` y `librexruntime.so` crecieron levemente frente a la v1.1
(recompilados con el SDK parcheado).

**⚠️ NO probada en ningún teléfono.** Build exitoso ≠ funciona: falta instalar
encima de la v1.1, importar el juego y ver si llega al menú. El riesgo
Mali-G57 DEVICE_LOST (~4 s, Zandatsu) sigue abierto y solo se prueba en el
teléfono real.

## 6. DLCs (llegaron, verificados, NO montados todavía)

El usuario trajo los 7 DLCs (2026-11-13 12:17–12:24) como .7z por chat.
Verificados por mí uno por uno en `~/workspace/col-recomp/private/dlc/`:
todos STFS LIVE válidos (magic "LIVE", Title ID [redactado], carpeta 00000002),
tamaños y MD5 exactos según `private/dlc/README.md`:

| DLC | Tamaño | MD5 |
|---|---|---|
| The Golem's Plight | 2.834.432 B | a21e628b… |
| Stardust Pack | 69.632 B | bb2fbd72… |
| Rough Oculi Pack | 69.632 B | ed00afdf… |
| Light Pack | 5.177.344 B | 9ec94e61… |
| Dark Pack | 6.230.016 B | 55493377… |
| Faceted Oculi Pack | 69.632 B | 4ac8b8f0… |
| Tumbled Oculi Pack | 69.632 B | 94383465… |

Total 14.520.320 B (~13,8 MB). Nota: Stardust NO estaba en la lista
documentada de la investigación (se esperaba Tumbled como 7º); Tumbled llegó
después, aparte. El .txt "Vimm's Lair" de cada .7z no se guardó.

**El montaje de DLC en ReXGlue NO tiene precedente** (informe §4.9/§6: diseño
pendiente) y NO es bloqueador del arranque. La variante full los incluirá
cuando el montaje esté diseñado y el juego arranque. No se retrasó el arreglo
GPU por los DLCs.

## 7. Variante full (privada, con el juego adentro) — EN CONSTRUCCIÓN

Variante aprobada por el usuario (2026-11-13 12:13, "Entonces hagámoslo
igual :3"): como la APK de Rayman Origins v0.4.0 (1,16 GB, todo incluido).
Solo para su teléfono y su Drive privado; el juego NUNCA entra al repo
público (ni STFS, ni extraído, ni como asset en ningún commit) y esta
variante se construye SOLO en local (el juego no puede ir a CI).

**Cómo se construye** (`tools/run-release-build-full.sh`):
- Gradle construye la APK normal con `-PrexVersionCode=2
  -PrexVersionName=1.2-full -PrexFull=true` (el flag `FULL` de
  `BuildConfig` + `noCompress stfs` ya estaban en `build.gradle.kts`).
- **El STFS NO pasa por Gradle como asset**: `:app:compressReleaseAssets`
  lee CADA asset entero en memoria y con `game.stfs` (1,5 GB) muere siempre
  con `Java heap space` (ni `noCompress` ni heap 1536m lo salvan; es el
  mismo fallo que tumbó dos intentos de la v1.2 con un `game.stfs` residual).
  El STFS se **inyecta después** en el ZIP como entrada `assets/game.stfs`
  STORED (sin comprimir, por streaming en bloques de 1 MB, sin cargarlo en
  RAM), luego `zipalign -f 4` y re-firma con el mismo keystore corazón
  (alias `childoflight<3`). El launcher lee `assets/game.stfs` igual que
  si lo hubiera empaquetado Gradle: en el primer arranque lo copia a
  `Android/data/<pkg>/files/game.stfs` y lo importa con el mismo código
  STFS del picker manual (`importStfsFile()`), sin paso manual.
- **Trampa del primer script (corregida):** el trap usaba rutas relativas
  y el script ya había hecho `cd tools/rexauto/android`, así que el `rm`
  no borraba nada y el `game.stfs` quedaba en `src/main/assets/` tras matar
  el build. Ahora todo con rutas absolutas + limpieza de
  `intermediates/assets/` antes de empezar.

(Por completar cuando termine el build: tamaño, SHA, enlace privado,
verificación local — incluido el MD5 round-trip del asset extraído de la
APK.)

**Resultado: ✅ CONSTRUIDA Y VERIFICADA** (log `analysis/fase3-build-full-1.log`,
`FULL_BUILD_EXIT=0`, tercer intento — ver lecciones abajo):

| Dato | Valor verificado |
|---|---|
| Archivo | `ChildOfLight-recomp-v1.2-full.apk` |
| Tamaño | **1.610.020.800 B (~1,50 GiB)** |
| SHA-256 | **`912bd00ce6a73e5661fd17a2d95ef1cfaa29cf99c74d541f444c4beb4bcc79f1`** |
| Paquete | `com.rexauto.port.childoflight`, versionCode 2, versionName **1.2-full** |
| Firma | keystore corazón, alias `childoflight<3`, **`apksigner verify`: VERIFIES** (cert SHA-256 `3768a83d…`, mismo que la v1.2) |
| Build | Gradle **BUILD SUCCESSFUL en 41s** + inyección + zipalign + re-firma |
| Contenido | `libmain.so` 55.277.616 B, `librexgpu-xenos.so` 4.522.600 B, `librexruntime.so` 8.620.280 B, `assets/hero_bg.jpg`, **`assets/game.stfs` 1.585.451.008 B (STORED, entrada única)**, `META-INF/MANIFEST.MF` presente |
| **MD5 round-trip** | el `game.stfs` extraído de la APK por streaming da **`ad88061a06fba28e3434945f37cb3d57` — EXACTO al STFS original** (el juego dentro de la APK es byte a byte el mismo) |
| Drive (PRIVADO, sin compartir) | Carpeta `Child-of-Light-recomp` (https://drive.google.com/drive/folders/1qJNEBJz_KfcvtzMb4PFd5WOYvMd2O2sZ) — **en 16 partes** (la subida directa de 1,6 GB falló con HTTP 500 tras 11,5 min y no dejó ningún archivo parcial): `ChildOfLight-recomp-v1.2-full.apk.part00` … `.part15` (100 MiB c/u, la última 37.156.800 B) + `ChildOfLight-recomp-v1.2-full-MD5SUMS.txt` + `ChildOfLight-recomp-v1.2-full-README.txt` con las instrucciones de rearmado (mismo método que el diagnóstico de KOF 2012). **Verificado en Drive: 19 archivos en la carpeta** (16 partes + MD5SUMS + README + la v1.2 normal), todos `shared: false`, tamaños exactos. MD5 de la APK rearmada: `96b70e64f1be04df413ae2e991ef6888` (verificado en local: `cat part* | md5sum` = mismo valor). |

**Lecciones de proceso (3 intentos del build full):**
1. Intento 1: `:app:compressReleaseAssets` → `Java heap space` (el STFS
   como asset de Gradle). → Solución: inyección post-build (arriba).
2. Intento 2: `apksigner sign` → `java.io.IOException: No space left on
   device` y la APK quedó sin `META-INF/MANIFEST.MF`. Causa: apksigner
   escribe su APK temporal en `java.io.tmpdir` = `/tmp` (tmpfs de 512 MB);
   `$TMPDIR` solo NO lo redirige (la JVM lo ignora aquí). → Solución:
   `JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$RECOMP/private/tmp"`.
3. Además el script no tenía `set -e`: el intento 2 terminó con
   `FULL_BUILD_EXIT=0` y copió una APK corrupta como "final". Ahora
   `set -eu` + verificación de entrada única + `apksigner verify` antes de
   copiar. **La APK corrupta del intento 2 (SHA `568917d3…`) se borró; no
   existe en Drive.**

**Espacio que necesita el teléfono:** APK 1,6 GB + copia temporal del STFS
1,5 GB + juego extraído 1,5 GB ≈ **5 GB libres transitorios**; en
régimen ~3,1 GB (APK + juego instalado). Los DLCs NO van en esta primera
full (el montaje aún no está diseñado); se añadirán cuando el juego
arranque.

## 8. Qué queda por probar en el teléfono (manos del usuario)

1. Instalar la v1.2 encima de la v1.1 (misma firma, mismo paquete).
2. Abrir: ¿la home nueva se ve bien? ¿aparece el botón "Controlador GPU"?
   (NO debe aparecer en Mali.)
3. Importar el juego (STFS) y pulsar JUGAR: ¿llega al menú? ¿se ve? ¿suena?
4. Si arranca: ¿DEVICE_LOST a los ~4 s? ¿fps? ¿controles táctiles?
5. Traer el log `childoflight.log` si algo falla (sobre todo si aparece
   "Vertex shader memexport draw encountered" o "unresolved export
   destinations").

**Variante full (cuando la pruebe):** descargar las 16 partes + README,
rearmar con `cat part* > apk` y verificar MD5 `96b70e64…` (tamaño
1.610.020.800 B); instalar encima de la v1.2 (misma firma); al abrir, el
juego se importa solo — ver "Copiando el juego incluido" y luego
"✓ Juego instalado" — y pulsar ▶ JUGAR sin importar nada a mano. Hacen
falta ~5 GB libres. Si la importación automática falla, el log dirá por
qué (y el camino manual sigue ahí).

## 9. Pendiente (después de que el juego arranque)

- Montaje de DLCs (7 packs, diseño pendiente, sin precedente en ReXGlue).
- ReXGlue v0.10.0 vs v0.10.1 (sin reconciliar; v0.10.1 no toca este tema).
- `detect_setjmp.py`; parche 0014 (fences ARM64); perfil Mali-G57 completo
  (docs/RENDIMIENTO.md); embellecer el código generado poco a poco (el usuario
  lo pidió con amor: renombrar/documentar por sistemas, sin inventar los
  nombres originales de la editora).
- Publicar el andamiaje del port en el repo (`autoports/` + `recomp/ppc/` hoy
  solo viven en esta VM); actualizar el README del repo ("Estado: Fase 0" ya
  no es verdad).

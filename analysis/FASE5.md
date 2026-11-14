# Fase 5 — v1.4: port 100% oficial, sin importar nada, < 2 GB, y rendimiento

**Fecha:** 2026-11-13. **Base:** v1.3, HEAD `205b41c`. **Coordinador + 3 workers**
(APP, VFS/FLUJO/CARGA, RENDER) + investigación de otros ports
(`analysis/FASE5-PORTS-OTROS.md`). **No se pushea nada en este documento:
el push lo hace el parent por navegador.**

**Requisito del usuario (2026-11-13 15:02):** port 100% oficial — instalar →
abrir → jugar, sin importar nada, nunca. Total instalado = tamaño de la APK
(< 2 GB; datos ~1,5 GB + motor ~24 MB = objetivo ~1,6 GB). Rendimiento y
texturas: prioridad máxima ("va demasiado lento y las texturas tienen muchos
errores"). Proceso ordenado: todos los agentes verifican todo, código por
código, e investigar en internet cómo hicieron jugables los demás ports.

**Informes de los workers (fuentes de este documento):**
`analysis/FASE5-app.md`, `analysis/FASE5-vfs-carga.md`,
`analysis/FASE5-render.md` (cada uno con sus verificaciones y sus citas
archivo:línea). Este documento sintetiza y verifica lo de los tres; lo que
aquí se afirma sin citar worker fue verificado por el coordinador contra el
código real.

---

## 0. Veredicto en una página

| Bug / requisito | Causa exacta | Estado en v1.4 |
|---|---|---|
| **5 — tamaño 3,26 GB** | El juego vivía dos veces (APK 1,68 GB + copia extraída 1,58 GB) | **Resuelto por diseño**: el juego se lee desde dentro de la APK (STFS montado por VFS, sin extraer). Total objetivo ~1,6–1,7 GB. Ver §1. |
| **4 — pantalla de importar** | La auto-copia mostraba el picker durante la copia | **Eliminada**: en full no hay pantalla de importar ni picker en ningún momento. Ver §1. |
| **2 — "Cargando..." varios minutos** | La caché persistente de shaders **se borraba en cada arranque** en Android (bug Bionic `"a+"`) + la caché del driver nunca se persiste | **Arreglado el borrado** (rewind tras cada `"a+b"`); la 2ª entrada debe ser mucho más rápida. El `VkPipelineCache` del driver sigue sin persistir (riesgo Mali). Ver §3. |
| **Lento (CPU 96%, mutex 8%+6,5%)** | `WaitMultiple` hacía polling (trylock de todos los handles + sleep 1 ms por waiter) + cada commit parseaba `/proc/self/maps` + hilo principal sin afinidad | **Arreglados los tres** (P0 #1a/#1b/#4): `WaitMultiple` bloqueante, `mprotect` primero, afinidad a núcleos grandes. Ver §2. |
| **1 — título sin llenar** | **NO es el presentador** (exonerado por tres vías): el juego dibuja el fondo del título solo en el 53% superior de su propia imagen de salida | **Sin arreglo en v1.4** (mecanismo dentro del guest, sin identificar). Añadido log de diagnóstico que decide el caso en el teléfono. Ver §4. |
| **6 — banda blanca / texturas** | Hipótesis nº 1: draws con vertex memexport que fallan individualmente en Mali; nº 2: fallback DXT (4× VRAM/banda); nº 3: frame-skip por placeholders | **Sin causa señalada** (falta el log del teléfono). Frame-skip desactivado por defecto (eso también ataca los ~6 fps). Ver §5. |
| **3 — se salta intro + menú** | (a) Las películas WMV3 **no tienen decodificador** en ReXGlue (`XamLoadExtraAVCodecs2` es un stub); (b) el menú que se salta no tiene causa VFS encontrada | **La intro SEGUIRÁ saltándose en v1.4** (falta un decodificador WMV3/WMA: trabajo grande, separado). El menú sigue sin explicación. Ver §6. |
| **7 — `plan_logo.es.srt` no se encuentra** | **El fichero no existe en ningún sitio** (ni suelto ni en ningún `.ipk`) | **Pista falsa**: el `0xc000000f` es ruido esperado. Los .srt que sí existen los lee el propio el motor. Ver §6. |

**Lo que NO trae v1.4 (decidido con motivo, ver §9):** decodificador WMV3/WMA,
`VkPipelineCache` persistente, register-as-local (re-recomp completo),
transcodificación BC→ETC2, parcheo de las constantes 16/9–9/16, renderer
nativo. **Nada de v1.4 está probado en el teléfono** — la puerta es CI + la
prueba del usuario (§12).

---

## 1. Port 100% oficial: el juego se lee desde dentro de la APK (P0 #10)

### 1.1 El circuito completo (tres workers, una sola cadena)

Ningún port conocido lee el juego desde dentro de la APK (verificado en
`FASE5-PORTS-OTROS.md` §6: el patrón universal es "APK pequeña + juego del
usuario en almacenamiento"). Es trabajo nuevo, pero el SDK ya traía la pieza
clave: `StfsContainerDevice` monta un STFS como dispositivo VFS sin extraer
(y `ContentManager::InstallContent` ya lo usaba — pero luego **copiaba** los
archivos fuera; la copia es el paso que sobra, no el montaje).

```
PortApp.onCreate (cada arranque)
  → StfsAsset.ensureOpen(getAssets())            [APP: StfsAsset.java]
  → StfsAsset.setAssetFd(fd, startOffset, length) (JNI, dup() inmediato)
      → rexport::StfsAssetFd()                   [APP: stfs_asset_jni.cpp]
  → android_main.cpp, rama asset: (:561-586)     [VFS: GAP 1 + GAP 2]
      → rex::filesystem::SetStfsAssetSource(fd, offset, length)
  → Runtime::SetupVfs (runtime.cpp:317) monta StfsContainerDevice
    en \\Device\\Harddisk0\\Partition1 + enlaces game: / d:
  → el juego lee d:\... desde dentro del STFS (assets/game.stfs, STORED)
```

**Dos gaps de interfaz detectados por el coordinador y cerrados por el
worker VFS** (verificados por grep antes y después): (1) nadie llamaba a
`SetStfsAssetSource` — el JNI de APP guardaba el triple en sus propios
globales y el SDK lo leía en los suyos; el puente vive ahora en
`android_main.cpp:575-576`. (2) `android_main` pasaba el marcador
`asset:game.stfs` como `--game_data_root`, que `ConstructRuntime` rechaza
por no ser directorio; ahora la rama `asset:` (:561) no lo pasa (raíz
vacía; el montaje se decide por la presencia del triple fd) y registra el
fd/offset/length con `ALOGI` (o `ALOGE` si falta el triple).

### 1.2 Cambios por worker

**APP** (`analysis/FASE5-app.md`): `SetupActivity.java` — borrada la
auto-importación (`maybeAutoImportBundledGame`, `importStfsFile`,
`hasBundledGameAsset`, cadena `status_bundled_copy`); el router full
escribe el marcador y lanza el juego sin UI; el picker manual queda solo
para la variante normal/dev. `GameFiles.java` — `hasValidGameRoot()`
entiende `asset:game.stfs` (sin esto, la 2ª entrada rebotaría en bucle
MainActivity↔SetupActivity). Nuevos `StfsAsset.java` (`verifyUsable`:
openFd + magia CON/LIVE/PIRS + Title ID en 0x360; `ensureOpen` idempotente,
nunca lanza) y `PortApp.java` (`Application.onCreate` entrega el fd en
cada arranque, antes de cualquier código nativo — un fd no sobrevive a la
muerte del proceso). Nuevos `stfs_asset.h` / `stfs_asset_jni.cpp` + 1 línea
en `CMakeLists.txt`. `AndroidManifest.xml`: `android:name=".PortApp"`,
`SetupActivity` a `sensorLandscape`. Decisión documentada: JNI con fd en
vez de `AAssetManager` en nativo, porque SDL no pasa ningún AssetManager al
nativo (cero menciones en `org.libsdl.app`).

**VFS** (`analysis/FASE5-vfs-carga.md`): nuevo `StfsDataFile` (rango
contiguo de bytes: fichero de host entero o trozo fd+offset+length con
`pread` en bucle, seguro ante EINTR; `OpenFd` valida el trozo contra
`fstat`); ctor fd de `StfsContainerDevice` (solo STFS de un fichero, sin
SVOD multi-fichero desde fd; toma la propiedad del fd); `OpenFiles()`
reescrita (rama fd / rama ruta); todos los `Seek`+`fread` de `ReadSVOD`,
`ReadEntrySVOD`, `ReadSTFS` y `GetBlockHash` pasaron a `ReadAt`;
**coalescido en `ReadSTFS`** (el código original metía un registro por
cada bloque de 4 KiB sin fusionar: un `.ipk` de 657 MB construía ~160k
registros y cada lectura aleatoria escaneaba esa lista linealmente);
`SetupVfs` reestructurada (rama STFS-asset primero, `:317-332`, con
fallback al montaje host-path si el montaje falla); `ConstructRuntime`
captura `stfs_asset_source` al principio y permite raíz vacía solo con
fuente STFS (la comprobación host de `default.xex` se salta cuando hay
fuente STFS: el XEX se carga desde el contenedor montado vía
`LoadXexImage("game:\\default.xex")`); `stfs_asset_source.cpp` y
`stfs_data_file.cpp` añadidos a la lista explícita de
`src/filesystem/CMakeLists.txt`.

**Verificaciones previas exigidas (a)(b)(c), hechas por el worker VFS:**
(a) el juego no escribe en `d:\` (saves por ContentManager, raíz en
`user_data_root`, `kernel_state.cpp:75`; `allow_game_relative_writes` es
`false` por defecto y `d:\` ya era solo-lectura en v1.3 — el STFS de
solo-lectura es idéntico en comportamiento); (b) coste por lectura = un
`pread` por tramo contiguo (con coalescido); (c) el `.ipk` con acceso
aleatorio funciona igual sobre STFS que sobre disco (`XFile::Read` →
`ReadSync` con offset/longitud arbitrarios).

### 1.3 Tamaño esperado

| | v1.3-full (medido) | v1.4-full (objetivo) |
|---|---|---|
| Aplicación (APK) | 1,68 GB | ~1,68 GB (igual: motor + STFS dentro) |
| Datos | 1,58 GB (juego extraído) | Solo guardados/ajustes/logs (MB) |
| **Total** | **3,26 GB** | **~1,6–1,7 GB** |

La variante normal queda como variante de desarrollo (24 MB + importar una
vez, ~1,6 GB). **Límite honesto:** el tiempo de montaje y el
`.ipk`-sobre-STFS no están medidos en tiempo real (sin build de APK ni
teléfono aquí) — solo razonados desde el código. Si montar el STFS desde
el asset resultara inviable en la práctica, el bloqueador estaría en
`android_main.cpp:575` (puente) o en `stfs_container_device.cpp:138`
(rechazo multi-fichero), y la alternativa real sería volver al montaje
host-path con extracción (3,26 GB), que el usuario ya rechazó.

---

## 2. Rendimiento: la CPU era el cuello de botella, no la GPU

Perfil real del teléfono (v1.3): Main XThread 96%, AK::IOThread 94%,
`pthread_mutex_unlock` 8%, `__kernel_clock_gettime` 7,4%,
`pthread_mutex_trylock` 6,5%, `nanosleep` 5,4%, `sched_yield` 1,5%, GPU
Commands solo 14%. La firma es idéntica a la de Rayman Origins — y los
tres arreglos P0 son los mismos que Origins/Gears/CrownPark midieron.

### 2.1 P0 #1a — `WaitMultiple` bloqueante (RENDER)

`PosixConditionBase::WaitMultiple` (`SDK/src/core/threading_posix.cpp`,
versión anterior) hacía, por cada waiter y en bucle: `pthread_mutex_trylock`
de TODOS los handles del conjunto, comprobar, y `sleep_for(1 ms)`. Con
decenas de multi-waiters (audio, hilos del guest) eso es un núcleo entero
de tráfico trylock/clock por waiter. El path de un solo handle sí
bloqueaba bien; solo el multi-handle hacía polling.

**Implementado (código propio, reimplementado desde la descripción):**
canal de wake-up compartido `MultiWaitChannel` (mutex + condition_variable
+ contador de generación `uint64_t` + contador atómico de waiters) y
`WakeMultiWaiters()` (no-op barato si nadie está en un multi-wait);
`PosixConditionBase::NotifyAll()` (`:500-505`) en los 7 caminos de
señalización (`Event::Signal`, `Semaphore::Release`, `Semaphore::post_execution`,
`Mutant::Release`, `Timer::Signal`, `Thread::Terminate`, salida de
`ThreadStartRoutine`); `WaitMultiple` reescrito (`:383-486`): lee la
generación ANTES de comprobar los handles (ninguna señal se pierde), duerme
en el canal con predicado hasta que algún handle señalice o venza el plazo;
wait-all bloquea en orden de dirección y consume todas las señales con los
bloqueos puestos; wait-any comprueba cada handle bajo su propio bloqueo.
Efecto medido en Origins con el mismo mecanismo: audio worker 73% → 7% de
un núcleo. **Licencia:** `SDK/LICENSE` es estilo BSD-3 (Copyright 2026 Tom
Clay); Origins es GPL-3.0 — **no se copió ninguna línea de sus parches**
(los diffs 0006/0005 se leyeron completos para entender el mecanismo).

### 2.2 P0 #1b — `mprotect` antes de parsear `/proc/self/maps` (RENDER)

`AllocFixed` (`SDK/src/core/memory_posix.cpp`): cada `kCommit` sobre una
reserva existente hacía `mmap(MAP_FIXED_NOREPLACE)` → EEXIST →
`IsRangeFullyMapped()` (abrir + parsear `/proc/self/maps` entero, línea por
línea, por commit) → `mprotect`. El espacio del guest se reserva por
adelantado, así que casi cada commit pagaba el paseo completo por maps.

**Implementado:** fast path Linux (`:333-355`): para `kCommit` con
dirección y longitud alineadas a página, probar `mprotect` PRIMERO
(`mprotect` falla con ENOMEM salvo que todo el rango esté ya mapeado, así
que intentarlo es seguro; en caso de fallo se sigue al path `mmap`);
path EEXIST (`:385-395`): `mprotect` directo en vez de
`IsRangeFullyMapped()` (un rango parcialmente mapeado falla con ENOMEM
exactamente igual que antes; semántica idéntica, sin el parseo);
`IsRangeFullyMapped` **eliminado** (era su único uso). `Protect()` /
`QueryProtect()` no se tocaron (el path de faltas usa el mapa cacheado).
La invalidación de la caché de maps sigue en todos los caminos de éxito.

**Parche obsoleto:** `tools/rexauto/android/patches/rexglue-sdk-v0.10.0-android-perf.patch`
contenía el `AllocFixed` antiguo con `IsRangeFullyMapped`. **Regenerado en
esta ronda:** eliminado el hunk EEXIST obsoleto y añadida una cabecera que
documenta la divergencia (Fase 5, v1.4) y que nada lo aplica en el build
(Gradle compila `sdk/rexglue-sdk` tal cual; es archival). Verificado con
`git apply --stat` (8 ficheros, 186 inserciones, parsea limpio).

### 2.3 P0 #4 — Afinidad de hilos (RENDER)

Helper `GetBigCoreAffinityMask()` (`SDK/src/ui/rex_app.cpp:466-590`, solo
Android): lee `/sys/devices/system/cpu/online`, clasifica por
`cpu_capacity` (o `cpuinfo_max_freq` si no existe — nunca se mezclan), toma
las CPUs con el valor máximo, devuelve 0 si el dispositivo es homogéneo o
no se puede determinar, e intersecta con la afinidad real del proceso
(`sched_getaffinity`) para no pinchar fuera del cpuset permitido. Nada se
asume del SoC (el G99 es 2×A76 + 6×A55, pero otro teléfono puede diferir).
Llamada en `ReXApp::LaunchModule` (`:623-630`): después de
`PrepareModuleLaunch()` y antes de `Resume()`, pincha el hilo principal
del guest a los núcleos grandes + log `Pinning guest main thread to big
cores (affinity mask 0x{:X})`. Solo el hilo principal; el resto sigue
migrando libremente. Verificado funcionalmente contra un sysfs falso: 5/5
casos (split big/little, homogéneo→0, fallback por frecuencia, `online`
malformado→0, intersección con la afinidad del proceso).

### 2.4 P1 #7 — `clear_memory_page_state=false`: YA ESTABA

Verificado en `android_main.cpp:600` (antes `:568` en la numeración del
worker). No hizo falta ningún cambio. Queda pedir la medida de re-subidas
por frame en el próximo perfil (Gears: 4,6 → 9,4 fps a 222 MHz fijos;
render passes 1.600 → 155).

### 2.5 Lo que NO se tocó de rendimiento

- `Clock::UpdateGuestClock` (try_lock por consulta de tiempo) y
  `XmaContext::Block(bool poll)` (espera XMA con polling propio): menores,
  no tocados. El 94% de AK::IOThread encaja con girar en esperas XMA/audio
  — el arreglo de `WaitMultiple` lo cubre si esas esperas pasan por
  multi-handle; si alguna espera XMA es de un solo handle con polling
  propio, queda como residuo a medir en el próximo perfil.
- El hilo de comandos GPU ("GPU Commands" 14%) vive dentro del plugin y no
  es alcanzable desde `LaunchModule`; pincharlo sería un cambio en el
  backend Vulkan.

---

## 3. Carga: la caché de shaders se borraba en cada arranque (P0 #3)

### 3.1 El bug Bionic `"a+"` — VERIFICADO y ARREGLADO (VFS)

Los dos ficheros de caché se abren con `"a+b"` (`pipeline_cache.cpp:422`
pipeline storage, `:505` shader storage; lo mismo en D3D12, `:255` y
`:339`). Son las 4 ÚNICAS aperturas `"a+b"` del SDK. El código hacía
`fread(header)` inmediatamente **sin rewind/seek previo**. POSIX no define
la posición inicial de lectura en `"a+"`: glibc empieza al principio, pero
**Bionic (Android) empieza al final del fichero** → el primer `fread`
devuelve 0 → la comprobación de cabecera falla → el código trunca el
fichero a 0 y escribe una cabecera nueva. **Consecuencia: en Android la
caché persistente de shaders se borraba en CADA arranque.** La carga de
"varios minutos" no era solo de la primera vez: se repetía siempre.

**Arreglo implementado** (precedente: parche 0007 de Gears):
`rex::filesystem::Seek(file, 0, SEEK_SET)` explícito tras cada apertura,
en Vulkan (`:436` y `:525`) y en D3D12 (`:269` y `:352`), con comentario
explicativo. El rewind también limpia el indicador EOF; las escrituras
siguen yendo al final porque el modo es O_APPEND. Verificado por el
coordinador: los 4 rewinds están en su sitio con el comentario correcto.

### 3.2 Lo que sigue faltando: `VkPipelineCache` del driver

**No hay `vkGetPipelineCacheData` en `pipeline_cache.cpp` (0 ocurrencias,
confirmado):** el `VkPipelineCache` del driver nunca se persiste — cada
arranque vuelve a ejecutar `vkCreateGraphicsPipelines` para todos los
pipelines guardados (el coste "mali-compiler"). Los ficheros `.xpso`/`.xsh`
ahorran solo la traducción a SPIR-V, no la compilación del driver.
**No se implementó en v1.4:** es trabajo nuevo con riesgo — MW2 desactivó
el `VkPipelineCache` persistente en Mali por deadlocks documentados del
driver. Hay que probarlo en el teléfono antes de darlo por bueno (ver §9).

### 3.3 P1 #9/#11 — pre-warm y creación asíncrona de pipelines (evaluado)

- **Creación asíncrona YA EXISTE en el SDK:** hilos `CreationThread`
  (`pipeline_cache.cpp:3531`), `creation_queue_`, `ConfigurePipeline`
  implementa el patrón estilo wivi514 (pipeline placeholder + cola real).
  Lo que sigue siendo síncrono es la **precarga de arranque**:
  `InitializeShaderStorage` crea todos los pipelines guardados de forma
  bloqueante. Hacerla asíncrona sería un cambio mayor; **recomendación:
  no tocarlo en v1.4; medir primero el segundo arranque con el arreglo
  `"a+"`.**
- **Pre-warm / siembra NO existe soporte:** `shader_storage_seed_root` =
  0 ocurrencias. Sembrar una caché templada (estilo CrownPark) sería una
  característica nueva: hacen falta ficheros semilla empaquetados en la APK
  (que no existen — nadie ha jugado el juego entero para generarlos) y el
  bug `"a+"` habría borrado cualquier semilla en el primer arranque.
  **Aplazado a después de v1.4** (cuando el usuario juegue una partida
  completa generando una caché templada en el dispositivo).

---

## 4. Bug 1 — el título no llena la pantalla: el presentador está exonerado

### 4.1 Medidas (RENDER, con Python/PIL sobre las capturas reales)

Capturas 2048×945 = 2340×1080 escalado (×1,1426); medidas ya en píxeles
reales. Título (52958): arte en rect **(210, 0, 1920, 576)**, corte nítido
de 1 fila en y≈576 (fila 503 media 73,6 → fila 505 media 3,4), pilares
x 0–184 y 2151–2340. "Cargando..." (52960): logo redondo completo centrado
en (≈1170, 540, r≈139), cortado en y≈577; texto "Cargando..." visible
debajo, en y≈990. Banner "Pulsa A": visible DEBAJO del corte, en y real
851–920. Logo el motor (52954): CORRECTO, letterbox 16:9 completo. Gameplay
(52963/52962): CORRECTO, 1920×1080 con pilares. Coordenadas del guest: el
corte en y presentada 576 ↔ y guest 384 (÷1,5; fotograma guest 1280×720).

**La disociación es la prueba:** el fondo se corta en y≈576 mientras el
texto de la UI se dibuja entero en posiciones correctas de fotograma
completo debajo del corte. Un error de viewport/scissor global del
presentador cortaría también el texto.

### 4.2 El presentador, exonerado por tres vías

`Presenter::GetGuestOutputPaintFlow` (`SDK/src/ui/presenter.cpp:857-1022`)
usa `surface_width/height_in_paint_connection_` (el tamaño real de la
superficie), no el tamaño del render target del host. Ninguna combinación
de tamaño de superficie obsoleto (incluida la vertical 1080×2340, que
daría (0,400,2340,281)) ni de aspecto de pantalla produce el rect medido
(210,0,1920,576). Con la superficie correcta el flujo da (210,0,1920,1080).
El pintado final usa viewport/scissor de swapchain completo
(`vulkan_presenter.cpp:2000-2004`) y el rect del flujo. Existe además una
guarda (`presenter.cpp:~1007-1024`): si la salida calculada queda anclada
arriba-izquierda y más pequeña que el render target, la expande — pero NO
se dispara en el caso del título (la salida está centrada en x=210, no
anclada en x≈0). **Conclusión: el recorte está dentro de la imagen de
salida del guest** (el juego dibuja el fondo del título solo en el 53%
superior del frontbuffer). El fix de Fase 4 SÍ está presente en v1.3 y
funciona (hint `android_main.cpp:519` + `setOrientationBis` en
`MainActivity.java:57-69`); que el gameplay salga correcto lo prueba. El
bug del título es otro defecto distinto, dentro del guest.

### 4.3 P0 #2 — VK_SUBOPTIMAL_KHR: el bug de Origins NO existe aquí

Rastreo completo de la cadena (RENDER; el coordinador verificó el
presentador): `vkAcquireNextImageKHR` trata `VK_SUCCESS`/`VK_SUBOPTIMAL_KHR`
igual (break, `:1577-1579`); `vkQueuePresentKHR` SUBOPTIMAL →
`PaintResult::kPresentedSuboptimal` (`:2290`), un resultado DISTINTO, no un
re-create; el consumidor `Presenter::PaintAndPresent`
(`presenter.cpp:1509-1517`) marca `kConnectedOutdated` SOLO si un present
anterior devolvió SUCCESS (transición óptimo→subóptimo) y tras una
reconexión el flag queda en false (`:1398`), así que un flujo ESTABLE
subóptimo no genera reconexiones: solo una por cada transición. **Veredicto:
no hay re-creación por frame; no se cambia nada.** Pre-transform: el
swapchain se crea con IDENTITY si está soportado, si no INHERIT
(`:1355-1358`); `currentTransform` NO se lee en ningún sitio del
presentador. Contar re-creaciones reales en el log del teléfono:
`grep -c "Created .* swapchain" childoflight.log` (cada creación registra
`VulkanPresenter: Created {}x{} swapchain`, `:1414`).

### 4.4 P0 #5 — par 16/9 y 9/16: ENCONTRADO, pero NO es el título

Búsqueda verificada en el binario real (RENDER; **verificada byte a byte
por el coordinador** en `default_image.bin`): `default.xex` comprimido: 0
coincidencias. `default.pe` (base 0x82000000): par adyacente big-endian en
offset de fichero **0x61284** — `16/9 = 0x3FE38E39` (1,77777779) en 0x61284
y `9/16 = 0x3F100000` (0,5625) en 0x61288, dentro de `.rdata`
(**dirección guest 0x82061284 / 0x82061288**), en un pool de constantes
flotantes (√2 en 0x61280, FLT_MAX en 0x6128c, 18,225 en 0x61290).
¿Lo usa el código? No se encontró consumidor directo: ninguna referencia
literal a `0x82061284`/`0x82061288` en `recomp/ppc/`; el código carga del
pool con `lis rX,-32250` + `PPC_LOAD_U32(ctx.rX.u32 + offset)`; un barrido
por flujo de datos encontró una sola candidata (`sub_82A5C830`) que no es
el par (la carga sale de r3 mientras la base está en r4). **Veredicto: el
par existe, pero no hay evidencia de que el título lo use, y las constantes
describen 16:9 mientras el gameplay ya hace letterbox correcto. No se
parchean en v1.4** — cambiarlas sin identificar al consumidor sería
disparar a ciegas. El modo de vídeo guest ya está "a juego": la ventana se
crea a 1280×720 (`rex_app.cpp:356`) y el presentador la encaja en 2340×1080
con pilares.

### 4.5 Log de diagnóstico añadido (RENDER)

`VulkanPresenter: paint geometry guest {}x{} aspect {}:{} swapchain {}x{}
output ({},{}) {}x{}` (`vulkan_presenter.cpp:1656-1700`, cuando cambia la
geometría). En el próximo arranque dirá si el frontbuffer del guest en la
pantalla del título es 1280×720 con rect completo (→ el recorte está dentro
de la imagen del guest: buscarlo en el render target del título) o si el
frontbuffer ya llega corto (→ el juego presenta un frontbuffer corto).

---

## 5. Bug 6 — banda blanca lavada / errores de textura

### 5.1 Lo medido (RENDER)

Gameplay 52963: escalón brusco de brillo en y real 576 (+42,8) con banda
brillante 238–244 en y real 583–788. Gameplay 52962: banda más suave
~686–788, brillo ~195 frente a cielo ~170, sin escalón nítido en 576. No
se puede resolver solo con capturas si es niebla/agua propia del juego o
una textura fallida. **El negro bajo el título es el Bug 1, no un error de
textura.**

### 5.2 Causas posibles, ordenadas (verificadas en código)

1. **Draws con vertex memexport que fallan en Mali (hipótesis nº 1).** En
   Mali-G57 `vertexPipelineStoresAndAtomics` es falso (perfil Mali,
   `docs/RENDIMIENTO.md` punto 3) → cualquier draw cuyo vertex shader use
   memexport FALLA individualmente
   (`SDK/src/graphics/vulkan/command_processor.cpp:3641-3647`; el de píxel
   necesita `fragmentStoresAndAtomics`, :3680+). Un draw de niebla/agua que
   dependa de memexport y falle dejaría justo una franja sin el efecto (o
   con el color base lavado).
2. **Decodificación DXT (fallback, hipótesis nº 2).** Mali-G57 no tiene
   formatos BC → DXT1/2_3/4_5/DXN se decodifican a RGBA8 con **load shaders
   de cómputo en GPU** (`texture_cache.cpp:2475` y ~:2400–2520), 4× memoria +
   4× ancho de banda de subida + un pase de decodificación por cómputo en
   cada invalidación. **Es un load shader en GPU, NO decodificación por CPU**
   (corrección al diagnóstico inicial, confirmada por los workers VFS y
   RENDER). Un fallo aquí daría texturas blancas/negras, no una banda suave.
3. **Frames con pipelines placeholder presentados/salteados (hipótesis
   nº 3).** Con `async_shader_compilation` (defecto true) +
   `vulkan_async_skip_incomplete_frames` (defecto true), cualquier frame que
   use un pipeline placeholder tiene su presentación SALTADA entera
   (`command_processor.cpp:2300-2315`). Es un mecanismo verificado de caída
   de frames (contribuye a los ~6 fps) y, en el peor caso, deja ver frames
   viejos.
4. **La niebla/agua propia del juego.** No descartable sin el log ni una
   captura de GPU.

**Discriminador (líneas a buscar en el próximo `childoflight.log`):**
`Vertex shader memexport draw encountered` / `Skipping Vulkan frame
presentation...` / líneas de fallback DXT, junto con qué textura/draw
estaba en pantalla en ese momento. **Sin ese log no se puede señalar la
textura exacta; `texture_cache.cpp` no se editó.**

### 5.3 Frame-skip desactivado por defecto (RENDER)

`--vulkan_async_skip_incomplete_frames=false` en los args built-in
(`android_main.cpp:621`; `settings.txt` puede sobreescribirlo). Con el
skip desactivado, los frames con placeholder se presentan (con el shader
placeholder) en vez de saltarse enteros. **Riesgo:** puede mostrar
flashing de placeholders durante el calentamiento de shaders; es
reversible por `settings.txt` con
`--vulkan_async_skip_incomplete_frames=true`.

---

## 6. Bug 3 + Bug 7 — intro, menú y el .srt

### 6.1 `plan_logo.es.srt` no existe (VFS; verificado por el coordinador)

Cero `.srt` sueltos en todo el árbol extraído (`find *.srt` = 0). Los 4
`.ipk` parseados (magic `50 EC 12 BA`): bundle 3611 ficheros, 0 `.srt`;
**child_of_light_x360.ipk 3344 ficheros, 61 `.srt`** (p. ej.
`r01_cin010_cs_intro_bink.es.srt` con hermanos `.de/.en/.fr/.it/.ja/.mx/.pt/.ru`,
con la ruta lógica exacta `world/common/movie/` en el registro); fullLogic
3626, 0 `.srt`; persistentloading 46, 0 `.srt`. **`plan_logo.es.srt` no
existe en ningún sitio** (la única coincidencia es `plan_logo.videod` en
`bundle_x360.ipk`); el coordinador lo confirmó con grep directo en los 4
`.ipk` (0 ocurrencias). El logo no tiene fichero de subtítulos: el
`NtCreateFile FAILED ... plan_logo.es.srt -> 0xc000000f` es **ruido
esperado** del cargador sondeando un fichero que el juego original tampoco
tiene. **El bug 7, tal como estaba formulado, era una pista falsa para el
logo.** Los subtítulos de las cinemáticas (que SÍ existen dentro de
`child_of_light_x360.ipk`) los lee el propio motor el motor con su sistema de
ficheros en capas (sueltas primero, luego IPK); el VFS solo tiene que
servir los bytes del `.ipk` correctamente, cosa que el `HostPathFile` de
v1.3 ya hace. No hay literales `movie`/`wmv`/`plan_logo`/`.srt`/`ipk` en el
código recompilado del juego: las rutas se construyen en tiempo de
ejecución, lado invitado.

### 6.2 La intro se salta: no hay decodificador WMV3 (VFS; verificado)

ffprobe sobre los `.wmv` reales: `plan_logo.wmv` = WMV3 (VC-1 Main)
1280x720 29.97 fps + WMA Pro 5.1, 9.68 s; `r01_cin010_cs_intro_bink.wmv` =
WMV3 1280x720 30 fps + 9 pistas WMA Pro (fre, jpn, ger, spa, ita, rus,
por, spa, eng), 75 s. **El SDK no tiene decodificador WMV/vídeo**: solo
menciones a decodificadores software VP6/Bink en comentarios; FFmpeg
(`libavcodec`) solo está enlazado para audio XMA. `xam_video.cpp` (44
líneas) solo implementa `XGetVideoMode` / `XGetVideoCapabilities`.
`XamLoadExtraAVCodecs2` es `REX_EXPORT_STUB` (`export_table.inc:1353`,
`xam_video.cpp:43`); `REX_STUB` (`include/rex/hook.h:52-57`, verificado por
el coordinador) solo registra un aviso y **no pone r3** — el valor de
retorno es lo que r3 tuviera (indefinido). En Xbox 360, los juegos que
reproducen WMV cargan los codecs del sistema WMV/WMA vía
`XamLoadExtraAVCodecs2`; con el stub, la inicialización del decodificador
de vídeo del juego no puede tener éxito → las películas se saltan.
**La intro SEGUIRÁ saltándose en v1.4** hasta que exista un decodificador
WMV3/WMA (FFmpeg VC-1 + WMA Pro, subida de frames a texturas,
sincronización A/V, selección de pista española entre las dos `spa`) —
trabajo grande, de otro worker. Dato útil: las cinemáticas YA tienen audio
en español en los datos de 360 (las dos pistas `spa` por .wmv; España vs
LatAm aún por confirmar cuál es cuál) — cuando el decodificador exista, el
audio español ya está ahí; no hace falta Vita para las películas. Solo el
narrador del juego es inglés en 360.

### 6.3 El menú que se salta: sin causa VFS

El título se renderiza y el juego llega al gameplay. Hipótesis sin
verificar: auto-continuación del flujo de menú / doble activación de
entrada título→menú, o el fondo animado del menú (`attract.wmv`, también
WMV3) fallando. Necesita el log completo + la comprobación del worker de
presenter (¿menú renderizado fuera de pantalla?). **No afirmar una causa
VFS para el menú.**

---

## 7. Bug 4 — la pantalla de importar, eliminada (no rediseñada)

En v1.3 `SetupActivity.continueStart()` construía siempre la pantalla
completa (título + ayuda + 2 botones del picker) y después arrancaba la
auto-importación; `setBusy(true)` solo deshabilitaba los botones, que
seguían visibles sobre el progreso. En v1.4 **la auto-importación no
existe**: la variante full no muestra ninguna pantalla — `PortApp` entrega
el fd, `SetupActivity` escribe el marcador `asset:game.stfs` y lanza el
juego. La segunda entrada va directa al juego (el marcador lo entiende
`hasValidGameRoot()`). La ruta manual de importación queda intacta solo
para la variante normal (desarrollo).

---

## 8. Lo que NO se hizo en v1.4 (decidido con motivo)

| Trabajo | Por qué no va en v1.4 | Siguiente paso |
|---|---|---|
| Decodificador WMV3/WMA | Trabajo grande (FFmpeg VC-1 + WMA Pro, frames a texturas, A/V, pista `spa`); de otro worker | Worker de vídeo/audio separado |
| `VkPipelineCache` persistente | Trabajo nuevo con riesgo de deadlock en Mali (precedente MW2: lo desactivaron) | Implementar detrás de interruptor + prueba en el teléfono |
| register-as-local (CTR/XER/LR en locales) | Exige parche en XenonRecomp + re-correr el recomp completo + re-verificar todo (280.871 escrituras a CTR/LR/XER, 74.742 referencias a `ctx.ctr`) | Siguiente paso, no a medias |
| BC→ETC2 en CPU / −2 mips | Sin cvar de mip/LOD en el backend Vulkan (grep = 0); la transcodificación es trabajo nuevo sin precedente (ningún port transcodifica a ASTC ni guarda caché persistente de texturas) | Cuando el mutex y la caché de pipelines estén resueltos |
| Parcheo de las constantes 16/9–9/16 | Par encontrado (0x82061284/0x82061288) pero sin consumidor identificado | Identificar al consumidor en el recomp |
| Renderer nativo | Trabajo de meses; en el G99 es una incógnita (Zandatsu pierde el dispositivo en este SoC) | Proyecto de fondo; el juego ya se juega sobre la ruta Xenos |
| Siembra de caché de shaders | No existe soporte (`shader_storage_seed_root` = 0) ni caché templada que sembrar | Después de v1.4, con la caché que genere el usuario jugando |
| Re-crear swapchain ante SUBOPTIMAL | El bug de Origins NO existe aquí (ver §4.3) | Contar re-creaciones en el log |

**Frase clave de Gears 2 (informe de ports):** "No se conoce ningún port
Android jugable sobre la ruta GPU genérica de ReXGlue." el título ya es la
excepción: el juego SE JUEGA sobre la ruta Xenos emulada. Eso no invalida
el camino — significa que el renderer nativo es la apuesta de fondo, no el
primer paso: primero se agotan los arreglos baratos sobre la ruta que ya
funciona.

---

## 9. Verificación (coordinador, independiente de los workers)

Verificado por el coordinador contra el código real o los ficheros reales
(todo lo demás es afirmación del worker citado, con su propia verificación
en su informe):

- **Circuito STFS completo**: archivos nuevos APP existen; nombre JNI
  coincide carácter a carácter con la declaración Java; 0 ocurrencias de
  las funciones de auto-import borradas; `GameFiles` entiende el marcador.
  GAP 1+2: `android_main.cpp:561` (rama `asset:`), `:575-576` (puente),
  includes `:54`/`:60`; `stfs_asset_source.cpp` en la lista explícita de
  `src/filesystem/CMakeLists.txt:4`.
- **Rewinds `"a+"`**: los 4 presentes con el comentario correcto (Vulkan
  `:436`/`:525`, D3D12 `:269`/`:352`).
- **Par 16/9–9/16**: byte a byte en `default_image.bin` (0x61284 =
  1,77777779; 0x61288 = 0,5625; √2 en 0x61280; FLT_MAX en 0x6128c).
- **WaitMultiple bloqueante**: `NotifyAll()` en los 7 caminos
  (`:544, :581, :589, :613, :645, :1073, :1549`); `IsRangeFullyMapped` = 0
  en el árbol; `mprotect` primero en `:326`/`:347`, directo en EEXIST
  `:390`; afinidad en `rex_app.cpp:535,623-628` conviviendo con el STFS
  (`:28, :215, :220, :285`); cvars en `android_main.cpp:600` y `:621`;
  log de geometría en `vulkan_presenter.cpp:1656-1700`.
- **`plan_logo.es.srt`**: 0 ocurrencias en los 4 `.ipk` (grep directo) y 0
  sueltos. **`XamLoadExtraAVCodecs2`**: stub verificado
  (`export_table.inc:1353`, `xam_video.cpp:43`, `hook.h:52-57` no pone r3).
- **`vkGetPipelineCacheData`**: 0 ocurrencias en `pipeline_cache.cpp`
  (confirmado dos veces).
- **Licencia**: `SDK/LICENSE` es estilo BSD-3 (Tom Clay); ningún código
  GPL-3.0 de Origins copiado (ambos workers lo verifican; los mecanismos
  se reimplementaron desde la descripción).
- **Parche obsoleto regenerado**: hunk EEXIST eliminado (con su línea
  huérfana), cabecera de divergencia añadida; `git apply --stat` parsea
  limpio (8 ficheros, 186 inserciones).
- **Auditorías de agentes**: APP — OpenCode OK (3 de 4 supuestos bugs
  refutados; Gemini falló con error de servidor; Codex no usado, fuera
  hasta 2026-12-10). VFS — OpenCode 14/14 OK, cero bloqueos. RENDER —
  OpenCode 3 rondas (threading 3 hallazgos refutados, memory NONE FOUND,
  afinidad 1 hallazgo refutado). Sintaxis `clang++` NDK: `android_main.cpp`
  PASS (VFS); `threading_posix.cpp`, `memory_posix.cpp`, `rex_app.cpp`
  (también con `-D__ANDROID__`), `vulkan_presenter.cpp` limpios (RENDER);
  `pipeline_cache.cpp` Vulkan parsea salvo 2 errores preexistentes
  (`:2977`/`:3438`, identificador generado por codegen); D3D12 no
  comprobable en Linux (lo verifica CI).
- **Lo NO verificado sin teléfono**: ningún cambio probado en dispositivo
  real; tiempo de montaje STFS y `.ipk`-sobre-STFS no medidos; conteo de
  re-creaciones de swapchain y discriminador del Bug 6 pendientes del log.

---

## 10. Archivos cambiados (para el push; nada pusheado por el coordinador)

### Nuevos

| Archivo | Contenido |
|---|---|
| `tools/rexauto/android/app/src/main/java/com/rexauto/port/StfsAsset.java` | `verifyUsable`, `ensureOpen`, JNI `setAssetFd` |
| `tools/rexauto/android/app/src/main/java/com/rexauto/port/PortApp.java` | `Application.onCreate` → `ensureOpen` en cada arranque |
| `tools/rexauto/android/app/src/main/cpp/stfs_asset.h` | `rexport::StfsAssetFd()` |
| `tools/rexauto/android/app/src/main/cpp/stfs_asset_jni.cpp` | JNI `setAssetFd` + `dup` + triple |
| `tools/rexauto/android/sdk/rexglue-sdk/include/rex/filesystem/devices/stfs_data_file.h` | `StfsDataFile` (rango de bytes: ruta o fd+offset+length) |
| `tools/rexauto/android/sdk/rexglue-sdk/src/filesystem/devices/stfs_data_file.cpp` | `pread` en bucle (EINTR), `OpenFd` con `fstat` |
| `tools/rexauto/android/sdk/rexglue-sdk/include/rex/filesystem/stfs_asset.h` | `StfsAssetSource`, `Set/Has/TakeStfsAssetSource` |
| `tools/rexauto/android/sdk/rexglue-sdk/src/filesystem/stfs_asset_source.cpp` | Implementación (mutex, `dup`) |
| `tools/run-release-build-v14.sh` | Build normal v1.4 (vC 4) |
| `tools/run-release-build-full-v14.sh` | Build full v1.4 (vC 4, 1.4-full) |
| `analysis/FASE5.md` | Este informe |
| `analysis/FASE5-app.md` | Informe worker APP |
| `analysis/FASE5-vfs-carga.md` | Informe worker VFS |
| `analysis/FASE5-render.md` | Informe worker RENDER |

### Modificados

| Archivo | Cambio |
|---|---|
| `tools/rexauto/android/app/src/main/java/com/rexauto/port/SetupActivity.java` | Borrada la auto-importación; router full sin UI |
| `tools/rexauto/android/app/src/main/java/com/rexauto/port/GameFiles.java` | `ASSET_PREFIX`, rama `asset:` en `hasValidGameRoot` |
| `tools/rexauto/android/app/src/main/AndroidManifest.xml` | `android:name=".PortApp"`; `SetupActivity` → `sensorLandscape` |
| `tools/rexauto/android/app/src/main/res/values/strings.xml` | Borrado `status_bundled_copy` (muerto) |
| `tools/rexauto/android/app/src/main/cpp/CMakeLists.txt` | `stfs_asset_jni.cpp` en `libmain` |
| `tools/rexauto/android/app/src/main/cpp/android_main.cpp` | Rama `asset:` + puente fd (GAP 1+2); `--vulkan_async_skip_incomplete_frames=false`; `--clear_memory_page_state=false` ya estaba |
| `tools/rexauto/android/sdk/rexglue-sdk/include/rex/filesystem/devices/stfs_container_device.h` | Ctor fd + `fd_backed_`/`fd_file_` |
| `tools/rexauto/android/sdk/rexglue-sdk/src/filesystem/devices/stfs_container_device.cpp` | Ctor fd, `OpenFiles()` reescrita, `ReadAt`, coalescido |
| `tools/rexauto/android/sdk/rexglue-sdk/include/rex/filesystem/devices/stfs_container_entry.h` | `MultiFileHandles` con `StfsDataFile` |
| `tools/rexauto/android/sdk/rexglue-sdk/src/filesystem/devices/stfs_container_file.cpp` | `ReadSync` con `ReadAtPartial` |
| `tools/rexauto/android/sdk/rexglue-sdk/src/filesystem/CMakeLists.txt` | `stfs_asset_source.cpp` + `stfs_data_file.cpp` en la lista explícita |
| `tools/rexauto/android/sdk/rexglue-sdk/src/system/runtime.cpp` | Rama STFS-asset primero en `SetupVfs` |
| `tools/rexauto/android/sdk/rexglue-sdk/src/ui/rex_app.cpp` | `HasStfsAssetSource` en `ConstructRuntime` + afinidad `GetBigCoreAffinityMask` + pin |
| `tools/rexauto/android/sdk/rexglue-sdk/src/graphics/vulkan/pipeline_cache.cpp` | Rewinds `"a+"` (`:436`/`:525`) |
| `tools/rexauto/android/sdk/rexglue-sdk/src/graphics/d3d12/pipeline_cache.cpp` | Rewinds `"a+"` (`:269`/`:352`) |
| `tools/rexauto/android/sdk/rexglue-sdk/src/core/threading_posix.cpp` | `WaitMultiple` bloqueante + `NotifyAll()` en 7 caminos |
| `tools/rexauto/android/sdk/rexglue-sdk/src/core/memory_posix.cpp` | `mprotect` primero; `IsRangeFullyMapped` eliminado |
| `tools/rexauto/android/sdk/rexglue-sdk/src/ui/vulkan/vulkan_presenter.cpp` | Log `VulkanPresenter: paint geometry ...` |
| `tools/rexauto/android/patches/rexglue-sdk-v0.10.0-android-perf.patch` | Hunk EEXIST obsoleto eliminado + cabecera de divergencia |
| `tools/run-release-build-full.sh` | Solo comentarios (STORED obligatorio; sin auto-import) |
| `README.md` | Sección de variantes y tamaño (v1.4) |

**No tocado:** `MainActivity.java`, nada de `texture_cache.cpp`, nada de
voces, nada de `tools/rexauto/android/app/build/`, ni `gradle.properties`
(heap 1536m, local) ni `tools/rexauto/android/ci-release.jks` (keystore
corazón, local). **NUNCA pushear `gradle.properties` ni el keystore.**

---

## 11. Builds v1.4

### Normal — BUILD SUCCESSFUL

- **Build:** local, Gradle offline, log `analysis/fase5-build-1.log` —
  **BUILD SUCCESSFUL in 14m 23s** (45 actionable tasks: 25 executed, 1 from
  cache, 19 up-to-date). 0 errores (solo warnings preexistentes).
- **APK:** `ChildOfLight-recomp-v1.4.apk`, **24.568.063 B**,
  **SHA-256 `101e79902efe3a6e04e3a31ff95b25e5edd8e66f65bc57dabb24a984abaf984f`**,
  package `com.rexauto.port.childoflight`, versionCode **4**, versionName
  **1.4**, minSdk 28 / targetSdk 35, launchable-activity =
  `com.rexauto.port.MainActivity` (port oficial: tocar el icono entra
  directo al juego).
- **apksigner verify OK** (v1+v2), firmante = keystore corazón
  (CN=`el título <3`, SHA-256 `3768a83d38837f424138c1570f77e111bd1777ff6df049d476e562a46bb189ab`).
- **Contenidos verificados:** 25 entradas; `lib/arm64-v8a/libmain.so`,
  `lib/arm64-v8a/librexgpu-xenos.so`, `assets/hero_bg.jpg` presentes; sin
  `assets/game.stfs` (correcto: la normal no lleva el juego dentro).

### Full — BUILD SUCCESSFUL

- **Build:** script `tools/run-release-build-full-v14.sh`, log
  `analysis/fase5-build-full-1.log` — Gradle **BUILD SUCCESSFUL in 46 s**
  (incremental), inyección STFS como entrada STORED, zipalign, firma con
  el keystore corazón.
- **APK:** `ChildOfLight-recomp-v1.4-full.apk`, **1.610.028.992 B**,
  **SHA-256 `c645e8c502e5370f91f3a1319d3ff1d2dd5f18233ab0cd4ee3114272d4208601`**,
  **MD5 `8c8dfc4944c4fb9b4a614c384fa7eb02`**, package
  `com.rexauto.port.childoflight`, versionCode **4**, versionName
  **1.4-full**, launchable-activity `com.rexauto.port.MainActivity`.
- **apksigner verify OK** (v1+v2), mismo firmante corazón
  (SHA-256 `3768a83d…9ab`).
- **Contenidos verificados:** 29 entradas; `assets/game.stfs` **STORED**,
  1.585.451.008 B; `assets/hero_bg.jpg` STORED; `libmain.so` presente
  (55.284.352 B).
- **MD5 round-trip del STFS dentro de la APK (streaming, sin escribir a
  disco): `ad88061a06fba28e3434945f37cb3d57` — EXACTO al STFS original.**
- **Partes para Drive:** 16 partes de 100 MB (`part00`–`part14` de
  104.857.600 B, `part15` de 37.164.992 B) en `private/v14-full-parts/`;
  reensamblaje verificado localmente (`cat part* | md5sum` =
  `8c8dfc4944c4fb9b4a614c384fa7eb02`, exacto).
- **Drive (PRIVADO, verificado):** carpeta "el port v1.4-full"
  (ID `1nbjnBy8YY-NSyygpSj9lYHMaUQVR_Zgc`), **19 ficheros, TODOS
  `shared: false`** (verificado por listado: 16 partes con tamaños exactos
  + MD5SUMS + README + APK normal v1.4). La part10 falló en el primer
  intento con un 502 transitorio de Google y se re-subió bien. Enlaces:
  carpeta https://drive.google.com/drive/folders/1nbjnBy8YY-NSyygpSj9lYHMaUQVR_Zgc
  y APK normal https://drive.google.com/file/d/1hFH6NCMhEN5tviVy9LDd8veWT37CSXY_/view

---

## 12. Qué debe probar el usuario en el teléfono (v1.4)

Meta: `docs/RENDIMIENTO.md` — 60 fps sostenidos a 720p (16,6 ms/frame).

1. **Instalar v1.4-full desde cero** (desinstalar antes la v1.3-full): al
   abrir, **entra directo al juego** — sin pantalla de importar, sin
   picker, sin "Copiando el juego incluido". Si aparece el picker en una
   full, es un fallo (asset ausente o con Title ID incorrecto).
2. **Ajustes → Almacenamiento: total ~1,6–1,7 GB** (Aplicación ~1,68 GB;
   Datos solo unos MB). Si Datos marca ~1,58 GB, el montaje no está activo
   y se está extrayendo.
3. **Cerrar y volver a abrir**: entra directo al juego, sin pantallas.
   **Segunda entrada claramente más rápida** (la caché `.xsh`/`.xpso` ya no
   se borra). Anotar el tiempo de "Cargando..." de la 1ª y la 2ª entrada.
4. **FPS**: jugar 5 minutos en la misma zona que en v1.3 y comparar
   (¿suben los ~6 fps? ¿se acaban los tirones durante la compilación de
   shaders?).
5. **Título**: ¿sigue cortado en y≈576? Capturar la pantalla del título y
   la de "Cargando..." y medir el corte si persiste.
6. **Texturas**: ¿sigue la banda blanca lavada en el gameplay? ¿Aparecen
   flashes de placeholder durante los primeros minutos (efecto del
   frame-skip desactivado)? Si el flashing es peor que los frames perdidos,
   reactivar el skip en `settings.txt` con
   `--vulkan_async_skip_incomplete_frames=true`.
7. **La intro SEGUIRÁ saltándose en v1.4** (no hay decodificador WMV
   todavía) — no es un fallo de esta versión. Anotar si tras el título
   aparece el menú o salta directo al gameplay.
8. **Enviar `childoflight.log` + `profile.txt`** de la sesión. Líneas a
   buscar en el log:
   - `game root is asset:game.stfs - STFS asset fd=...` (el puente
     entregó el fd; si aparece el `ALOGE` de triple ausente, el puente JNI
     no entregó el fd — lado APP).
   - `VulkanPresenter: paint geometry guest ...` (título y gameplay) — el
     dato que decide el Bug 1.
   - `Created ...x... swapchain` — contar re-creaciones.
   - `Pinning guest main thread to big cores (affinity mask 0x...)` — qué
     máscara se detectó en el G99.
   - `Created N graphics pipelines from Vulkan storage` — ¿cuántos
     pipelines se reutilizan de la caché en la 2ª entrada?
   - `Skipping Vulkan frame presentation...` — ¿sigue apareciendo? (No
     debería, con el skip desactivado.)
   - `Vertex shader memexport draw encountered` + líneas de fallback DXT
     — el discriminador del Bug 6.
   - `XamLoadExtraAVCodecs2 STUB` — confirmaría la causa de la intro.
   - En `profile.txt`: ¿bajaron `pthread_mutex_trylock` / `unlock` /
     `__kernel_clock_gettime`? ¿Sigue AK::IOThread al 94%?

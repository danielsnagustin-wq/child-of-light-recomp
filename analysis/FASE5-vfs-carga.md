# FASE 5 — VFS / FLUJO / CARGA (v1.4) — Child of Light, port nativo Xbox 360 → Android

Autor: worker VFS/FLUJO/CARGA · Fecha: 2026-11-13 · Base: v1.3, HEAD `205b41c`
Alcance: bugs 3+7 (intro que se salta, `.srt` no resuelto), bug 2 (carga de varios minutos),
P0 #10 (montar el STFS desde dentro de la APK, < 2 GB, sin importar nada),
P0 #3 (bug Bionic `"a+"` en la caché de shaders), P1 #9/#11 (pre-warm / pipelines asíncronos).
**No se pusheó nada. No se construyeron APKs.** El parent construye al final.

## 0. Veredicto en una página

| Bug | Causa exacta | Estado en v1.4 |
|---|---|---|
| 3+7 — intro se salta, `plan_logo.es.srt` no se encuentra | **Dos causas distintas, ninguna es el VFS.** (1) `plan_logo.es.srt` **no existe en ningún sitio**: ni suelto ni dentro de ningún `.ipk` (verificado por escaneo de los 4 `.ipk`); el fallo `0xc000000f` es ruido esperado del logo. (2) Las películas WMV3 **no tienen decodificador en ReXGlue**: `XamLoadExtraAVCodecs2` es un stub que no carga los codecs del sistema 360; sin decodificador WMV el juego salta las películas. El montaje STFS **no cura** ninguno de los dos. | Sin arreglo en v1.4: falta un decodificador WMV3/WMA (trabajo grande, separado). Ver §2. |
| 2 — "Cargando..." varios minutos | **La caché persistente de shaders se borra en CADA arranque en Android** (bug Bionic `"a+"`: la primera lectura cae al final del fichero, no hay cabecera, el código trunca el fichero a 0). Además la caché del driver (`VkPipelineCache`) **nunca se persiste** (0 ocurrencias de `vkGetPipelineCacheData`). | **Arreglado el borrado**: rewind explícito tras cada apertura `"a+b"` (Vulkan + D3D12). El segundo arranque debe ser mucho más rápido. Ver §3. |
| P0 #10 — STFS desde la APK | El driver STFS existía pero solo abría rutas de host con `fopen`. | **Implementado**: `StfsDataFile` (fd+offset+longitud con `pread`), ctor fd de `StfsContainerDevice`, montaje en `SetupVfs`, puente en `android_main.cpp` (GAP 1 y GAP 2 cerrados). Ver §4. |
| P1 #9/#11 — pre-warm / async | El mecanismo asíncrono **ya existe** en el SDK (hilos de creación + placeholder); no existe siembra de caché (`shader_storage_seed_root` = 0 ocurrencias). | **Evaluado, sin código nuevo en v1.4**: la siembra necesita una caché templada que aún no existe (y el bug `"a+"` la habría borrado); el pre-carga de arranque sigue siendo síncrono por diseño. Ver §5. |

Regla respetada: `docs/RENDIMIENTO.md` — shaders bajo demanda con caché persistente, nunca
precompilar todo al arrancar. Nada de lo hecho precompila nada nuevo; solo hace que la caché
que ya existía por diseño sobreviva entre arranques.

---

## 1. Cómo resuelve el VFS las rutas `d:\...` (base para todo lo demás)

- `Runtime::SetupVfs` (`tools/rexauto/android/sdk/rexglue-sdk/src/system/runtime.cpp:294-321`
  en el original) monta `game_data_root_` como `\Device\Harddisk0\Partition1` con
  `HostPathDevice(mount_path, abs_game_root, !REXCVAR_GET(allow_game_relative_writes))`,
  registra los enlaces `game:` y `d:` al mismo punto, y monta `update_data_root_` como
  `\Device\Harddisk0\PartitionUpdate` si existe.
- `VirtualFileSystem::ResolvePath` (`src/filesystem/virtual_file_system.cpp`) canoniza la ruta
  del invitado, resuelve enlaces, busca el dispositivo por prefijo del punto de montaje y, si no
  hay, registra `VFS: '<path>' -> [no device]` + `ResolvePath(<path>) failed - device not found`.
- `UPDATE:\patch_x360.ipk` falla por eso: `update_data_root` está vacío en Android (no hay parche
  que aplicar). **Benigno y esperado.** `NtCreateFile` (`src/kernel/xboxkrnl/xboxkrnl_io.cpp:103`)
  devuelve `0xc000000f` = `X_STATUS_NO_SUCH_FILE` vía `VFS::OpenFile`.
- `HostPathDevice` (`src/filesystem/devices/host_path_device.cpp`) es un directorio de host plano:
  `d:\world\common\movie\plan_logo.es.srt` se resuelve a
  `<game_data_root>/world/common/movie/plan_logo.es.srt` en el sistema de ficheros del host.
  **No puede ver dentro de los `.ipk`.** Los `.ipk` no se montan como directorios virtuales para
  `NtCreateFile`; el juego los abre como ficheros y los lee por dentro con su propio lector
  (sistema de ficheros en capas de UbiArt: sueltas primero, luego archivos IPK — ver §2.2).
- El `StfsContainerDevice` ya estaba compilado en el SDK (`src/filesystem/CMakeLists.txt`,
  lib OBJECT `rexfilesystem`; `src/kernel/CMakeLists.txt:69` la enlaza en `rex::runtime`;
  `CMakeLists.txt:275` del SDK tiene `add_subdirectory(src/filesystem)`). Su único usuario en
  árbol era `ContentManager::InstallContent` (`src/system/xam/content_manager.cpp:578`), que
  monta el STFS y luego **copia** todos los ficheros fuera — la copia es el paso que sobra,
  no el montaje.

---

## 2. Bugs 3+7 — intro que se salta, `plan_logo.es.srt` no resuelto

### 2.1 Dónde vive realmente cada cosa (verificado contra los ficheros reales)

- Películas sueltas en `private/extracted/world/common/movie/`: `attract.wmv`,
  `col_trial_outro.wmv`, `plan_logo.wmv`, `r00d02_cin060_cs_goodending_bink.wmv`,
  `r01_cin010_cs_intro_bink.wmv` (la intro), `r01_cin020_cs_altar_bink.wmv`,
  `r01_cin130_cs_storyoflight_bink.wmv`, `r01_cin142_ig_thornbushes_bink.wmv`,
  `r02_cin000_ig_introplains_bink.wmv`, `r02_cin253_cs_magnaup_bink.wmv`.
- **Cero `.srt` sueltos** en todo el árbol extraído (`find *.srt` = 0).
- `.ipk` (magic `50 EC 12 BA`, cabecera BE u32[3]=offset de tabla de nombres,
  u32[4]=nº de ficheros): bundle 3611 ficheros, child_of_light_x360 3344,
  fullLogic 3626, persistentloading 46. Nombres `.srt` distintos por escaneo regex:
  bundle **0**, **child_of_light_x360.ipk 61**, fullLogic **0**, persistentloading **0**.
  Ejemplo: `r01_cin010_cs_intro_bink.es.srt` con hermanos `.de/.en/.fr/.it/.ja/.mx/.pt/.ru`.
  El registro dentro del `.ipk` lleva la ruta lógica exacta que pide el juego:
  u32 dir-len `0x13` + `world/common/movie/` + u32 name-len `0x1f` +
  `r01_cin010_cs_intro_bink.es.srt`.
- **`plan_logo.es.srt` no existe en ningún sitio**: ni suelto, ni en ninguno de los 4 `.ipk`
  (la única coincidencia es `plan_logo.videod` en `bundle_x360.ipk`). El logo no tiene fichero
  de subtítulos. El `NtCreateFile FAILED ... plan_logo.es.srt -> 0xc000000f` es **ruido esperado**
  del cargador de subtítulos sondeando un fichero que el juego original tampoco tiene.
  **Montar el STFS no "cura" `plan_logo.es.srt`.** El bug 7, tal como estaba formulado
  (".srt no encontrado"), es una pista falsa para el logo.
- Los subtítulos de las cinemáticas (que SÍ existen dentro de `child_of_light_x360.ipk`) los lee
  el propio motor UbiArt del juego con su sistema de ficheros en capas (sueltas primero, luego
  IPK); el VFS solo tiene que servir los bytes del `.ipk` correctamente, cosa que el
  `HostPathFile` de v1.3 ya hace. No hay literales `movie`/`wmv`/`plan_logo`/`.srt`/`ipk` en el
  código recompilado del juego (`autoports/childoflight/port/generated/`): las rutas se
  construyen en tiempo de ejecución, lado invitado.

### 2.2 Por qué se salta la película (la causa real)

- ffprobe verificado 2026-11-13: `plan_logo.wmv` = WMV3 (VC-1 Main) 1280x720 29.97 fps +
  WMA Pro 5.1, 9.68 s. `r01_cin010_cs_intro_bink.wmv` = WMV3 1280x720 30 fps + 9 pistas
  WMA Pro (fre, jpn, ger, spa, ita, rus, por, spa, eng), 75 s.
- **El SDK no tiene decodificador WMV/vídeo**: solo menciones a decodificadores software
  VP6/Bink en comentarios de la caché de texturas; FFmpeg (`libavcodec`) solo está enlazado
  para audio XMA (`src/audio/xma_context.cpp`).
- `src/kernel/xam/xam_video.cpp` (44 líneas) solo implementa `XGetVideoMode` /
  `XGetVideoCapabilities`. `XamLoadExtraAVCodecs2` / `XamUnloadExtraAVCodecs2` son
  `REX_EXPORT_STUB` (`export_table.inc:1353-1354`). `REX_STUB` (`include/rex/hook.h:52-57`)
  solo registra un aviso y **no pone r3** — el valor de retorno es lo que r3 tuviera
  (indefinido). En Xbox 360, los juegos que reproducen WMV cargan los codecs del sistema
  WMV/WMA vía `XamLoadExtraAVCodecs2`; con el stub, la inicialización del decodificador de
  vídeo del juego no puede tener éxito → las películas se saltan.
- No se pudo probar que el juego importe `XamLoadExtraAVCodecs2` (sin referencia en
  `autoports/` ni en `analysis/col-imports.txt`, que solo lista direcciones IAT).
  **Comprobación para el parent/usuario: buscar `XamLoadExtraAVCodecs2 STUB` en el log de v1.3.**
- Precedente Xenia (búsqueda web): los vídeos en Xenia a menudo se reproducen (decodificación
  software Bink a texturas) o se saltan/quedan en negro; los vídeos del logo de Ubisoft aparecen
  específicamente en problemas de compatibilidad. No se encontró soporte de decodificador WMV
  en Xenia.
- **El salto del menú NO lo explica el VFS**: el título se renderiza y el juego llega al
  gameplay. Hipótesis sin verificar: auto-continuación del flujo de menú / doble activación de
  entrada título→menú, o el fondo animado del menú (`attract.wmv`, también WMV3) fallando.
  Necesita el log completo + la comprobación del worker de presenter (¿menú renderizado fuera
  de pantalla?). **No afirmar una causa VFS para el menú.**

### 2.3 Qué NO se hizo aquí (y por qué)

- No se implementó un decodificador WMV3/WMA: es trabajo grande (FFmpeg VC-1 + WMA Pro,
  subida de frames a texturas, sincronización A/V, selección de pista de audio española entre
  las dos `spa`) y pertenece a un worker de vídeo/audio, no al VFS. La intro **seguirá
  saltándose en v1.4** hasta que ese decodificador exista — no prometer lo contrario al usuario.
- Dato útil (verificado por Aurora, no por este worker): las cinemáticas YA tienen audio en
  español en los datos de 360 (9 pistas, 2 `spa` — España vs LatAm aún por confirmar cuál es
  cuál). Cuando el decodificador exista, el audio español ya está ahí; no hace falta Vita
  para las películas. Solo el narrador del juego es inglés en 360.

---

## 3. Bug 2 — "Cargando..." de varios minutos

### 3.1 La caché SÍ persiste por diseño (pero se borraba en cada arranque)

- `VulkanPipelineCache::InitializeShaderStorage`
  (`src/graphics/vulkan/pipeline_cache.cpp:391`) lee
  `<cache>/shaders/shareable/<TITLEID>.fbo.vk.xpso` (descripciones de pipelines, validadas
  con XXH3) + `<TITLEID>.xsh` (shaders del invitado), traduce los shaders necesarios y
  (bloqueante) recrea los pipelines guardados al arrancar. Se llama con `blocking=true`
  desde `ReXApp::LaunchModule` (`src/ui/rex_app.cpp:464`) tras `PrepareModuleLaunch`, antes
  de `main_thread->Resume()`. El cvar `store_shaders` es true por defecto
  (`src/graphics/graphics_system.cpp:39`). Hay un `StorageWriteThread` que añade entradas
  nuevas. `vulkan_pipeline_creation_threads` por defecto -1 → 3/4 de los núcleos lógicos.
- **NO hay `vkGetPipelineCacheData` en `pipeline_cache.cpp` (0 ocurrencias, confirmado)**:
  el `VkPipelineCache` del driver nunca se persiste — cada arranque vuelve a ejecutar
  `vkCreateGraphicsPipelines` para todos los pipelines guardados (el coste "mali-compiler"
  del perfil). Los ficheros `.xpso`/`.xsh` ahorran solo la traducción a SPIR-V, no la
  compilación del driver.

### 3.2 El bug Bionic `"a+"` — VERIFICADO, P0 #3 (precedente Gears parche 0007)

- Los dos ficheros de caché se abren con `"a+b"`:
  `pipeline_cache.cpp:422` (pipeline storage) y `:505` (shader storage); lo mismo en D3D12
  (`:255` y `:332`). Son las 4 ÚNICAS aperturas `"a+b"` del SDK. El código hace
  `fread(header)` inmediatamente **sin rewind/seek previo**.
- Manual (man-pages, espejo kernel.org + Arch): *"POSIX is silent on what the initial read
  position is when using this mode. For glibc, the initial file position for reading is at
  the beginning of the file, but for Android/BSD/MacOS, the initial file position for reading
  is at the end of the file."*
- Prueba local con glibc: `fopen("a+b")` → `ftell=0`, la cabecera se lee bien. **En Bionic
  el primer `fread` devuelve 0** → la comprobación de cabecera falla → el código trunca el
  fichero a 0 y escribe una cabecera nueva (cola de `InitializeShaderStorage`: descripciones
  vacías → `TruncateStdioFile(...,0)` + cabecera nueva; la rama else del shader storage igual).
- **Consecuencia: en Android la caché persistente de shaders se borra en CADA arranque.**
  La carga de "varios minutos" no es solo de la primera vez, se repite siempre. El log de v1.3
  no debería mostrar nunca `Created N graphics pipelines from Vulkan storage`.
- **Arreglo implementado** (ver §6, filas 12–13): `rex::filesystem::Seek(file, 0, SEEK_SET)`
  explícito tras cada apertura, en Vulkan y en D3D12. El rewind también limpia el indicador
  EOF; las escrituras siguen yendo al final porque el modo es O_APPEND. Es el mismo arreglo
  que el parche 0007 de Gears (`analysis/FASE5-PORTS-OTROS.md:177`).
- Caveat (MW2, mismo informe): MW2 desactivó el `VkPipelineCache` persistente en Mali por
  deadlocks documentados del driver — **no añadir persistencia del driver sin interruptor
  y sin prueba en el teléfono**. No se tocó nada de eso aquí.

### 3.3 El fallback DXT no es decodificación por CPU (hipótesis descartada)

- La Mali-G57 no tiene formatos BC, así que `VulkanTextureCache`
  (`src/graphics/vulkan/texture_cache.cpp:2459+`, `log_bc_fallback`) pasa de BC1/BC2/BC3 a
  RGBA8 mediante **load shaders de cómputo en GPU** (`kLoadShaderIndexDXT1ToRGBA8`, etc.):
  4x memoria de texturas + 4x ancho de banda de subida + un pase de decodificación por
  cómputo en cada invalidación. El coste de CPU es el 4x de ancho de banda + la contención
  del mutex de la caché de texturas, **no** decodificación por CPU. Es territorio del worker
  de render; no se cambió nada ahí. `docs/RENDIMIENTO.md` (perfil Mali, punto 4) lo confirma
  como restricción conocida ("Texturas BC decodificadas en CPU (la G57 no tiene BC/DXT)").
- La copia+extracción de 1.5 GB del flujo antiguo era por sí misma gran parte de "la primera
  vez tarda una eternidad"; el STFS-desde-asset elimina la copia+extracción por completo —
  esa es la verdadera ganancia de primera carga.

---

## 4. P0 #10 — Montar el STFS desde dentro de la APK

### 4.1 Verificaciones previas exigidas (a)(b)(c)

**(a) El juego no escribe nada en `d:\`.** Los saves/contenido NO viven en `d:\`:
`ContentManager` tiene su raíz en `user_data_root` (`src/system/kernel_state.cpp:75`).
`allow_game_relative_writes` es `false` por defecto
(`src/filesystem/virtual_file_system.cpp:18`) y no se pone en ningún otro sitio, así que en
v1.3 `d:\` YA estaba montado solo-lectura (`SetupVfs` pasa
`read_only = !allow_game_relative_writes` = true) y `VFS::OpenFile` degrada las aperturas
de escritura sobre entradas de solo-lectura a acceso de lectura en vez de fallar. **El STFS
de solo-lectura es idéntico en comportamiento para el juego.** El cvar
`allow_game_relative_writes` existe, pero nadie lo activa en Android.

**(b) Coste CPU de las lecturas STFS.** El montaje (`ReadSTFS`) recorre la cadena de hashes
una vez por fichero y construye `block_list_`; el coste por lectura en
`StfsContainerFile::ReadSync` es un `pread` por tramo contiguo. Pero el código original
metía **un registro de bloque por cada bloque de 4 KiB sin coalescer**: un `.ipk` de 657 MB
construía ~160k registros y cada lectura aleatoria escaneaba esa lista linealmente desde el
principio. Se implementó coalescido (ver §6, fila 6). **No se pudo medir tiempo de pared
aquí** (no hay ningún `game.stfs` en el workspace); el informe debe decirlo y pedir al
parent/usuario que cronometre el montaje desde el log.

**(c) El `.ipk` con acceso aleatorio funciona igual sobre STFS que sobre disco.**
`XFile::Read` (`src/system/xfile.cpp:105,158`) llama
`file_->ReadSync(buffer, byte_offset, ...)`; `StfsContainerFile::ReadSync` admite offset/
longitud arbitrarios vía `block_list_` — el lector IPK propio del juego abre el `.ipk` por
`NtCreateFile` y lo lee posicionalmente, lo que funciona igual sobre STFS que sobre disco.

### 4.2 Diseño implementado

- `StfsDataFile`: un rango contiguo de bytes que respalda un fragmento STFS/SVOD; o bien un
  fichero de host entero (`FILE*`, modo ruta) o bien un trozo de un fichero mayor
  (fd + base_offset + length, modo fd). `OpenPath`, `OpenFd` (toma la propiedad del fd en
  todos los casos), `ReadAt` (exacto), `ReadAtPartial` (tolerante a lecturas cortas),
  `size()`, `is_fd_backed()`. Modo fd solo en Android/Linux/macOS (`REX_STFS_FD_BACKED`).
  En modo fd usa `pread()` en bucle (seguro ante EINTR) — posicional ⇒ lecturas concurrentes
  seguras sin hilos; en modo ruta conserva `fseek`+`fread` (comportamiento histórico).
  `OpenFd` valida el trozo contra el tamaño de `fstat`.
- `StfsContainerDevice` con ctor fd (solo STFS de un fichero; sin SVOD multi-fichero desde fd).
  `Initialize()` se ramifica: en modo fd se saltan las comprobaciones de directorio/existencia.
  `OpenFiles()` reescrita (rama fd: verificación de cabecera desde el trozo, rechazo de
  `data_file_count > 1`; rama ruta: misma lógica vía `StfsDataFile::OpenPath`).
  `ReadHeaderAndVerify(StfsDataFile*)` (tamaño desde el trozo, `ReadAt(0, &header_, ...)`).
  Todos los `Seek`+`fread` de `ReadSVOD` (magic 0x2000/0x12000/0xD000, XSF 3 bytes, root_data),
  `ReadEntrySVOD` (entrada de directorio + nombre), `ReadSTFS` (bloque de directorio) y
  `GetBlockHash` (3 lecturas de tabla de hashes) pasaron a `ReadAt`.
  `ReadPackageHeader` (estático, basado en ruta) **sin cambios**.
- **Coalescido en `ReadSTFS`**: bloques físicamente contiguos se fusionan en un solo
  `BlockRecord` (misma regla que `ReadEntrySVOD` ya usaba para SVOD; comprueba
  `last.file == 0 && last.offset + last.length == offset`); la validación ahora cuenta
  bloques con un `block_count` local en vez de `block_list_.size()` (que se rompería con el
  coalescido). El null-deref de `GetBlockHash` ante error de lectura es preexistente y se
  conserva tal cual.
- `SetupVfs` (`runtime.cpp`): rama STFS-asset PRIMERO (si `HasStfsAssetSource()`:
  `TakeStfsAssetSource()`, monta `StfsContainerDevice` en `\Device\Harddisk0\Partition1`,
  registra los enlaces `game:`/`d:`, `game_mounted=true`; si falla, registra el error y cae
  al montaje host-path). El montaje host-path queda envuelto en `if (!game_mounted)`
  (lógica sin cambios, incluido el montaje update + NullDevice). Comentario largo documenta
  la equivalencia de solo-lectura y el enrutado de saves por ContentManager.
- `ConstructRuntime` (`rex_app.cpp`): captura `const bool stfs_asset_source =
  HasStfsAssetSource()` al principio (porque `SetupVfs` toma/limpia el global durante
  `Setup`, así que las comprobaciones posteriores deben usar el valor capturado); raíz
  `game_data_root` vacía permitida solo cuando `stfs_asset_source`; raíz no vacía sigue
  teniendo que ser un directorio; la comprobación host de existencia de `default.xex` se
  salta cuando `stfs_asset_source` (el XEX se carga desde el contenedor montado vía
  `LoadXexImage("game:\\default.xex")`).
- `app/build.gradle.kts:106`: `noCompress += "stfs"` — el asset se guarda SIN COMPRIMIR en
  la APK, así que `AssetManager.openFd` devuelve un fd real + startOffset + length dentro
  del fichero APK. Eso es lo que hace posible el enfoque fd.
- El `game.stfs` empaquetado es STFS (un solo fichero), NO SVOD: `SetupActivity.importStfsFile`
  lanza "GOD needs folder" para SVOD, y el auto-import full funciona — así que el camino fd
  solo necesita STFS de un fichero (`data_file_count <= 1`). No existe ningún `game.stfs` en
  el workspace (buscado; el asset de la APK full de 1.6 GB viene del disco del usuario vía
  staging de CI).

### 4.3 Interfaz JNI esperada (lado APP — ya construida por el worker APP, verificada)

Circuito completo:

```
PortApp.onCreate
  → StfsAsset.ensureOpen(getAssets())            (PortApp.java:20)
  → StfsAsset.setAssetFd(fd, startOffset, length)  (StfsAsset.java:154, nativo)
  → Java_com_rexauto_port_StfsAsset_setAssetFd     (cpp/stfs_asset_jni.cpp)
      · hace dup() del fd inmediatamente; el fd queda abierto durante el proceso
      · guarda el triple en globales propios, expuesto como
        rexport::StfsAssetFd(int*, std::int64_t*, std::int64_t*)  (cpp/stfs_asset.h:31)
  → android_main.cpp (rama asset:, :561-586) — puente, ver §4.4
      · rexport::StfsAssetFd(...) → rex::filesystem::SetStfsAssetSource(fd, offset, length)
  → rex::filesystem::SetStfsAssetSource(fd, base_offset, length)  (SDK, stfs_asset.h:47)
      · hace dup() de nuevo; la copia del lado JNI queda abierta como está documentado
  → Runtime::SetupVfs (runtime.cpp:317) monta StfsContainerDevice en Partition1
  → el juego lee d:\... desde dentro del STFS
```

Contrato del lado SDK (`include/rex/filesystem/stfs_asset.h`):
`SetStfsAssetSource(fd, offset, length)` — toma la propiedad del fd que se le pasa (hace
dup interno; el llamador conserva su copia). `HasStfsAssetSource()` / `TakeStfsAssetSource()`
(transfiere la propiedad y limpia el global). Globales protegidos por mutex.
La raíz del juego la decide solo el marcador `game_root.txt`
(`GameFiles.ASSET_PREFIX = "asset:"`, formato `asset:game.stfs`, `GameFiles.java:20-22,80`).

### 4.4 GAP 1 y GAP 2 — los dos cierres de conexión (asignados por el parent 2026-11-13 ~15:22)

**GAP 1 — Nadie llamaba a `SetStfsAssetSource`.** grep de todo `tools/` (cpp/h/java): CERO
llamadores. `stfs_asset_source.cpp` guardaba el triple en globales del SDK, pero el JNI del
worker APP guardaba el triple en SUS propios globales (`rexport::StfsAssetFd()` en
`stfs_asset_jni.cpp`, libmain). Dos fuentes de verdad desconectadas: `HasStfsAssetSource()`
siempre sería false y la rama STFS de `SetupVfs` nunca se ejecutaría.
**Cierre (opción (b), la que el propio header APP documenta como contrato de consumidor):**
en la rama `asset:` de `android_main.cpp`, `android_main` llama a
`rexport::StfsAssetFd(...)` y luego a `rex::filesystem::SetStfsAssetSource(fd, offset, length)`
(`android_main.cpp:575-576`). Incluye `#include "stfs_asset.h"` y
`#include <rex/filesystem/stfs_asset.h>` (añadidos tras un primer intento fallido por
old_text: el fichero real tiene `#if REX_HAVE_ADRENOTOOLS` / `#endif` alrededor del include
de adrenotools, líneas 53-59). Verificación de la cadena:
`android_main.cpp:575-576` → `stfs_asset_jni.cpp:28` → `stfs_asset.h:31` →
`stfs_asset.h (SDK):47` → `stfs_asset_source.cpp:36`. `kConfigFileName` = `"game_root.txt"`
(`android_main.cpp:76`).
Además se verificó que `stfs_asset_source.cpp` se compila de verdad en la lib del SDK:
**está en la lista explícita** de `src/filesystem/CMakeLists.txt` (líneas 4 y 18, junto con
`devices/stfs_data_file.cpp`); el SDK usa lista explícita, no glob.

**GAP 2 — `android_main` seguía pasando el marcador como `--game_data_root`.**
`android_main.cpp:558-559` pasaba `game_root.txt` tal cual
(`--game_data_root=asset:game.stfs`), y `ConstructRuntime` (:220) rechaza cualquier raíz
no vacía que no sea directorio. El acomodo de raíz vacía no servía mientras `android_main`
pasara el marcador.
**Cierre:** si `game_root.starts_with("asset:")` (`android_main.cpp:561`), NO se pasa
`--game_data_root` (se deja vacío para que la rama STFS decida por la presencia del triple
fd) y se ejecuta el puente de arriba; se registra el fd/offset/length con `ALOGI`; si el
triple falta, `ALOGE` explicando que el juego no puede arrancar (entonces `ConstructRuntime`
falla con `"--game_data_root was not provided"`). Las raíces de directorio mantienen el
comportamiento histórico `--game_data_root=<path>` (`:590`).
Sin estos dos cierres, una v1.4-full no arranca (rechazo en `ConstructRuntime` o caída al
montaje host-path con raíz inválida). Con ellos, el circuito completo queda cerrado.

---

## 5. P1 #9 y #11 — Pre-warm + siembra de caché / creación asíncrona de pipelines

Evaluación para v1.4 (verificado contra el código, no implementado):

- **Creación asíncrona de pipelines — YA EXISTE en el SDK.** Hilos `CreationThread`
  (`pipeline_cache.cpp:3531`), `creation_queue_`, `IsCreatingPipelines()` (`:1219-1224`),
  `startup_loading_`. `ConfigurePipeline` (~:1130-1200) ya implementa el patrón estilo
  wivi514: si el pipeline real no está listo, crea un pipeline **placeholder** (con
  placeholder pixel shader) vía `EnsurePipelineCreated(creation_arguments_placeholder,
  placeholder_pixel_shader_)`, marca `is_placeholder=true` y mete los argumentos reales
  en `creation_queue_` para los hilos; el draw sigue con el placeholder (no se salta, se
  renderiza con el shader placeholder). Solo cuando la creación asíncrona está desactivada
  se bloquea síncronamente (`EnsurePipelineCreated(creation_arguments_real)`).
  Lo que sigue siendo síncrono es la **precarga de arranque**: `InitializeShaderStorage`
  crea todos los pipelines guardados de forma bloqueante
  ("Vulkan currently doesn't support deferred pipeline handles at draw time, so complete
  preload creation regardless of blocking mode", ~:655). Hacer asíncrona la precarga de
  arranque sería un cambio mayor en la interacción `ConfigurePipeline` /
  `InitializeShaderStorage` (renderizado con placeholders al arrancar, drenado de cola)
  y es trabajo del worker de render. **Recomendación v1.4: no tocarlo; medir primero el
  segundo arranque con el arreglo `"a+"`.** Si sigue siendo lento, la precarga asíncrona
  es el siguiente paso.
- **Pre-warm / siembra — NO existe soporte de semilla en el SDK.**
  `shader_storage_seed_root` = 0 ocurrencias. El estilo CrownPark (sembrar una caché templada
  copiando `.xsh`/`.xpso` antes de cargar) sería una característica nueva: hacen falta
  ficheros semilla empaquetados en la APK (que no existen — nadie ha jugado el juego entero
  para generarlos), una copia al arrancar y lógica de "nunca sobrescribir la caché existente".
  Además el bug `"a+"` habría borrado cualquier semilla en el primer arranque.
  **Recomendación: aplazar la siembra a después de que v1.4 salga con el arreglo `"a+"` y
  el usuario juegue una partida completa (generando una caché templada en el dispositivo);
  una versión futura podría empaquetar esa caché templada como semilla.**
- Lo que Origins llama "pre-warm" (`pipeline_cache.bin` + `pipelines.txt`, crear todo al
  arrancar) es exactamente lo que `InitializeShaderStorage` ya hace con `.xpso` (bloqueante).
  La pieza que falta es solo el blob `VkPipelineCache` del driver (deliberadamente no
  persistido; ver caveat MW2 en §3.2).
- Los datos de wivi514 (`tools/wivi514-casezero/docs/async-pipeline-plan.md`): `vkCreateGraphicsPipelines`
  1–200 ms por pipeline, perezoso en el hilo de frame, 534 llamadas en 3 min de juego, un
  frame de 396 ms (372 ms dentro de `GetPipeline`). Coherente con el perfil del usuario
  (Main XThread 96%, AK::IOThread 94%, mutex 8%/6.5%, GPU Commands 14%): el coste está en
  la creación de pipelines y la contención, no en la GPU.

---

## 6. Cambios implementados (todos en `~/workspace/col-recomp/`, NO pusheados)

### Archivos nuevos (SDK)

| # | Archivo | Contenido |
|---|---|---|
| 1 | `tools/rexauto/android/sdk/rexglue-sdk/include/rex/filesystem/devices/stfs_data_file.h` | `StfsDataFile`: un rango contiguo de bytes que respalda un fragmento STFS/SVOD; fichero de host entero (`FILE*`, modo ruta) o trozo de un fichero mayor (fd + base_offset + length, modo fd). `OpenPath`, `OpenFd`, `ReadAt`, `ReadAtPartial`, `size()`, `is_fd_backed()`. |
| 2 | `tools/rexauto/android/sdk/rexglue-sdk/src/filesystem/devices/stfs_data_file.cpp` | Bucle `pread()` seguro ante EINTR en modo fd (posicional ⇒ lecturas concurrentes seguras); `fseek`+`fread` en modo ruta. `OpenFd` valida el trozo contra el tamaño de `fstat`. Solo fd en Android/Linux/macOS (`REX_STFS_FD_BACKED`). |
| 3 | `tools/rexauto/android/sdk/rexglue-sdk/include/rex/filesystem/stfs_asset.h` | `StfsAssetSource{fd, base_offset, length}`, `SetStfsAssetSource(fd, offset, length)` (hace dup del fd; reemplaza cualquier fuente previa), `HasStfsAssetSource()`, `TakeStfsAssetSource()` (transfiere la propiedad, limpia el global). Globales con mutex. |
| 4 | `tools/rexauto/android/sdk/rexglue-sdk/src/filesystem/stfs_asset_source.cpp` | Implementación. |

### Archivos modificados (SDK)

| # | Archivo | Cambio |
|---|---|---|
| 5 | `include/rex/filesystem/devices/stfs_container_device.h` | Nuevo ctor `StfsContainerDevice(mount_path, int fd, uint64_t base_offset, uint64_t length)` (toma la propiedad del fd; solo STFS de un fichero, sin SVOD multi-fichero desde fd); `files_` pasa de `std::map<size_t, FILE*>` a `std::map<size_t, std::unique_ptr<StfsDataFile>>`; miembros `fd_backed_`, `fd_file_`; `ReadHeaderAndVerify(StfsDataFile*)`. |
| 6 | `src/filesystem/devices/stfs_container_device.cpp` | Ctor fd; `Initialize()` se ramifica (modo fd salta comprobaciones de directorio/existencia); `OpenFiles()` reescrita (rama fd: verificación de cabecera desde el trozo, rechazo de `data_file_count > 1` en `:138`; rama ruta: misma lógica vía `StfsDataFile::OpenPath`); `CloseFiles()` (los destructores unique_ptr cierran FILE*/fd); `ReadHeaderAndVerify(StfsDataFile*)`; todos los `Seek`+`fread` de `ReadSVOD`, `ReadEntrySVOD`, `ReadSTFS`, `GetBlockHash` pasaron a `ReadAt`. `ReadPackageHeader` (estático, basado en ruta) SIN CAMBIOS. **Coalescido en `ReadSTFS`**: bloques contiguos se fusionan en un `BlockRecord`; la validación cuenta bloques con un `block_count` local. |
| 7 | `include/rex/filesystem/devices/stfs_container_entry.h` | `MultiFileHandles` ahora `std::map<size_t, std::unique_ptr<StfsDataFile>>` + include de `stfs_data_file.h` + `<memory>`. |
| 8 | `src/filesystem/devices/stfs_container_file.cpp` | `ReadSync` ahora usa `file->ReadAtPartial(record.offset + read_offset, p, read_length)` (posicional; sin seek; se conserva la semántica histórica de acumulación ante lecturas cortas). |
| 9 | `src/filesystem/CMakeLists.txt` | Añadidos `stfs_asset_source.cpp` y `devices/stfs_data_file.cpp` a la lista explícita OBJECT de `rexfilesystem` (líneas 4 y 18; el SDK `CMakeLists.txt:275` tiene `add_subdirectory(src/filesystem)`). |
| 10 | `src/system/runtime.cpp` | Includes añadidos (`stfs_container_device.h`, `stfs_asset.h`); `SetupVfs()` reestructurada: rama STFS-asset primero (`:317-332`, `TakeStfsAssetSource()` en `:318`); montaje host-path envuelto en `if (!game_mounted)` (lógica sin cambios, incl. montaje update + NullDevice). Comentario largo sobre equivalencia de solo-lectura y enrutado de saves. |
| 11 | `src/ui/rex_app.cpp` | Include `stfs_asset.h`; `ConstructRuntime` captura `const bool stfs_asset_source = HasStfsAssetSource()` en `:215` (antes de `Setup`); raíz vacía válida solo con `stfs_asset_source` (`:220`); la comprobación host de `default.xex` se salta cuando `stfs_asset_source` (`:285`). |
| 12 | `src/graphics/vulkan/pipeline_cache.cpp` | Arreglo `"a+"`: tras `OpenFile(..., "a+b")` en `:422` (pipeline storage) y `:512` (shader storage), añadido `rex::filesystem::Seek(file, 0, SEEK_SET)` en `:436` y `:525` + comentario (Bionic posiciona `"a+"` al final; el rewind también limpia el indicador EOF; las escrituras siguen siendo append por O_APPEND). |
| 13 | `src/graphics/d3d12/pipeline_cache.cpp` | Arreglo idéntico: aperturas `:255` y `:339`, rewinds `:269` y `:352`. No se usa en Android; mismo bug latente, mismo arreglo, inocuo en todas las libc. |
| 14 | `tools/rexauto/android/app/src/main/cpp/android_main.cpp` | **GAP 1 + GAP 2** (ver §4.4): includes `stfs_asset.h` y `<rex/filesystem/stfs_asset.h>`; rama `asset:` en `:561-586` — detección del prefijo, puente `rexport::StfsAssetFd` → `SetStfsAssetSource` en `:575-576`, `ALOGI` con fd/offset/length, `ALOGE` si falta el triple, y NO pasar `--game_data_root` para raíces asset (`:590` mantiene el paso histórico para raíces de directorio). |

### Archivos que NO toqué

- Nada de presenter/surface/window/MainActivity ni de la app Java (del worker APP).
- En `rex_app.cpp` hay hunks preexistentes de otros workers (`SetupEnvironment` ~:201,
  `SetupOverlays` ~:427, `LaunchModule` ~:456) y en `d3d12/pipeline_cache.cpp`
  (`#include <fstream>` ~:17, log `dump_shaders` ~:1221): no son míos, no los toqué.
- El resto del árbol SDK tiene 72 ficheros modificados por otros workers; mis cambios son
  solo los 13 de arriba + `android_main.cpp`.

---

## 7. Qué se verificó

- **Sintaxis** con `clang++ -std=c++23 -fsyntax-only --target=aarch64-linux-android34`
  (NDK 27.2.12479018, sysroot + includes del SDK/thirdparty; stub generado de
  `rex/version.h` en `/tmp/rexgen` + `-DREXGLUE_BUILD_TITLE='"test"'`): **PASS** para
  `stfs_data_file.cpp`, `stfs_asset_source.cpp`, `stfs_container_file.cpp`,
  `stfs_container_device.cpp`, `runtime.cpp`, `rex_app.cpp` y **`android_main.cpp`**
  (con `-DREX_APP_NAME='"childoflight"' -DREX_HAVE_ADRENOTOOLS=0`).
  `pipeline_cache.cpp` (Vulkan): el TU parsea entero salvo 2 errores preexistentes en
  `:2977`/`:3438` (`FLAGS_vulkan_dynamic_rendering_storage_` sin declarar — identificador
  generado por codegen, ajeno a mi edición; mis regiones `:422-436` y `:512-525` no tienen
  errores). `d3d12/pipeline_cache.cpp` no se puede comprobar en Linux (no se compila en
  Android; solo lo compila el build de Windows/escritorio — lo verifica CI).
  Nota: `-std=c++20` falla por `std::byteswap` preexistente en `include/rex/types.h:44` —
  el SDK necesita C++23.
- **Auditoría OpenCode** (`opencode run -m freellmapi/auto`, 2026-11-13): los 14 ficheros
  revisados, **14/14 OK, cero bloqueos**. Confirmó: propiedad del fd (guard RAII `FdCloser`,
  `CloseFiles()` resetea `fd_file_`); corrección de `pread`/`ReadAt` (bucle EINTR, rechazo
  SVOD multi-fichero); el coalescido con la validación de `block_count` intacta y rangos
  `ReadSync` exactos; comportamiento de modo ruta preservado; orden
  `HasStfsAssetSource()` capturado antes de `Setup` / `TakeStfsAssetSource()` en `SetupVfs`;
  los rewinds en el sitio correcto; la rama `asset:` de `android_main` (detección, triple
  ausente, raíces no-asset sin cambios).
- **Bionic `"a+"`**: confirmado por el manual (glibc lee desde el principio, Bionic desde el
  final) + prueba local glibc (`fopen("a+b")` → `ftell=0`, la cabecera se lee bien). El
  borrado en cada arranque en Android es consecuencia directa del código (fread sin rewind
  → cabecera no encontrada → truncado a 0).
- **`.ipk` / `.srt`**: verificado parseando los 4 `.ipk` reales (magic, conteos, 61 `.srt`
  en `child_of_light_x360.ipk`, 0 en los demás; `plan_logo.es.srt` en ningún sitio).
- **WMV**: verificado por ffprobe sobre los `.wmv` reales (WMV3 + WMA Pro, 9 pistas en la
  intro) y por lectura del código (sin decodificador; `XamLoadExtraAVCodecs2` stub).
- **No hay `game.stfs` en el workspace**: el tiempo de montaje y el comportamiento
  `.ipk`-sobre-STFS **no están medidos** — solo razonados desde el código. Decirlo claro.
- **No hay log de v1.3 en el workspace** para comprobar `Created N graphics pipelines from
  Vulkan storage` ni `XamLoadExtraAVCodecs2 STUB` (solo `col-bug-report_*.txt` en
  `~/workspace/user/files/`; los grep no encontraron esas cadenas). Lo debe comprobar el
  parent/usuario en el log del teléfono.

## 8. Qué debe probar el usuario (checklist)

1. **Arranque v1.4-full (asset)**: el juego arranca sin pantalla de importación y sin
   copiar nada. En el log debe aparecer el `ALOGI` de `android_main` con fd/offset/length
   y el montaje STFS; **no** debe aparecer `--game_data_root was not provided` ni
   `Entrypoint XEX not found`. Si aparece `ALOGE` de triple ausente, el puente JNI no
   entregó el fd (lado APP).
2. **Segunda entrada más rápida**: entrar, salir, volver a entrar. La segunda carga debe
   ser **claramente más corta** que la primera (la caché `.xsh`/`.xpso` ya no se borra).
   En el log de la segunda entrada debe aparecer
   `Created N graphics pipelines from Vulkan storage` (si no aparece nunca, la caché se
   sigue truncando — avisar con el log).
3. **El juego completo funciona desde el STFS**: jugar, guardar, cargar, cambiar de zona
   (lecturas `.ipk` aleatorias), sonido. Si algo falla solo en la variante asset y no en
   la extraída, es el camino STFS (anotar el primer error `e: file` literal del log).
4. **La intro SEGUIRÁ saltándose en v1.4** (no hay decodificador WMV todavía) — no es un
   fallo de esta versión. El `NtCreateFile FAILED ... plan_logo.es.srt -> 0xc000000f` es
   ruido esperado (ese fichero no existe en el juego original). Comprobar en el log si
   aparece `XamLoadExtraAVCodecs2 STUB` (confirmaría la causa del §2.2).
5. **El menú**: anotar si tras el título aparece el menú o salta directo al gameplay
   (sigue sin explicación VFS; dato para el worker de presenter).
6. **Tamaño total**: la variante full debe quedar < 2 GB instalada, sin duplicado
   (el STFS vive dentro de la APK; nada se extrae a disco).

## 9. Límites honestos / trabajo que queda fuera

- **Películas**: necesitan un decodificador WMV3/WMA Pro (FFmpeg VC-1 + WMA Pro, frames a
  texturas, selección de pista `spa`). Trabajo grande, de otro worker. Sin él, intro y
  logo se saltan siempre.
- **Menú que se salta**: sin causa VFS encontrada; pendiente del log completo y del worker
  de presenter.
- **`VkPipelineCache` del driver**: no persistido por diseño; no se tocó (caveat de deadlocks
  en Mali, MW2). Si la segunda carga sigue siendo lenta tras el arreglo `"a+"`, el
  siguiente paso es la precarga asíncrona de arranque (§5), no la persistencia del driver.
- **Montaje STFS sin medir**: sin `game.stfs` en el workspace, el tiempo de montaje, el
  coste por lectura y el `.ipk`-sobre-STFS son razonamientos desde el código, no medidas.
  La primera prueba real es en el teléfono del usuario.
- **Si montar el STFS desde el asset resultara inviable en la práctica** (p. ej. el fd de
  `openFd` no sobrevive al hilo nativo, o el STFS empaquetado no es el esperado), el
  bloqueador concreto estaría en `android_main.cpp:575` (puente) o en
  `stfs_container_device.cpp:138` (rechazo multi-fichero) — y la alternativa real sería
  volver al montaje host-path con extracción (la variante full actual de 3.26 GB), que el
  usuario ya rechazó. No hay precedente en ningún port de leer el juego desde dentro de
  la APK (P0 #10, informe de ports).

# Fase 5 — RENDER v1.4 (el título, port nativo Xbox 360 → Android)

Trabajo del worker RENDER. Base: v1.3, HEAD 205b41c. Árbol: `~/workspace/col-recomp/`.
Raíz del SDK en este documento: `tools/rexauto/android/sdk/rexglue-sdk/`
(abreviada `SDK/`). No se pusheó nada; no se construyeron APKs (el árbol de
build es compartido; el coordinador construye al final).

Alcance: Bug 1 (título aplastado) + Bug 6 (banda blanca / errores de textura)
+ rendimiento (contención de mutex, caché de shaders/pipelines, coste DXT)
+ P0 #1, #2, #4, #5 y P1 #6, #7, #8 de `analysis/FASE5-PORTS-OTROS.md`.
No se tocó la investigación de voces en español (otro coordinador).
No se editó `texture_cache.cpp` (de otro worker; solo lectura para diagnosticar).

Regla seguida: cada afirmación está verificada contra el código real o el
registro real. Lo que no se puede verificar sin el teléfono se marca así y se
dice el dato exacto que falta.

---

## 1. Bug 1 — el título no llena la pantalla: VEREDICTO

**El presentador está exonerado (verificado por tres vías). El recorte está
dentro de la imagen de salida del guest: el juego dibuja el fondo del título
solo en el 53% superior del frontbuffer; el texto de la UI sale en posiciones
correctas de fotograma completo debajo del corte.**

### 1.1 Medidas (no estimaciones)

Capturas medidas con Python/PIL. Las capturas son 2048×945 = 2340×1080
escalado (×1,1426); las medidas de abajo ya están en píxeles reales (2340×1080).

| Captura | Medida |
|---|---|
| Título (52958_3_u810.jpg) | Arte del título: rect (210, 0, 1920, 576) px reales. Corte nítido de 1 fila en y≈576 (fila 503 media 73,6 → fila 505 media 3,4). Pilares x 0–184 y 2151–2340. |
| Cargando (52960_5_spvo.jpg) | Logo redondo: círculo completo centrado en (≈1170, 540, r≈139), cortado en y≈577 — ~62% superior visible, corte plano. Texto "Cargando..." visible debajo, en y≈990. |
| Título, banner "Pulsa A" | Visible DEBAJO del corte, en y real 851–920. |
| Logo el motor (52954) | CORRECTO: letterbox 16:9 completo. |
| Gameplay (52963/52962) | CORRECTO: 1920×1080 con pilares laterales. |

Coordenadas del guest: el corte en y presentada 576 ↔ y guest 384 (÷1,5;
fotograma guest 1280×720). El texto de la UI se dibuja en posiciones
correctas de fotograma completo mientras el arte de fondo se corta en y≈576.
Esa disociación (fondo cortado, texto entero) descarta un error de
viewport/scissor global del presentador: un viewport mal puesto cortaría
también el texto.

### 1.2 El presentador, exonerado

`Presenter::GetGuestOutputPaintFlow` (`SDK/src/ui/presenter.cpp:857-1022`)
usa `surface_width/height_in_paint_connection_` (el tamaño real de la
superficie), no el tamaño del render target del host. Ninguna combinación de
tamaño de superficie obsoleto (incluida la vertical 1080×2340, que daría
(0,400,2340,281)) ni de aspecto de pantalla produce el rect medido
(210,0,1920,576). Con la superficie correcta el flujo da (210,0,1920,1080).
El pintado final usa viewport/scissor de swapchain completo
(`SDK/src/ui/vulkan/vulkan_presenter.cpp:2000-2004`) y el rect del flujo.
Conclusión: **el recorte está dentro de la imagen de salida del guest**.

El fix de Fase 4 SÍ está presente en v1.3 y funciona: hint
`SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft")` en
`tools/rexauto/android/app/src/main/cpp/android_main.cpp:519`, antes de
`SDL_InitSubSystem(VIDEO)` (:521), más el override `setOrientationBis` en
`MainActivity.java:57-69`. Que el gameplay salga correcto lo prueba; el bug
del título es otro defecto distinto.

Existe además una guarda en el flujo (`presenter.cpp:~1007-1024`): si la
salida calculada queda anclada arriba-izquierda y más pequeña que el render
target, registra `Presenter: expanding guest output from {}x{} to host render
target {}x{}...` y la expande. Esa guarda NO se dispara en el caso del título
(la salida está centrada en x=210, no anclada en x≈0), así que no lo cura ni
lo explica.

### 1.3 Lo que queda por precisar (mecanismo guest)

El mecanismo exacto dentro del guest (resolve del render target vs
viewport/scissor de la escena del título) no se puede determinar sin el
registro del teléfono. Para eso se añadió un log de diagnóstico (ver §5.4):
en el próximo arranque, la línea `VulkanPresenter: paint geometry guest
{}x{} aspect {}:{} swapchain {}x{} output ({},{}) {}x{}` dirá si el
frontbuffer del guest en la pantalla del título es 1280×720 con un rect de
flujo completo (→ el recorte está dentro de la imagen del guest, hay que
buscarlo en el render target del título) o si el frontbuffer ya llega corto
(→ el juego presenta un frontbuffer corto en el título).

**Dato que falta (pedir al teléfono):** `childoflight.log` de v1.3 con las
líneas `VulkanPresenter: paint geometry ...`, `Created ...x... swapchain`,
`Presenter: expanding guest output ...` y `XELOG_GPU PRESENT: ...`.

### 1.4 P0 #5 — par 16/9 y 9/16 en el binario: ENCONTRADO, pero NO es el título

Búsqueda verificada en el binario real:

- `private/extracted/default.xex` (14.036.992 B): **0 coincidencias** del par
  (LE ni BE, en ningún orden). Las secciones de datos del XEX van comprimidas.
- `private/extracted/default.pe` (14.286.848 B, la imagen descomprimida del
  XEX, base 0x82000000): **par adyacente big-endian en offset de fichero
  0x61284** — `16/9 = 0x3FE38E39` en 0x61284 y `9/16 = 0x3F100000` en
  0x61288, dentro de `.rdata` (VA 0x00061284). **Dirección guest:
  0x82061284 (16/9) y 0x82061288 (9/16).** El contexto lo confirma como un
  pool de constantes flotantes: π en 0x6126c/0x61278, 1e-5 en 0x61270/0x6127c,
  FLT_MAX en 0x61274/0x6128c, √2 en 0x61280, 18,225 y 32,4 en 0x61290/0x61294
  (32,4/18,225 = 16/9).

¿Lo usa el código? No se encontró consumidor directo:

- Ninguna referencia literal a `0x82061284` / `0x82061288` en `recomp/ppc/`.
- El código recompilado carga del pool con `lis rX,-32250` (= 0x82060000)
  + `PPC_LOAD_U32(ctx.rX.u32 + offset)` con offsets decimales. Un barrido
  por flujo de datos sobre todo `recomp/ppc/` buscó funciones que tengan a la
  vez un `lis -32250` y una carga `+4740`/`+4744` (0x1284/0x1288) desde el
  mismo registro: **una sola candidata** (`sub_82A5C830`,
  `recomp/ppc/ppc_recomp.264.cpp`), y ahí la carga sale de r3 mientras la
  base del pool está en r4 — no es el par. El otro caso (`sub_82521FC8`,
  `recomp/ppc/ppc_recomp.130.cpp:9458`) carga r29+4740/4744, pero r29 es
  `r31+16` (puntero a un struct, no la base del pool). No hay construcción
  `addis` de 0x82060000 fuera de los `lis`.
- Total: 5863 sitios `lis -32250` (la base de `.rdata` se usa para muchísimas
  constantes, no solo aspecto) y solo 7 cargas `+4740/+4744` en todo el árbol.

**Veredicto P0 #5: el par existe, pero no hay evidencia de que el título lo
use, y las constantes describen 16:9 mientras el gameplay ya hace letterbox
correcto. No se parchean en v1.4.** El modo de vídeo guest ya está "a juego":
la ventana se crea a 1280×720 (`SDK/src/ui/rex_app.cpp:356`) y el presentador
la encaja en 2340×1080 con pilares. Cambiar las constantes sin identificar al
consumidor sería disparar a ciegas.

---

## 2. Bug 6 — banda blanca lavada / errores de textura

### 2.1 Lo medido

- Gameplay 52963: escalón brusco de brillo en y real 576 (+42,8) con banda
  brillante 238–244 en y real 583–788.
- Gameplay 52962: banda más suave ~686–788, brillo ~195 frente a cielo ~170,
  sin escalón nítido en 576.
- No se puede resolver solo con capturas si es niebla/agua propia del juego
  o una textura fallida. El negro bajo el título **es el Bug 1, no un error
  de textura**.

### 2.2 Hechos verificados en código (causas posibles, ordenadas)

1. **Draws con vertex memexport que fallan en Mali.** En Mali-G57
   `vertexPipelineStoresAndAtomics` es falso (ver `docs/RENDIMIENTO.md`,
   perfil Mali punto 3) → cualquier draw cuyo vertex shader use memexport
   FALLA individualmente (`SDK/src/graphics/vulkan/command_processor.cpp:3641-3647`;
   el de píxel necesita `fragmentStoresAndAtomics`, :3680+). Línea del log:
   draws con vertex memexport "will fail individually" (fallback a compute
   shaders). Un draw de niebla/agua que dependa de memexport y falle dejaría
   justo una franja sin el efecto (o con el color base lavado). **Hipótesis
   nº 1.**
2. **Decodificación DXT (fallback).** Mali-G57 no tiene formatos BC →
   DXT1/2_3/4_5/DXN se decodifican a RGBA8 con load shaders de cómputo en GPU
   (`SDK/src/graphics/vulkan/texture_cache.cpp:2475` y ~:2400–2520), 4× memoria
   + 4× ancho de banda de subida + un pase de decodificación por cómputo en
   cada invalidación. Línea del log: `Texture format k_DXT1/2_3/4_5/DXN/DXT5A
   falling back ... to an uncompressed format ... multiplies texture memory
   and upload bandwidth 4x`. Es un load shader en GPU, NO decodificación por
   CPU (corrección al diagnóstico inicial). Un fallo aquí daría texturas
   blancas/negras, no una banda suave. **Hipótesis nº 2.**
3. **Frames con pipelines placeholder presentados/salteados.** Con
   `async_shader_compilation` (defecto true,
   `SDK/src/graphics/command_processor.cpp:77`) +
   `vulkan_async_skip_incomplete_frames` (defecto true,
   `SDK/src/graphics/vulkan/command_processor.cpp:59-62`), cualquier frame
   que use un pipeline placeholder tiene su presentación SALTADA entera
   (`command_processor.cpp:2300-2315`). Línea del log: `Skipping Vulkan frame
   presentation due to async placeholder draw usage in this frame`. Esto es
   un mecanismo verificado de caída de frames (contribuye a los ~6 fps) y, en
   el peor caso, deja ver frames viejos. **Se desactiva por defecto en v1.4
   (ver §5.5); el usuario puede reactivarlo en settings.txt.**
4. **La niebla/agua propia del juego.** No descartable sin el log ni una
   captura de GPU.

**Discriminador (líneas a buscar en el próximo `childoflight.log`):**
`Vertex shader memexport draw encountered` / `Skipping Vulkan frame
presentation...` / líneas de fallback DXT, junto con qué textura/draw
estaba en pantalla en ese momento. Sin ese log no se puede señalar la
textura exacta; `texture_cache.cpp` no se editó (es de otro worker — si la
causa cae ahí, el arreglo es el descrito en el informe del worker VFS o en
§6.3).

---

## 3. Rendimiento — cuello de botella principal: contención de mutex

### 3.1 Causa raíz verificada: `WaitMultiple` hacía polling

Perfil real del teléfono (v1.3): Main XThread 96%, AK::IOThread 94%,
`pthread_mutex_unlock` 8%, `pthread_mutex_trylock` 6,5%,
`__kernel_clock_gettime` 7,4%, `nanosleep` 5,4%, `sched_yield` 1,5%,
GPU Commands solo 14%. La firma es idéntica a la de Rayman Origins.

Causa en código (antes del arreglo):
`PosixConditionBase::WaitMultiple`
(`SDK/src/core/threading_posix.cpp:335-420`, versión anterior) hacía, por
cada waiter y en bucle: `pthread_mutex_trylock` de TODOS los handles del
conjunto (todo o nada), comprobar, y `sleep_for(1 ms)`. Cada vuelta cuesta
N trylocks + `steady_clock::now()` + nanosleep, aunque no haya pasado nada.
Con decenas de multi-waiters (hilo de audio, hilos del guest) eso es un
núcleo entero de tráfico trylock/clock por waiter. El path de un solo handle
(`Wait`, :341-380) sí bloqueaba bien en su condition variable; solo el
multi-handle hacía polling.

Otros sitios trylock (menores, no tocados): `Clock::UpdateGuestClock`
(`SDK/src/core/clock.cpp:70-88`, try_lock por consulta de tiempo) y
`XmaContext::Block(bool poll)` (`SDK/src/audio/xma_context.cpp:158-166`,
llamado desde `xma_decoder.cpp:234` y :318-322). El 94% de AK::IOThread
encaja con girar en esperas XMA/audio — el arreglo de `WaitMultiple` lo
cubre si esas esperas pasan por multi-handle; si alguna espera XMA es de un
solo handle con polling propio, queda como residuo a medir en el próximo
perfil.

### 3.2 P0 #1a — WaitMultiple bloqueante (IMPLEMENTADO, código propio)

**Licencia (definitiva, verificado):** `SDK/LICENSE` es estilo **BSD-3**
(Copyright 2026 Tom Clay; partes derivadas de Xenia; redistribución con/sin
modificación permitida con aviso). Rayman Origins es GPL-3.0. **No se copió
ninguna línea de los parches de Origins**: el mecanismo (contador de
generación global + una sola variable de condición) se reimplementó a partir
de la descripción, escrito para este archivo. Los diffs de los parches 0006
y 0005 se leyeron completos para entender el mecanismo (no se reproducen
aquí).

Cambios en `SDK/src/core/threading_posix.cpp`:

- **Canal de wake-up compartido** (:285-320): `MultiWaitChannel` (mutex +
  condition_variable + contador de generación `uint64_t` + contador atómico
  de waiters) y `WakeMultiWaiters()` (no-op barato si nadie está en un
  multi-wait: comprueba el contador atómico antes de tomar el mutex).
- **`PosixConditionBase::NotifyAll()`** (:500-505): `cond_.notify_all()` +
  `WakeMultiWaiters()`. Los 7 sitios que pueden hacer que un handle pase a
  señalizado usan ahora `NotifyAll()` en vez de `cond_.notify_all()` directo:
  `Event::Signal` (:544), `Semaphore::Release` (:581),
  `Semaphore::post_execution` (:589, el conteo restante debe despertar a
  otros waiters), `Mutant::Release` (:613, solo cuando el conteo llega a 0),
  `Timer::Signal` (:645), `Thread::Terminate` (:1073) y la salida de
  `ThreadStartRoutine` (:1549). Los `state_signal_.notify_all()` (:1022,
  :1529) se dejan como están a propósito: son la CV de estado del hilo
  (suspend/resume), no de señalización de handle.
- **`WaitMultiple` reescrito** (:383-486): lee la generación ANTES de
  comprobar los handles (así ninguna señal se pierde entre la lectura y el
  sueño), comprueba una vez, y duerme en el canal compartido con predicado
  (`wait`/`wait_until` con `generation != seen_generation`) hasta que algún
  handle señalice o venza el plazo. wait-all bloquea los handles en orden de
  dirección (ordenados + deduplicados) y consume todas las señales con los
  bloqueos puestos; wait-any comprueba cada handle bajo su propio bloqueo y
  consume la señal (auto-reset) con el bloqueo puesto, así ningún segundo
  waiter observa la misma señal. Los bloqueos usan `LockHandleMutex`
  (:508-525), que conserva el manejo de robust-mutex con `EOWNERDEAD` de
  Linux no-Android. El wrapper público (`rex::thread::WaitMultiple`,
  :1313-1340) reenvía el path no-alertable directamente aquí (:1326); el path alertable
  trocea en rebanadas de `kAlertablePollSlice` = 1 ms (:1171) por diseño
  (tiene que drenar callbacks de usuario/APC), así que los waits alertables
  siguen despertando como mucho cada 1 ms — inherente a la semántica
  alertable, no un bug.
- El llamador (`XObject::WaitMultiple`, `SDK/src/system/xobject.cpp:262`)
  ignora el índice en wait-all (llama `WaitCallback()` a todos y devuelve
  `X_STATUS_SUCCESS`), así que devolver 0 en wait-all es correcto (es el
  comportamiento Win32 `WAIT_OBJECT_0`); en wait-any el índice sí importa y
  se devuelve el del handle señalizado.

Efecto esperado (medido en Origins con el mismo mecanismo): el worker de
audio pasó de 73% a 7% de un núcleo. En el título debería verse como una caída
fuerte de trylock/unlock/clock_gettime en el próximo `profile.txt`.

### 3.3 P0 #1b — mprotect antes de parsear /proc/self/maps (IMPLEMENTADO)

`AllocFixed` (`SDK/src/core/memory_posix.cpp:290-399`, antes del arreglo):
cada `kCommit` sobre una reserva existente hacía `mmap(MAP_FIXED_NOREPLACE)`
→ EEXIST → `IsRangeFullyMapped()` (abrir + parsear `/proc/self/maps` entero,
línea por línea, por commit) → `mprotect`. El espacio de direcciones del
guest se reserva por adelantado, así que casi cada commit pagaba el paseo
completo por maps.

Cambios (código propio, mismo mecanismo que el parche 0005):

- **Fast path Linux** (:333-355): para `kCommit` con dirección y longitud
  alineadas a página, probar `mprotect` PRIMERO. `mprotect` falla con ENOMEM
  salvo que todo el rango esté ya mapeado, así que intentarlo es seguro; en
  caso de fallo se sigue al path `mmap` de abajo (que reserva el rango).
  Espejo de la rama macOS que ya existía (:346-361).
- **Path EEXIST** (:385-395): `mprotect` directo en vez de
  `IsRangeFullyMapped()`. Un rango parcialmente mapeado falla aquí con
  ENOMEM exactamente igual que fallaba antes (nullptr en ambos casos); un
  rango totalmente mapeado tiene éxito igual que antes. Semántica idéntica,
  sin el parseo.
- **`IsRangeFullyMapped` eliminado** (era su único uso, :394). `Protect()` /
  `QueryProtect()` no se tocaron: el path de faltas usa el mapa cacheado
  (`FindEntryForAddressCached`); el parseo sin caché solo se usa cuando
  `Protect` pide `out_old_access`, que no es el path caliente de commit.
- La invalidación de la caché de maps (`InvalidateLinuxMapsCache()`) sigue
  en todos los caminos de éxito.

Nota: `tools/rexauto/android/patches/rexglue-sdk-v0.10.0-android-perf.patch`
contiene la versión ANTIGUA de `AllocFixed` con `IsRangeFullyMapped`
(líneas 119, 159 del parche). Ese directorio `patches/` es archival: ningún
script de build lo referencia (ni `build.gradle.kts` ni `rexauto.py` lo
aplican; Gradle compila `sdk/rexglue-sdk` tal cual). **El parche queda
obsoleto respecto al árbol y hay que regenerarlo o anotar la divergencia
antes de que alguien lo reaplique** — no lo edité porque es un artefacto
generado de una ronda anterior y su base es el SDK prístino v0.10.0.

### 3.4 P0 #2 — VK_SUBOPTIMAL_KHR: el bug de Origins NO existe aquí (verificado)

El informe de otros ports sospechaba que el renderer re-creaba el swapchain
en cada present por SUBOPTIMAL (Origins pasó de 43–46 a 60 fps con ese
cambio). Rastreo completo de la cadena en nuestro código:

- `vkAcquireNextImageKHR` trata `VK_SUCCESS`/`VK_SUBOPTIMAL_KHR` igual
  (break, `SDK/src/ui/vulkan/vulkan_presenter.cpp:1577-1579`).
- `vkQueuePresentKHR` SUBOPTIMAL → `PaintResult::kPresentedSuboptimal`
  (`vulkan_presenter.cpp:2290`), un resultado DISTINTO, no un re-create.
- Consumidor: `Presenter::PaintAndPresent`
  (`SDK/src/ui/presenter.cpp:1509-1517`): `kPresentedSuboptimal` marca
  `kConnectedOutdated` SOLO si un present anterior devolvió SUCCESS
  (transición óptimo→subóptimo). `PaintFromUIThread` (:468-470) reconecta
  entonces (`UpdateSurfacePaintConnectionFromUIThread` → destruir + crear
  swapchain). Tras una reconexión,
  `surface_paint_connection_was_optimal_at_successful_paint_` queda en false
  (:1398), así que un flujo ESTABLE subóptimo no genera reconexiones: solo
  una por cada transición óptimo→subóptimo.
- El resize sí es el disparador autorizado: `window.cpp:558` →
  `OnSurfaceResizeFromUIThread` (`presenter.cpp:390`) → reconexión.

**Veredicto: no hay re-creación por frame; no se cambia nada.** El cambio
de "no marcar outdated ante subóptimo" se evaluó y se descartó: el
comportamiento actual es correcto (la marca es el salvavidas para el caso
en que un resize llegue sin evento) y tocar la lógica compartida del
presenter arriesga regresiones en escritorio sin beneficio medido en
Android (en estado estable no hay churn). **Contar re-creaciones reales en
el log del teléfono:** `grep -c "Created .* swapchain" childoflight.log`
(cada creación registra `VulkanPresenter: Created {}x{} swapchain`,
`vulkan_presenter.cpp:1414`). No se pudo contar aquí: no hay ningún
`childoflight.log` de v1.3 en el workspace (los únicos .log son de agentes
y herramientas).

Pre-transform/rotación (también verificado): el swapchain se crea con
`preTransform` IDENTITY si está soportado, si no INHERIT
(`vulkan_presenter.cpp:1355-1358`); `currentTransform` NO se lee en ningún
sitio del presentador. En un teléfono de orientación natural vertical, en
landscape la superficie reporta rotación 90°, así que con preTransform
IDENTITY el compositor rota la imagen y cada present puede devolver
SUBOPTIMAL — que nuestro código tolera sin re-crear (ver arriba). Cambiar a
`currentTransform` obligaría a renderizar pre-rotado (transponer el extent);
no se toca sin el dispositivo para probarlo.

### 3.5 P0 #4 — Afinidad de hilos (IMPLEMENTADO)

- La API ya existía: `rex::thread::Thread::set_affinity_mask(uint64_t)`
  (`SDK/include/rex/thread.h:439`) y `XThread::thread()` da acceso al hilo
  host (`SDK/include/rex/system/xthread.h`, `thread()`). No hizo falta
  tocar `XThread`: su `SetAffinity` es la afinidad del GUEST (mapea índices
  de CPU de 360 a una sola CPU host y está vetada por
  `--ignore_thread_affinities=true`); esto es una decisión HOST sobre un
  hilo, distinta.
- Helper `GetBigCoreAffinityMask()` + `ReadSysInt` / `ParseCpuNumber` /
  `ReadOnlineCpuMask` (`SDK/src/ui/rex_app.cpp:466-590`, solo Android):
  lee `/sys/devices/system/cpu/online` (rangos `0-7`, `0-3,5-7`, `0`),
  clasifica por `cpu_capacity` (o `cpuinfo_max_freq` si no existe — nunca
  se mezclan), toma las CPUs con el valor máximo, devuelve 0 si el
  dispositivo es homogéneo o no se puede determinar, e intersecta con la
  afinidad real del proceso (`sched_getaffinity(0, ...)`) para no pinchar
  fuera del cpuset permitido. Nada se asume del SoC (el G99 es 2×A76 +
  6×A55, pero otro teléfono puede diferir).
- Llamada en `ReXApp::LaunchModule` (`rex_app.cpp:623-630`): después de
  `PrepareModuleLaunch()` y antes de `Resume()`, si la máscara es no nula,
  `main_thread->thread()->set_affinity_mask(big_core_mask)` + log
  `Pinning guest main thread to big cores (affinity mask 0x{:X})`. Solo el
  hilo principal del guest se pincha; el resto sigue migrando libremente
  (coherente con el comentario de `android_main.cpp:578-581`).
- Verificación funcional: el helper (extraído literal, con la raíz sysfs
  redirigida a un árbol falso) pasó 5/5 casos — split big/little → máscara
  de los grandes; homogéneo → 0; fallback por frecuencia → correcto;
  `online` malformado → 0; intersección con la afinidad del proceso (la VM
  solo permite 4 CPUs: una máscara con CPUs 6-7 se intersecta a 0).

### 3.6 Caché de shaders/pipelines persistente

- **El almacenamiento .xsh + pipeline storage SÍ persiste por diseño:**
  `<cache>/shaders/shareable/<TITLEID>.fbo.vk.xpso` (descripciones de
  pipelines, validadas con XXH3) + `<TITLEID>.xsh` (ucode de shaders del
  guest), abiertos `"a+b"` (`SDK/src/graphics/vulkan/pipeline_cache.cpp:422,
  512`), releídos al arrancar (:441-480, :525-575), con re-creación de
  `vkCreateGraphicsPipelines` por cada pipeline guardado al lanzar,
  bloqueante (`SDK/src/ui/rex_app.cpp:638`,
  `InitializeShaderStorage(..., true)`; `cache_root` = `user_dir/cache` =
  `<external>/data/cache`, `rex_app.cpp:138-146`; `store_shaders` por
  defecto true, `graphics_system.cpp:39`).
- **Corrección a un diagnóstico anterior: el bug Bionic `"a+"` YA ESTÁ
  ARREGLADO en el árbol actual.** Los dos `fopen("a+b")` van seguidos de
  `rex::filesystem::Seek(..., 0, SEEK_SET)` con comentario explicativo
  (`pipeline_cache.cpp:~435` para el storage de pipelines y `:~523` para
  el de shaders): "Bionic opens 'a+' streams positioned at the end of the
  file. Without this rewind the header fread() below finds nothing and the
  stored shaders are truncated on every launch". No hizo falta ningún cambio.
- **Lo que NO existe: `VkPipelineCache` persistente.** Grep de
  `VkPipelineCache|vkCreatePipelineCache|GetPipelineCacheData` en todo el
  backend Vulkan = cero resultados. Cada arranque re-ejecuta la compilación
  de pipelines del lado del driver para todos los pipelines guardados → la
  carga de varios minutos se repite. **Arreglo descrito (no implementado en
  v1.4, es trabajo nuevo y con riesgo):** fichero
  `<cache>/shaders/shareable/<TITLEID>.fbo.vkcache`;
  `vkCreatePipelineCache` con `pInitialData` al arrancar, pasarlo a
  `vkCreateGraphicsPipelines`, `vkGetPipelineCacheData` al apagar y
  guardarlo; validar la cabecera (`VK_PIPELINE_CACHE_HEADER_VERSION_ONE`,
  vendorID/deviceID/UUID). Referencia: el sample `pipeline_cache` de
  Vulkan-Samples re-crea en 24,4 ms con caché frente a 50,4 ms sin ella en
  Mali G76. **Riesgo anotado:** MW2 desactivó el VkPipelineCache
  persistente en Mali por deadlocks — hay que probarlo en el teléfono antes
  de darlo por bueno.

### 3.7 Coste DXT

Decodificación por load shader en GPU (no por CPU), 4× VRAM + 4× ancho de
banda de subida, con un pase de decodificación por cómputo en cada
invalidación (ver §2.2 punto 2). No existe caché persistente de texturas
transcodificadas en ningún port (trabajo nuevo). Origins decodifica
DXT→RGBA8 en CPU y aun así llega a 60 fps en un S23: el coste está dominado
por otras cosas (el mutex y la compilación de pipelines), así que el DXT no
es el primer objetivo.

### 3.8 P1 #6 — register-as-local: evaluado, NO implementado en v1.4

Verificado: el código generado hace hoy lo contrario —
`recomp/ppc/ppc_recomp.0.cpp:148`: `ctx.ctr.u64 = ctx.r28.u64` (CTR/XER/LR
viven en `PPCContext`, `recomp/ppc/ppc_context.h:379,382,385`). Tamaño del
cambio: 280.871 escrituras a CTR/LR/XER y 74.742 referencias a `ctx.ctr` en
el código recompilado. Exige parche en XenonRecomp + re-correr el recomp
completo + re-verificar todo. Efecto esperado según el informe de otros
ports: −⅓ instrucciones, −⅕ ciclos del hilo guest (Gears). **Queda como
siguiente paso; no se hace a medias.**

### 3.9 P1 #7 — `clear_memory_page_state=false`: YA ESTABA

Verificado en `tools/rexauto/android/app/src/main/cpp/android_main.cpp:568`.
No hizo falta ningún cambio. Queda pedir la medida de re-subidas por frame
en el próximo perfil (Gears: 4,6 → 9,4 fps a 222 MHz fijos; render passes
1.600 → 155).

### 3.10 P1 #8 — BC→ETC2 en CPU / −2 mips: descrito, no implementado

No existe ningún cvar de mip/LOD en el backend Vulkan (grep de
`mip|lod_bias` en cvars = 0), así que la alternativa barata (−2 mips en
perfil de poca memoria, Skate 3: 16× menos bytes) necesitaría código nuevo
en `texture_cache.cpp` (archivo de otro worker). La transcodificación
BC→ETC2 en carga (vía Unleashed) es trabajo nuevo sin precedente en los
ports (ninguno transcodifica a ASTC ni guarda caché persistente de texturas
transcodificadas). No se implementó a ciegas; queda como opción medida
cuando el mutex y la caché de pipelines estén resueltos.

---

## 4. Cambios implementados (resumen)

| # | Archivo | Líneas | Cambio |
|---|---|---|---|
| P0 #1a | `SDK/src/core/threading_posix.cpp` | ~270-530 | `WaitMultiple` bloqueante por contador de generación + CV compartida; `NotifyAll()` en los 7 caminos de señalización; `LockHandleMutex()` con manejo robust-mutex. |
| P0 #1b | `SDK/src/core/memory_posix.cpp` | 333-355, 385-395 | Fast path `mprotect` primero para `kCommit` alineado; `mprotect` directo en el path EEXIST; `IsRangeFullyMapped` eliminado. |
| P0 #4 | `SDK/src/ui/rex_app.cpp` | 466-590, 623-630 | `GetBigCoreAffinityMask()` (lee la topología de `/sys`); pin del hilo principal del guest a los núcleos grandes antes de `Resume()`. |
| Bug 1 diag | `SDK/src/ui/vulkan/vulkan_presenter.cpp` | 1656-1700 | Log `VulkanPresenter: paint geometry ...` cuando cambia la geometría de pintado (frontbuffer, aspecto, swapchain, rect de salida). |
| Frame-skip | `tools/rexauto/android/app/src/main/cpp/android_main.cpp` | 621 | `--vulkan_async_skip_incomplete_frames=false` en los args built-in (settings.txt puede sobreescribirlo). |

Cambios que se evaluaron y se DESCARTARON con motivo: no re-crear el
swapchain ante SUBOPTIMAL (el bug no existe aquí, §3.4); parchear las
constantes 16/9–9/16 (sin consumidor identificado, §1.4); register-as-local
(§3.8); VkPipelineCache persistente (§3.6, trabajo nuevo con riesgo Mali).

---

## 5. Qué se verificó

- **Compilación sintáctica local (g++, C++20/C++23, sin build completo):**
  `threading_posix.cpp` limpio; `memory_posix.cpp` limpio (en C++23; el
  fallo en C++20 es `std::byteswap` en `include/rex/types.h:44`, preexistente
  y ajeno al cambio); `rex_app.cpp` limpio en Linux Y con `-D__ANDROID__`
  (la rama de afinidad compila; comprobado por preprocesado: 3 ocurrencias
  con `__ANDROID__`, 0 sin él); `vulkan_presenter.cpp` limpio.
  `android_main.cpp` no se pudo comprobar aquí: necesita el NDK
  (`android/log.h` no instalado en la VM); el cambio es una sola línea
  `args.emplace_back(...)` idéntica a las 8 anteriores y el nombre del cvar
  está verificado contra su definición (`command_processor.cpp:59`).
- **Lógica de afinidad probada contra un sysfs falso:** 5/5 casos (§3.5).
- **Semántica de `WaitMultiple` verificada contra el llamador:**
  `XObject::WaitMultiple` ignora el índice en wait-all; el índice de
  wait-any se devuelve correcto.
- **Auditoría OpenCode (freellmapi, 3 rondas):**
  - Ronda 1 (threading): 3 hallazgos — **los 3 refutados releyendo el
    código**: (1) supuesta "wake-up perdida" — falso, el sueño usa predicado
    (`generation != seen_generation`, :479/481), así que un bump previo al
    sueño hace que el predicado sea cierto y no se duerme; (2) supuesta
    señal de salida de hilo sin `NotifyAll` — falso, los únicos dos sitios
    que ponen `signaled_ = true` en un hilo (:1072, :1548) llaman a
    `NotifyAll()` (:1073, :1549); la línea citada (:1022) es la CV de estado
    de `Resume()`, no de señalización; (3) supuesto deadlock por handles
    duplicados en wait-any — falso, wait-any tiene un solo bloqueo vivo a la
    vez (el lock vive dentro del cuerpo del bucle); wait-all sí deduplica.
  - Ronda 2 (memory): **NONE FOUND**.
  - Ronda 3 (afinidad + log + cvar): 1 hallazgo (A1, "falta intersección con
    la afinidad del proceso") — **refutado**: la intersección existe en
    `rex_app.cpp:576-587` (`best_mask &= allowed_mask`); el auditor solo
    leyó hasta la línea 565 y no la vio. (Su afirmación de que el fallo
    sería `EFAULT` también es incorrecta: sería `EINVAL`.) El resto de
    puntos (parsing de `online`, seguridad de pinchar antes de `Resume`,
    guarda del log, validez de `effect_output_sizes[effect_count-1]`,
    override por settings.txt) confirmados correctos.
- **Licencia:** `SDK/LICENSE` BSD-3 verificado; ningún código GPL copiado
  (reimplementación desde la descripción).
- **Par 16/9–9/16:** verificado byte a byte en `default.pe` y mapeado a
  dirección guest vía la tabla de secciones PE (§1.4).
- **NO verificado (falta el teléfono):** ningún cambio se ha probado en un
  dispositivo real. La puerta de validación es CI + la prueba en el
  teléfono del usuario.

---

## 6. Qué debe probar el usuario en el teléfono (v1.4)

Meta: `docs/RENDIMIENTO.md` — 60 fps sostenidos a 720p (16,6 ms/frame).

1. **FPS y carga:** jugar 5 minutos en la misma zona que en v1.3 y comparar
   fps (¿suben los ~6 fps? ¿se acaban los tirones durante la compilación de
   shaders?). Medir el tiempo de "Cargando..." (¿baja de varios minutos? —
   la caché .xpso/.xsh ya persistía; el VkPipelineCache sigue pendiente, así
   que la primera carga seguirá siendo larga, pero las siguientes no
   deberían recompilar pipelines del driver... en realidad SÍ seguirán
   haciéndolo hasta que exista el VkPipelineCache: anotar el tiempo de la
   2ª carga igual que el de la 1ª).
2. **Título:** ¿sigue cortado en y≈576? Capturar la pantalla del título y
   la de "Cargando..." y medir el corte si persiste.
3. **Texturas:** ¿sigue la banda blanca lavada en el gameplay (52963/52962)?
   ¿Aparecen flashes de placeholder durante los primeros minutos (efecto
   del frame-skip desactivado)? Si el flashing es peor que los frames
   perdidos, reactivar el skip en `settings.txt` con
   `--vulkan_async_skip_incomplete_frames=true`.
4. **Enviar `childoflight.log` + `profile.txt` de la sesión** (el log está
   en `<external>/data/<app>.log`, ver `android_main.cpp:564`). Líneas
   a buscar en el log:
   - `VulkanPresenter: paint geometry guest ...` (título y gameplay) — el
     dato que decide el Bug 1.
   - `Created ...x... swapchain` — contar re-creaciones:
     `grep -c "Created .* swapchain" childoflight.log`.
   - `Pinning guest main thread to big cores (affinity mask 0x...)` — qué
     máscara se detectó en el G99.
   - `Skipping Vulkan frame presentation...` — ¿sigue apareciendo? (No
     debería, con el skip desactivado.)
   - `Vertex shader memexport draw encountered` + líneas de fallback DXT —
     el discriminador del Bug 6.
   - `Created N graphics pipelines from Vulkan storage` — ¿cuántos
     pipelines se reutilizan de la caché?
   - En `profile.txt`: ¿bajaron `pthread_mutex_trylock` / `unlock` /
     `__kernel_clock_gettime`? ¿Sigue AK::IOThread al 94%?

---

## 7. Riesgos y pendientes (para el coordinador)

- `tools/rexauto/android/patches/rexglue-sdk-v0.10.0-android-perf.patch`
  queda obsoleto (contiene el `AllocFixed` antiguo con
  `IsRangeFullyMapped`): regenerarlo o anotar la divergencia antes de que
  alguien reaplique los parches. El build de Gradle NO lo aplica (compila
  el árbol tal cual), así que no bloquea v1.4.
- El frame-skip desactivado puede mostrar flashing de placeholders durante
  el calentamiento de shaders; es reversible por settings.txt.
- El VkPipelineCache persistente es el arreglo que falta para la carga de
  varios minutos, pero con riesgo de deadlock en Mali (precedente MW2):
  implementarlo detrás de una prueba en el teléfono, no a ciegas.
- La afinidad pincha solo el hilo principal del guest. El hilo de comandos
  GPU ("GPU Commands" 14%) vive dentro del plugin y no es alcanzable desde
  `LaunchModule`; pincharlo sería un cambio en el backend Vulkan.
- Bug 1: si el log de geometría muestra frontbuffer 1280×720 con rect
  completo, el recorte es un problema de render target / viewport DENTRO del
  guest (título), y el siguiente paso es instrumentar el resolve del RT del
  título, no el presentador.

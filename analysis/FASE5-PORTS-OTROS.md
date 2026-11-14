# FASE 5 — Cómo hicieron jugables los demás ports nativos (investigación)

**Fecha:** 2026-11-13
**Estado:** Investigación completa. Solo investigación — no se modificó el árbol del port ni se construyó nada.
**Autores:** subagente coordinador + 2 investigadores en paralelo (borradores en `analysis/_draft-ports-rayman.md` y `analysis/_draft-ports-unleashed.md`).

**Convención:** **VERIFICADO** = leído literalmente en la fuente citada (repo, archivo, issue, log o notas de release). **INFERIDO** = conclusión razonada a partir de lo verificado, marcada como tal. Lo no leído se dice explícitamente.

---

## 0. Veredicto en una frase

**Los demás ports jugables no resolvieron nuestros cuatro problemas con un solo truco: los resolvieron con una pila de arreglos pequeños y medidos — casi todos de CPU, no de GPU — y con una decisión arquitectónica que nosotros aún no hemos tomado: casi ninguno usa la ruta GPU emulada genérica de ReXGlue; todos los jugables usan un renderer nativo o propio. Y ninguno, ni uno solo, lee el juego desde dentro de la APK: el < 2 GB sin duplicar es trabajo nuevo, pero el SDK ya trae la pieza clave (`StfsContainerDevice`).**

### Nuestro punto de partida (lo que ya sabemos del título en el teléfono)

| Problema | Estado medido en v1.3-full (teléfono real, Helio G99 / Mali-G57 MC2) |
|---|---|
| Lento | Main XThread 96 %, AK::IOThread 94 %, GPU Commands 14 %. Top: `pthread_mutex_unlock` 8 %, `__kernel_clock_gettime` 7,4 %, `pthread_mutex_trylock` 6,5 %, `nanosleep` 5,4 % |
| Texturas con errores | DXT/BC sin soporte en Mali → fallback a RGBA8 (4× memoria/ancho de banda + pase de decodificación en cada invalidación). TODO upstream abierto: `S3TC -> ETC2 / EAC` |
| Título sin llenar la pantalla | Fix Fase 4 (hint SDL + `setOrientationBis`) no lo curó en v1.3; el arte queda arriba ~55 %, negro abajo |
| Tamaño | APK 1,68 GB + datos extraídos 1,58 GB = 3,26 GB. Requisito del usuario: port 100 % oficial, sin importar nada, total < 2 GB |
| Carga | "Cargando..." varios minutos en la primera carga |

---

## 1. El mapa: quién es quién y qué camino tomó cada uno

Fuentes: `droidports.com/platforms/xbox-360/` (17 ports, leída 2026-11-13), `abduznik/awesome-android-native-ports` (210 ports), `recompedia.com/platform/xbox360`, Quiver. VERIFICADO.

| Port | Repo | Base GPU | Estado / nota |
|---|---|---|---|
| Rayman Origins Recompiled | `XDanfr/RaymanOriginsRecomp` (BelmanteGu) | **Renderer nativo propio** (rexgpu-null + D3D→Vulkan) | 60 fps Galaxy S23; 10 hallazgos de rendimiento medidos |
| Legends-Recomp | `edeegg/Legends-Recomp` | Ruta Xenos genérica (sin código propio) | 60 fps en Snapdragon 845; 15 issues abiertos, 0 cerrados; Mali sin tratar |
| UnleashedRecomp-Android | `SansNope/UnleashedRecomp-Android` v0.6.1 | Xenos + transcodificación BC→ETC2 | El más completo en Mali/texturas |
| Skate 3 Android | `andrewnakas/skate3-android` v0.2.0 | **Renderer nativo** | 60,1 fps, p95 16,66 ms en S23 FE |
| Gears of War 2 | `santiquiroz/gears-of-war-2-pc-port` v0.4.0 | **Renderer nativo** (desde v0.3.1) | El mejor documentado en Android (`docs/ANDROID.md`, 487 líneas) |
| Zandatsu (MGR) | `dfdx047/Zandatsu` v1.3 | **Nativo Renut** + Emulado Xenia (fallback) | El único dato Mali-G57 MC2 + Helio G99 |
| Pinyon Shift (Forza Horizon) | `arcanite24/pinyon-shift` v0.8.2 | Renderer propio (ShiftGlue) | FSR 2/3 en Android, textura size caps, shader-pack |
| MW2 Android | `OpenPort-Labs/mw2-android` v0.0.2 | Xenos + afinidad CPU | Afinidad big.LITTLE, bypass VkPipelineCache en Mali |
| Fable II FableDroid | `hardsilinder` | ReXGlue | Turnip manager, instalador ISO en el teléfono |
| NFSMW Mobile | `victorgbd/NFSMW-Recompiled-Mobile` v0.5.6 | Motor nativo propio | Probado en Mali-G57 real (Galaxy A22) |
| Lost Odyssey | `freefrank/LostOdysseyRecomp` | XenonRecomp/XenosRecomp | Shaders precompilados descargables |
| GTA V | `andrewnakas/gtav-recomp` | ReXGlue v0.10.0 | Android experimental; el PC construye, el teléfono instala (~40 GB libres) |
| CrownPark (fork ReXGlue) | `CrownParkComputing/Xbox360-Native-Ports` | Fork `rexglue-vmx` (+97 commits) | Las medidas más sistemáticas de CPU/render |

**El dato estructural, VERIFICADO en `docs/ANDROID.md` de Gears 2:** *"No se conoce ningún port Android jugable sobre la ruta GPU genérica de ReXGlue."* Skate 3, Gears, Zandatsu, Origins y Pinyon usan renderer nativo o propio, no la emulación Xenos genérica. **el título ya es la excepción: nuestro juego SE JUEGA sobre la ruta Xenos emulada.** Eso no invalida el camino — significa que el renderer nativo es la apuesta de fondo, no el primer paso: primero se agotan los arreglos baratos sobre la ruta que ya funciona.

**El sesgo Adreno, VERIFICADO:** casi todos los ports dicen "Turnip Rec." (driver Mesa Turnip para Adreno). Mali es la excepción en todas partes; el único port con Mali como ciudadano de primera es Unleashed (experimental) y Zandatsu (con el fallo G99 documentado, §2.3).

---

## 2. Mali: arrancar es la mitad de la batalla

### 2.1 `vertexPipelineStoresAndAtomics` — el mismo muro en tres ports (VERIFICADO)

El crash que tumbó a el título v1.1 en el arranque es el mismo que documentan los demás, con la misma lógica de arreglo:

- **Skate 3 v0.2.0** (release leída íntegra): una Mali-G77 (Galaxy S20 FE) era rechazada antes de dibujar nada. Razonamiento literal del autor: ese requisito *"nunca fue tan duro como el check que lo imponía. La ruta de GPU emulada ya maneja que falte la característica — encamina el memexport de vértices por compute shaders, que es para lo que está esa ruta — y lo único que no puede servir es un draw que exporta desde un vertex shader. El dispositivo se acepta ahora, y un draw que realmente lo necesite falla donde ocurre en vez de al arrancar. En una GPU que tiene la característica no cambia nada."* Era el **tercer** requisito relajado (antes: geometry shaders y non-solid fill). Honestidad del autor: *"solo se ha probado que no rompe las GPUs que ya funcionaban. Si Skate 3 corre hasta el final de una sesión en Mali no está probado."*
- **Gears 2 v0.3.8** (SDK patch 0062, issue #27): *"The Vulkan renderer no longer refuses GPUs without `vertexPipelineStoresAndAtomics` (Mali-G720 in the Dimensity 8300, and many other Mali GPUs); it skips only the rare draws whose vertex shader writes to memory."* **v0.3.9** añadió no exigir `fillModeNonSolid` en Android (líneas/puntos se dibujan rellenos); corre en Poco X6 Pro (Mali-G615) y Dimensity 8300 (Mali-G720), *"still slow"*. **v0.3.10:** *Draw source → Automatic* da menos stutters en Mali-G615. (Contenido del parche 0062 en sí NO VERIFICADO — solo las notas de release.)
- **Legends-Recomp issue #7** ("Crashing on Mali GPU", 2026-10-22, Infinix Hot 60 Pro, Helio G200, **Mali-G57 2MC**): el log adjunto `rayman_legends_runtime.log` (leído entero, 78 líneas) dice literalmente lo mismo que nuestro log de v1.1: *"Vulkan device 'Mali-G57 MC2' doesn't support vertexPipelineStoresAndAtomics, which is required for Vulkan GPU emulation parity"* → `Unable to create graphics provider` → `Graphics presentation setup failed: C0000001`. El juego nunca arranca. Corrección: esa cadena **no está en el cuerpo del issue, está en el log adjunto**. Sin respuesta del mantenedor; 15 issues abiertos, 0 cerrados.
- **Nuestro fix (Fase 3) es el mismo concepto** (relajar el requisito vía los cvars existentes + fallback a compute shaders + relajar `fillModeNonSolid`). VERIFICADO: el juego arranca y se juega en el G99. Lo que nos falta y ellos documentan: el *skip* por draw de Gears (nuestro log dirá si algún draw del título usa vertex memexport) y la medición de "¿corre hasta el final de una sesión en Mali?".

### 2.2 Lo que sigue roto en Mali después de arrancar (VERIFICADO)

- **Legends #14 — CORRECCIÓN a nuestro encargo:** es un **S20 FE Snapdragon 865 (Adreno 650), NO Mali**. Enemigos 3D invisibles (dragón con hitbox presente) + corrupción progresiva de texturas tras ~3 niveles; el reportero sugiere VRAM llena (hipótesis suya, no diagnóstico). INFERIDO: patrón de gestión de recursos del renderer emulado (descriptores/heaps/texturas que se agotan), no fallo del juego.
- **Legends #15:** S24 Exynos 2400 / Xclipse 940, v0.2.0: pantalla negra + sin audio, controles táctiles visibles. Sin log adjunto. INFERIDO: contrasta con Origins, que corre a 60 fps en Galaxy A56 (Exynos 1580, Xclipse 540) sin nada específico: el punto débil es la emulación Xenos + driver, no el motor.
- **Pinyon Shift v0.7.0:** en dispositivos sin GPU Qualcomm, el setup muestra un aviso que apunta al opt-in que deja arrancar a Mali/Tensor/Exynos/Dimensity. **v0.8.0:** *"Mali GPUs start at SCENE SHADING > COARSE on a new config: 1.4-1.6 ms of a 21.5 ms race frame on a Galaxy Tab S10 Ultra."* Y en su lista de pendientes: *"faster Android rendering on tiled GPUs"* — ni ellos lo tienen resuelto.

### 2.3 Mali-G57 MC2 + Helio G99/G100: el fallo documentado sin causa (VERIFICADO)

**Zandatsu `docs/KNOWN_ISSUES.md` v1.3, ítem 12, literal** (issues #16, #20, #25): *"Native loses the device about 4 s after opening. Automatic mode moves to Emulated; you can still choose Native by hand. Cause not found."* Es el dato más cercano a nuestro teléfono de toda la investigación.

**Pero G57 sola no predice el fallo (VERIFICADO):** el RG Cube (Mali-G57, Unisoc T820) corre Zandatsu en Renut a ~40 fps en Low/540p FSR; el POCO X6 Pro (Mali-G615) a 59 fps en 720p; NFSMW se probó en un Galaxy A22 (Mali-G57); Lost Odyssey recomienda PanVK desde G57. **El binomio roto es G57 MC2 + Helio G99/G100 + driver del sistema, no la GPU aislada.**

**Consecuencia para el título (INFERIDO):** nuestro camino emulado es el correcto en este teléfono — es exactamente el fallback automático que Zandatsu usa en este mismo hardware. El renderer nativo en el G99 es una incógnita (nadie lo ha hecho estable y documentado en Helio G99/G100), no una promesa. El plan B (emulado) no es un fracaso: es lo que hace el port de referencia en ese mismo SoC.

---

## 3. Rendimiento de CPU: el filón (nuestro perfil es 96 % CPU, 14 % GPU)

Todos los ports miden lo mismo: en el teléfono el límite es la CPU y los bloqueos, no el fill rate. Skate 3 lo dice literal: *"en un teléfono este port se queda sin CPU mucho antes que sin fill rate"*; Gears: el trabajo GPU por draw es lo que limita al teléfono, pero el trabajo CPU es lo que se puede recortar.

### 3.1 `WaitMultiple` con polling — la firma de nuestro perfil (VERIFICADO)

**Origins `docs/ANDROID_PERFORMANCE.md` §3 (leído entero y verificado por el coordinador contra la fuente):** el audio worker usaba el 73 % de un núcleo. Su perfil: `PosixConditionBase::WaitMultiple`, `pthread_mutex_trylock`, atomic compare-and-swaps y `clock_gettime`. `WaitMultiple` intentaba bloquear cada handle, los comprobaba, dormía 1 ms y volvía a empezar; cuando un `trylock` fallaba hacía yield y reintentaba **sin dormir ni comprobar el timeout**. **Arreglo (parche 0006):** un handle señalizado incrementa un contador de generación global y despierta a los multi-waiters por una sola variable de condición; wait-any comprueba cada handle bajo su propio lock; wait-all bloquea en orden de dirección. **Resultado: audio worker 73 % → 7 % de un núcleo.**

**Nuestro perfil de v1.3 tiene la misma firma exacta:** `pthread_mutex_unlock` 8,0 %, `__kernel_clock_gettime` 7,4 %, `pthread_mutex_trylock` 6,5 %, `nanosleep` 5,4 %, `sched_yield` 1,5 %. INFERIDO: nuestro ReXGlue comparte ese `WaitMultiple` (misma base); el parche 0006 de Origins es candidato directo a portar.

**Corroborado por otros dos (VERIFICADO):**
- **Gears 2, parches SDK 0078–0080 (v0.4.3):** paran los hilos que se despertaban cada 1 ms; el worker de audio pasó de ~¾ de núcleo a < 1 %.
- **CrownPark, `WaitMultiple` bloqueante:** de 2,49 → 1,95 núcleos (antes polling try-lock/sleep-1 ms: un cuarto de todos los ciclos en Split/Second). Cola de timers bloqueante: 7,7 % de ciclos.

### 3.2 `/proc/self/maps` bajo lock — tres ports, mismo arreglo (VERIFICADO)

- **Origins, parche 0005:** cada commit de memoria guest parseaba `/proc/self/maps` (3.449 líneas, 351 KB) con el mmap lock del kernel retenido (los page faults de los demás hilos se atascan). Arreglo: el commit con dirección base prueba `mprotect` primero (`mprotect` ya falla con `ENOMEM` si no todo está mapeado).
- **Gears, parche 0086 (v0.4.4):** saca `/proc/self/maps` del camino del write-watch: **15,6 % de los ciclos del hilo de render** en 0.4.3.
- **CrownPark:** el handler de fallos MMIO ya no parsea `/proc/self/maps` bajo el lock global en cada fallo (~2,7 % → 0,16 % de ciclos).

### 3.3 Registros como locales — el ahorro de instrucciones (VERIFICADO)

- **Gears, `register-as-local` (desde v0.4.3):** las seis opciones `*_as_local` del manifest: en el teléfono **recortó las instrucciones del hilo principal del guest por frame en un tercio y sus ciclos en un quinto**; el trabajo GPU es el mismo, así que el fps sigue al reloj GPU.
- **CrownPark, `non_volatile_as_local = true` por defecto:** quita `__savegprlr_N`/`__restgprlr_N` (9 % de muestras en SoulCalibur II).
- **Pinyon Shift v0.6.0** (notas de release): *"Leaner recompiled code: CTR and XER in locals cut the generated host instructions by 22.6 %, host fibers for the game's job fibers another 7.4 %, and LR in locals with specialised physics code cut the Odin's job worker by 22 % of instructions a frame."*
- **Nuestro código generado HOY hace lo contrario (VERIFICADO en `recomp/ppc/ppc_recomp.0.cpp:148`):** `ctx.ctr.u64 = ctx.r28.u64;` — CTR vive en la estructura `PPCContext` en memoria, no en un local del host. Cada `mtctr`/`bctr` toca memoria. INFERIDO: portar el patrón "registros como locales" es el mayor ahorro de instrucciones disponible para el título.

### 3.4 `clear_memory_page_state=false` — el mayor ahorro aislado de Gears (VERIFICADO)

Al final de cada frame se marcaba como obsoleta cada página subida por CPU, así que todo el working set (~6.000 páginas, 21 MB) se volvía a copiar a la GPU en cada frame en ~1.850 uploads, cada uno terminando el render pass (~1.600 render passes por frame para 3.000 draws). El parche 0007 lo apaga en Android (los memory watches siguen capturando escrituras CPU): **a 222 MHz fijos, de 4,6 a 9,4 fps** (47,8 → 23,4 megaciclos GPU por frame), 155 render passes y ~46 uploads por frame. CrownPark lo confirma (`clear_memory_page_state = false` en perfiles; con on, re-sube ~190 MB/2 s por ~1 MB de escrituras guest) y añade `gpu_hot_page_frames` (render passes 701 → 106/frame en MCLA) y `gpu_invalidation_widening_pages` (1,7 MB/s de escrituras guest se convertían en ~110 MB/s de re-subidas).

### 3.5 Afinidad de hilos a núcleos rápidos (VERIFICADO)

- **MW2 Android v0.0.2** (CHANGELOG leído vía búsqueda GitHub): *"ARM64 CPU Affinity: Pinned main game loop to the Prime Core (Cortex-X), command recorder to Performance Cores, and background worker to Efficiency Cores via Bionic `sched_setaffinity` (`runtime/platform.cpp`)."* (El archivo `runtime/platform.cpp` en sí no se abrió; la afirmación es del CHANGELOG.)
- **Skate 3 v0.2.0:** los hilos críticos de frame van a los núcleos rápidos y streaming/decodificación a los lentos; el layout de clusters **se lee del dispositivo al arrancar** (un S23 FE tiene 4×1,79 + 3×2,50 + 1×2,99 GHz; asumir dos clusters ponía todo en el prime y dejaba los 2,5 GHz ociosos). Medido tras el fix: **60,1 fps bloqueados, p95 16,66 ms, ningún frame sobre 20 ms**.
- **Zandatsu:** hasta 8 hilos del juego piden prioridad a Android (ADPF), re-elegidos cada 30 s (v1.2.2). Y su opción "Optimización de CPU" (tareas del juego en un solo hilo, sin despertar los otros núcleos): 59 fps estables en POCO X6 Pro a 720p.
- **Para el G99 (INFERIDO):** 2× Cortex-A76 + 6× Cortex-A55. El Main XThread al 96 % de un núcleo no puede caer en un A55; la afinidad explícita es barata y potencialmente decisiva.

### 3.6 Otros ahorros medidos (VERIFICADO)

- **CrownPark `db16cyc` → `yield()`:** el poll de streaming/frame-sync retenía 16,5 % de toda la CPU; ceder recortó el CPU total en juego **26 %**, mismo fps.
- **CrownPark, renderer:** caché MRU de 64 entradas de estado de pipeline (95–97 % hit; `GetCurrentStateDescription` 0,3 % → 0,02 % CPU); memoización de fetch-writes de texturas (13–30 % son reescrituras idénticas); caché de descriptores de material por frame (2.046 reúsos/frame, escrituras de descriptores 1.253 → 404); fast path de bindings estables (`UpdateBindings` 3,2 % → 1,9 %); escrituras bulk de registros (`WriteRegister` 5,9 % → 0,5 %); guardia de rebuild de swapchain (19 rebuilds/sesión → 2).
- **Gears 0082:** conserva el descriptor set de texturas de un draw cuando nada cambió (mantiene el 53 % en el capítulo 2). **0083:** quita el salto PLT de llamadas dentro de `librexruntime.so`/`librexgpu-xenos.so`. **0087:** el lookup de cvar por draw. **0089 (`vulkan_tight_render_area`, on):** cada render pass empieza solo con el área que sus draws/clears pueden escribir, no todo el target tamaño EDRAM. **0054:** copia memoria guest a la GPU en bloques enteros de 256 KB (render passes 55 → 26 por frame). **Mayor desperdicio restante en Gears:** el hilo de render del juego gira ~un núcleo entero esperando al hilo de comandos GPU (y `gow2_gpu_wait_sleep_us = 200` lo bajó de 99 % a 43 % pero costó ~7 % de frames/GHz, así que se distribuye apagado).
- **Origins §8 (per-draw):** `CopyVertices` hacía byte-swap de **todo** el vertex buffer en cada draw (14 % del game thread) → solo el rango que usa la primitiva; `FillConstants` hacía byte-swap de 2× 4 KB por draw (5 %) → solo los bytes que lee cada shader; claves de caché `std::string` con `to_string` en cada draw → claves struct planas.
- **Origins §9:** 2 frames in flight (espera de GPU 3–8 ms → 0,0 ms).
- **Skate 3, CPU antes que píxeles:** recortes que paran poblaciones **en el spawn, no en el draw** (una entidad que nunca se crea no cuesta colisión, audio ni slot de update); NPC/World Update Rate cada 2º/3º/4º frame; draw distance hasta 1/8; resolución de escena 3D fraccionaria con HUD nítido; frame cap 40/45/50 que sí se aplica (un match por subcadena descartaba el cap al tocar su ajuste compañero); simulación ligada al frame con clamp (un frame de 33 ms corría el juego a mitad de velocidad; sin clamp, un stall de 3 s avanzaría 3 s de física en un paso).
- **Zandatsu v1.3, reloj GPU:** peleas atascadas en 20 fps los primeros 40–90 s porque Android no recibía el tiempo de GPU del Nativo y no subía el reloj; el fix llevó el POCO X6 Pro a 50–55 fps desde el inicio.

### 3.7 Metodología de medición (VERIFICADO, Origins `ANDROID_PERFORMANCE.md`)

Contadores de frame cada 120 frames (draw hooks / end / gpu wait + frames > 20 ms con lo que crearon), `simpleperf` por hilo y función, perfil off-CPU para encontrar esperas (el hallazgo #1 solo aparece así), `adb shell top -H`. Y captura de frame reproducible en PC (`vkrender` + `dumpview`) para depurar texturas sin el teléfono. Es la metodología que el título debería copiar tal cual: nuestro perfil actual (muestreo por CPU) no distingue "trabajando" de "esperando".

---

## 4. Texturas BC en Mali: el problema y las tres respuestas que existen

### 4.1 Nuestro estado exacto (VERIFICADO en el SDK local)

`tools/rexauto/android/sdk/rexglue-sdk/src/graphics/vulkan/texture_cache.cpp`:
- El fallback **descomprime los bloques BC a RGBA8 con un load shader en GPU** ("Decompressing guest blocks", `host_x_blocks_per_thread`) **en cada invalidación** — no es decode por CPU como se dijo en notas anteriores; el coste es 4× VRAM + 4× ancho de banda de subida + un pase de decode por invalidación. El comentario diagnóstico lo dice: *"decompressing BC textures to RGBA8 multiplies texture memory and re-upload bandwidth 4x (and adds a compute decode pass on every invalidation). On mobile this alone can dominate frame time."*
- El TODO de upstream sigue abierto en la línea 2464: `// TODO(Triang3l): S3TC -> ETC2 / EAC (a huge research topic).` Y también: `// TODO(Triang3l): S3TC -> 5551 or 4444 as an option.`
- Mali-G57 soporta ASTC LDR y ETC2 (ETC2 es formato core de Vulkan 1.0; ASTC LDR está en todos los Mali desde T600) — los dos destinos de transcodificación existen en el hardware. (Verificar con un dump de features en el teléfono antes de construir sobre ello.)

### 4.2 UnleashedRecomp-Android: BC → ETC2/EAC en CPU en carga (VERIFICADO, con un matiz)

Notas de release 0.5.x (SansNope, v0.6.1): las texturas del juego **BC1–BC5 y BC7 se transcodifican a ETC2/EAC en la CPU al cargar** en dispositivos cuyo driver no tiene soporte BC, **con la misma huella de memoria GPU que los originales**. El contenido sRGB mantiene muestreo sRGB correcto vía los formatos ETC2 sRGB. **Si tampoco hay ETC2, fallback a decodificación RGBA plana.** Interruptor experimental *Force native BC textures* (issue #72) para GPUs que sí soportan BC nativo pero caen a ETC2 por error. **No activarlo en Adreno/Turnip**, que realmente no tiene BC. Soporte Mali experimental: generación Valhall o posterior con driver Vulkan 1.3 (G610/G615/G710/G715/G720); generaciones anteriores (Bifrost y anteriores) no pueden funcionar. Confirmado en Dimensity 8300 Ultra (Mali-G615).

**Matiz sin resolver (dicho en el borrador, no fusionar):** el README actual dice *"Textures are decompressed on the CPU when the driver lacks BC format support, which increases memory usage"* (descompresión CPU, más memoria), mientras las notas de release dicen transcodificación a ETC2 con la misma huella. Son textos de versiones distintas; el mecanismo preciso vigente **NO quedó resuelto con lo leído** — haría falta leer el código del cargador de texturas. Lo seguro: existe ruta CPU + toggle de BC nativo + issue #72.

**`Texture Quality` (Full / Half / Quarter):** sirve cada textura desde más abajo en su propia cadena de mips en tiempo de carga. Half/Quarter recortan memoria de texturas y ancho de banda de muestreo ~4×/16×; UI y fuentes mantienen resolución completa.

### 4.3 Las otras dos respuestas (VERIFICADO)

- **Skate 3, perfil de < 4 GB RAM (automático):** baja 2 niveles de mip de las texturas más grandes (**16× menos bytes y 16× menos CPU expandiéndolas** en GPUs que no pueden muestrear el formato comprimido), mitad de draw distance, stores por debajo de 256 MB. En el peor caso (Galaxy Tab A7 Lite, 8× Cortex-A53, 2,9 GB) llega a gameplay pero lento; *"el límite restante es el código emulado del juego"*.
- **NFSMW v0.5.4 (literal):** *"On Mali and PowerVR (OpenGL ES and Vulkan), the most common textures take half the memory they did. No visible difference."* El mecanismo interno **NO se detalla en la release** — se registra el efecto, no la técnica.
- **Origins: decode a RGBA8 y aun así 60 fps.** Su `tools/native_renderer/xenos_texture.h` (leído entero, 406 líneas) decodifica **DXT1/3/5 → RGBA8 en CPU**, con tiling 2D, packed mips y endian swap; cobertura medida offline: **las 8.358 texturas de los bundles del juego usan DXT1, DXT2/3, DXT4/5 o 8888, todas soportadas**. Es decir: Origins paga el mismo 4× que el título en texturas DXT (no hay transcodificación a ETC2/ASTC en el renderer nativo) y aun así llega a 60 fps en un S23 — porque el coste lo dominaban otras cosas (swapchain, polling, por-draw). Su propio `docs/ANDROID.md` (§ "Next") apunta como trabajo pendiente que UnleashedRecomp-Android *"already solves … BC→ETC2 texture transcoding"*.
- **MW2 v0.0.2 (CHANGELOG):** en Adreno, BC1–BC5 por **decodificación SIMD en software** (el hardware Adreno muestrea mal el alfa de 1 bit); `MW2_FORCE_BC_HW=1` fuerza BC por hardware en Turnip. Es el mismo problema visto desde el otro lado: ni Adreno ni Mali muestrean BC bien en estos ports; la diferencia es que en Adreno el fallo es de calidad y en Mali de ausencia.

### 4.4 Lo que NADIE hace (ausencias verificadas en lo leído)

- **Ningún port transcodifica a ASTC.** Todos los destinos documentados son ETC2/EAC o RGBA8.
- **Ningún port guarda una caché persistente en disco de texturas ya transcodificadas** (todos parecen re-transcodificar en cada carga o no lo documentan). Es trabajo nuevo, como la lectura desde la APK (§6). El precedente más cercano es la *caché de shaders* (persistente y sembrada), no la de texturas.
- **Basis Universal / KTX2 (técnica general, VERIFICADO en documentación pública, no en ningún port):** un solo archivo UASTC/ETC1S se transcodifica en carga a BC7 (escritorio), ASTC 4×4 (móvil moderno) o ETC2 (móvil viejo) — `ktxTexture2_TranscodeBasis`, destino por probe de capacidades. **Pero no aplica tal cual a el título:** nuestras texturas ya vienen comprimidas en BC dentro de los `.ipk` del juego, no en UASTC. Transcodificar BC→ASTC/ETC2 exige decodificar el bloque BC a RGBA y re-codificar (doble compresión con pérdida, coste en carga) o un transcodificador directo BC→ETC2. La vía con precedente es la de Unleashed (BC→ETC2 en CPU en carga); la vía con más retorno a medio plazo es transcodificar una sola vez y guardar el resultado en una caché persistente en disco (§9, técnica 12).

---

## 5. Carga y shaders: la primera carga larga se acepta, la segunda no se repite

### 5.1 Nuestro estado exacto (VERIFICADO en el SDK local)

`src/graphics/vulkan/pipeline_cache.cpp`:
- **La caché de shaders SÍ persiste:** abre `{title_id}.xsh` en modo `"a+b"` en el `shader_storage_shareable_root`, carga los shaders escritos por runs anteriores (validados con hash XXH3, trunca ante corrupción) y un segundo fichero de descripciones de pipelines (`PipelineStoredDescription`, cargado al arrancar).
- **Pero NO hay `vkGetPipelineCacheData` en todo el fichero:** la caché de pipelines del driver (el binario compilado por el driver Mali) **no se persiste**. Cada arranque recompila en el driver todos los pipelines, aunque la traducción Xenos→SPIR-V ya esté cacheada. Esa es la mayor parte probable de los "varios minutos" de "Cargando...".
- **Caveat Mali (VERIFICADO, MW2 v0.0.1):** *"Bypassed persistent `VkPipelineCache` on Arm Mali GPUs to eliminate documented driver cache deadlocks during monolithic pipeline compilation."* Persistir la caché del driver en Mali puede dar deadlock; si se hace, con interruptor y con prueba en el teléfono. MW2 también menciona *"Restored `depthControl` and `modeCntl` in legacy Vulkan 1.1 shader cache prewarming keys"* (tienen pre-warm de caché de shaders con claves).

### 5.2 Lo que hacen los demás (VERIFICADO)

- **Gears 2 — el bug Bionic `"a+"` (parche 0007):** *"Bionic starts reading an `"a+"` stream at its end, so the Vulkan shader storage found no header and truncated itself at every launch. Patch 0007 rewinds those streams; on the phone the storage now loads at boot (`Created 295 graphics pipelines from Vulkan storage` after the first gameplay)."* El primer arranque (y el primero tras un update que cambia gráficos) **compila los shaders otra vez: la ventana puede quedarse en la pantalla de título unos minutos**; los arranques posteriores usan los shaders guardados. **Candidato concreto a comprobar en el título:** si nuestra caché se trunca en cada arranque por el mismo bug, la carga de minutos se repite siempre y no es "primera carga" sino "cada carga".
- **Origins — caché en disco + pre-warm (§10):** `VkPipelineCache` cargado de `files/pipeline_cache.bin` + lista `files/pipelines.txt`; todos los pipelines usados se crean al arrancar; ambos se guardan (como mucho cada 5 s) cuando aparecen nuevos. Desde la segunda sesión, un pipeline conocido no cuesta nada en mitad del juego (una caché de otro driver la rechaza el driver y se empieza vacía).
- **wivi514 / DR2 Case Zero — creación asíncrona de pipelines (`docs/async-pipeline-plan.md`, VERIFICADO en el clon local):** mecanismo medido — en una máquina con la caché del driver fría, `vkCreateGraphicsPipelines` cuesta **1–200 ms por pipeline**, se llama perezosamente **en el hilo de frame** y se disparó **534 veces en tres minutos de juego** (un frame: 396 ms de pared, 372 ms dentro de `GetPipeline`). El pre-warm enviado solo es medio arreglo para un jugador nuevo: `prewarm.keys` lleva las 1.364 claves, pero solo **757** se pueden crear al arrancar, porque las otras ~600 nombran vertex shaders que aún no existen en una instalación fresca (el disco no trae microcódigo de vértice usable) — cada vertex shader llega por traducción a primera vista *durante el juego* y cada pipeline que lo necesita compila síncrono, en mitad del frame, al primer encuentro. **Arreglo:** creación asíncrona de pipelines en un hilo worker (reflejando `shaderjit`), con el draw saltado mientras su pipeline no está listo (el mismo contrato visual que ya tiene la traducción de shaders a primera vista); encadenar el pre-warm a la traducción a primera vista (cuando aterriza un shader, se encolan las claves que lo nombran). La huella que lo confirma en un reporte: el stutter es peor en la primera sesión, concentrado en la primera llegada a cada zona, y casi desaparece en la segunda (caché del driver caliente). Su primera ejecución: *"prepares the game's 1,367 shaders (~10 seconds)"* con barra de progreso; las siguientes van directas al juego.
- **CrownPark — caché templada sembrada (`shader_storage_seed_root`):** archivos de caché compartibles copiados a una caché de usuario fresca antes de cargar; Split/Second: 377 creaciones a mitad de partida y un frame de 1.434 ms → 0; nunca sobrescribe una caché existente. Y `async_shader_compilation = false` en su perfil recomendado (*"se lee como parpadeo; una caché templada es la mejor respuesta"*).
- **Zandatsu v1.2.2:** en Emulado, los efectos nuevos compilan en paralelo sin parar el juego (en RG Cube, con la caché borrada, tirones de 37 s → 26 s en el mismo camino y tiempo parado compilando de 8,6 s → 0,3 s). En Nativo con Mali-G615, el app anota el shader que tumba el driver, pasa a crear pipelines de uno en uno y si cae 2 veces en el mismo lo salta.
- **Lost Odyssey:** en el primer arranque el juego ofrece shaders precompilados para el renderer; si se salta, los compila una vez. Caché: `cache/shaders/`. Velocidad de carga: Original (ritmo Xbox 360) o Fast (por defecto).
- **Pinyon Shift:** `pinyon.py android shader-pack` construye un pack de shaders en el dispositivo.
- **Unleashed (rama winnerspiros):** precompilación de pipelines paralelizada con TBB (`std::execution`); parseo STFS/SVOD con mmap y zero-allocation.
- **GTA V:** los teléfonos no pueden correr un compilador ni un traductor de shaders: el PC/Mac construye la librería del juego y los shaders, y la app del teléfono instala después los archivos del juego desde los discos.

---

## 6. Empaquetado: el < 2 GB sin duplicar no tiene precedente — pero el SDK ya trae la pieza

### 6.1 Lo que hacen todos (VERIFICADO)

**Ningún port lee el juego desde dentro de la APK.** El patrón universal es "APK pequeña (solo código) + juego del usuario en el almacenamiento del app":

| Port | Cómo llega el juego | ¿Lee sin extraer? |
|---|---|---|
| Unleashed (SansNope) | Launcher copia ZIP/carpeta/ISO+TU+DLC con progreso | No (copia) |
| Skate 3 (3 ramas) | ISO → extracción en el teléfono (~6 GB quedan; pico ~15 GB con la ISO dentro) | No (extrae) |
| Gears 2 | ISO o carpeta → **copia** a almacenamiento propio (*"the game cannot read files through the folder picker… that is two copies; delete yours once the app has finished"*) | No (copia) |
| Zandatsu | ISO elegida; **se lee in situ sin extraer** (no borrar ni mover la ISO después; ~8 GB libres) | **Sí, pero la ISO es externa** |
| LibertyDroid | ISO → importación (~6,6 GB + ISO) | No |
| Lost Odyssey | Importador (carpeta/XEX/ISO/GOD) a `game/` | No |
| GTA V | Pack construido en PC + ISOs copiadas | No |
| Rexauto | La app desempaqueta ISO/STFS/GoD en privado | Desempaqueta |
| Origins | Import por `.zip` (Zip64) o pack compacto | No (el juego nunca entra en la APK: *"limited to Zip32"*) |

**Conclusión verificada: leer assets desde dentro de la APK (assets/`AAssetManager`/OBB) no tiene precedente en ningún port encontrado.** Para el título es trabajo nuevo, no receta copiable. La única lectura sin duplicar es la ISO *in situ* de Zandatsu, que sigue exigiendo la ISO aparte (~8 GB) y prohíbe moverla.

### 6.2 Origins: el pack compacto deduplicado (VERIFICADO)

`docs/ANDROID.md` §4 + parche 0007 (`PackedDevice`): los bundles repiten muchos datos entre niveles. `make_compact_pack.py` guarda cada tramo distinto de bytes una sola vez y describe cada archivo como lista de piezas del almacén: **5,4 GB → 1,1 GB** (`.rdpk`, sin pérdidas, verificado byte a byte al reconstruir). El runtime lee el pack **memory-mapped, como si los archivos originales estuvieran ahí**. Es una app separada (Rayman Origins Compact, `…raymanrecomp.compact`); los shaders van dentro de la APK y el launcher los extrae en el primer arranque.

**Lectura para el título (INFERIDO):** los `.ipk` del título probablemente también repiten datos entre niveles (misma estructura el motor). Probar la idea sobre los `.ipk` y medir la ratio es la vía con más retorno si se acepta importar una vez: total ~1,1 GB, sin duplicar. Pero sigue exigiendo importación — no cumple "sin importar nada".

### 6.3 Nuestra vía: montar el STFS desde dentro de la APK (VERIFICADO en el SDK local, INFERIDO el montaje)

Esto es lo que ningún port ha hecho y lo que resuelve el requisito tal como lo pidió el usuario (port 100 % oficial, sin importar, total = tamaño de instalación < 2 GB). Las piezas ya existen:

1. **El SDK ya tiene un dispositivo STFS** (`src/filesystem/devices/stfs_container_device.cpp`, `stfs_container_entry.cpp`, `stfs_container_file.cpp`): monta un paquete STFS como dispositivo VFS y sirve sus archivos por lectura aleatoria (`stfs_container_file.cpp:54`: `Seek(file, record.offset + read_offset, SEEK_SET)`). `ContentManager::InstallContent` (`src/system/xam/content_manager.cpp:578`) ya lo usa: monta el STFS y luego **copia** los archivos fuera — la copia es el paso que sobra, no el montaje.
2. **La raíz del juego hoy es un `HostPathDevice`** (`src/system/runtime.cpp`, `Runtime::SetupVfs`): monta `game_data_root_` en `\\Device\\Harddisk0\\Partition1` y registra los enlaces `game:` y `d:`. Cambiar ese montaje por un `StfsContainerDevice` en el mismo punto es un cambio acotado en un solo sitio.
3. **El asset dentro de la APK es legible sin extraer (VERIFICADO, Android Developers):** `AssetManager.openFd(String)` — *"Open an uncompressed asset by mmapping it and returning an `AssetFileDescriptor`. The asset must be uncompressed, or an exception will be thrown."* Nuestro `game.stfs` ya se inyecta como entrada **STORED** (sin comprimir) en la APK, así que `openFd` funciona; en NDK, `AAssetManager_open` con `AASSET_MODE_RANDOM` da lecturas con seek, y `AAsset_openFileDescriptor` da fd + offset + longitud para `pread`/`mmap`.
4. **El trabajo que falta (INFERIDO, acotado):** el `StfsContainerDevice` hoy toma un `std::filesystem::path` y lo abre con `OpenFile(path, "rb")`. Hace falta un envoltorio de fichero con offset (abrir `base.apk`, leer en `[asset_offset, asset_offset + size)`), o un constructor que acepte fd + offset + tamaño. Y verificar tres cosas antes de construir sobre ello: (a) que el juego no escriba nada en `d:\` (los saves van por el content manager de XAM, no por `d:\` — verificar en el código del juego/runtime); (b) el coste CPU de las lecturas STFS (cada lectura atraviesa tabla de hashes/bloques — medir contra la lectura desde disco extraído); (c) que el `.ipk` (que el juego lee por VFS con acceso aleatorio) funcione igual sobre el STFS montado que sobre disco.
5. **Resultado esperado:** total instalado = APK sola (~1,6 GB: motor ~24 MB + STFS 1,5 GB + arte). Sin importación, sin copia, sin duplicar. El STFS dentro de la APK *es* el juego; el runtime lo lee por demanda.
6. **OBB (sin precedente en los ports):** Android soporta expansion files (OBB) leídos por `StorageManager` con acceso aleatorio, pero nadie en los ports leídos lo usa y añade otro archivo a instalar; la vía del asset dentro de la APK es más directa para un port "oficial".

### 6.4 El detalle de Skate 3 que sí se puede copiar (VERIFICADO)

El APK de Skate 3 pasó de 91 MB a **38 MB porque el motor va comprimido dentro del APK y se desempaqueta al instalar**; la instalación tarda más y el tamaño instalado crece ~el tamaño del motor. Ese intercambio es lo que permite cargar un driver personalizado. (Lo que va comprimido es el **motor**, no el juego.) Para el título: el motor ya es pequeño (~24 MB); no es donde está el tamaño.

---

## 7. Aspecto y presentador: opciones explícitas, no magia

### 7.1 Nuestro estado (VERIFICADO, `analysis/FASE4-pantalla.md`)

SDL sobrescribe con `FULL_USER` al crear la ventana; la primera superficie nace en vertical (1080×2400) y el presentador crea el swapchain en vertical y dibuja el fotograma 16:9 con letterbox (banda 1080×607). El presentador NO está roto: su matemática de letterbox es correcta para la superficie que le dan. El fix v1.3 (hint `LandscapeLeft` + override de `setOrientationBis`) **no curó el título** en la prueba del teléfono. Queda por verificar en el log nuevo el tamaño de superficie real en el momento del título.

### 7.2 Lo que hacen los demás (VERIFICADO)

- **Origins §1 — el sospechoso nº 1 del título del título:** el swapchain usaba pre-transform identidad y dejaba que el compositor rotara la imagen a paisaje; la pantalla reporta rotación 90°, así que `vkQueuePresentKHR` devolvía `VK_SUBOPTIMAL_KHR` **en cada present**. El renderer trataba SUBOPTIMAL como "stale" y cada frame pagaba `vkDeviceWaitIdle` + destruir el swapchain + 3 buffers nuevos de 2340×1080 (y el chorro de mensajes `qdgralloc` en logcat). **Arreglo:** solo `VK_ERROR_OUT_OF_DATE_KHR` y `VK_ERROR_SURFACE_LOST_KHR` re-crean el swapchain; un swapchain subóptimo sigue presentando bien. **Resultado: 43–46 → 60 fps.** INFERIDO para el título: si nuestro presenter re-crea el swapchain ante SUBOPTIMAL, es sospechoso nº 1 tanto del coste por frame como de los problemas de superficie/título. Comprobación barata: grep del manejo de SUBOPTIMAL + contar re-creaciones en logcat.
- **Origins — widescreen el motor reutilizable:** `RAYMAN_WIDESCREEN=<aspect>` parchea las **dos constantes de ajuste 16:9 del motor (16/9 en `0x8201EF58`, 9/16 en `0x8201EF5C`, usadas por `sub_824C8798`)** y el renderer encaja ese aspecto en la ventana; el modo de vídeo guest debe coincidir (720 líneas × aspecto real). En Android está activo por defecto: la app fija un modo de vídeo de 720 líneas con el aspecto de la pantalla (19.5:9 en el S23). **INFERIDO para el título:** buscar en el binario del título el mismo par de flotantes 16/9 y 9/16 usados por la función de ajuste de aspecto es una vía barata para el título que no llena la pantalla; las direcciones serán distintas (otro ejecutable), el patrón es el mismo. Structs de vértice el motor documentados (16 PC / 20 PT / 24 PCT / 28 font / 36 PNCT / 64 patch) y el resolve por dirección física `(addr & 0x1FFFFFFF) + 0x1000` son reutilizables si algún día se hace renderer nativo.
- **Gears 2 v0.3.9 — llenar una pantalla ancha (issue #33):** Display → *Keep 16:9 shape* → Off llena la pantalla: la imagen **se recorta hasta 10 % arriba y abajo (la safe area de TV, así el HUD queda dentro) y se estira lateralmente el resto** (`present_letterbox`). SurfaceFlinger: la SurfaceView se veía como DEVICE con `ROT_90`, sin capas CLIENT; el display rotaba el swapchain `IDENTITY`-preTransform sin pasada de composición GPU, así que la pre-rotación no era necesaria en ese teléfono. **Background (parche 0085, v0.4.3):** el juego sobrevive a salir y volver; antes crasheaba en `vkCreateAndroidSurfaceKHR` con una ventana ya liberada. Ultrawide real (Hor+, hasta 1.680 px de ancho).
- **Zandatsu:** proporción 16:9 original (defecto), **Estirar**, o **Ultrawide** aparte que sigue la proporción de la pantalla; queda una **costura a 4/5 del ancho en algunos menús**; el HUD sigue en layout 16:9 pegado a la izquierda; logos y vídeos quedan 16:9 con barras.
- **Lost Odyssey:** cinemáticas y overlay de pausa usan un área 16:9 centrada; pantallas más altas se rellenan.
- **NFSMW (Galaxy A22, Mali-G57):** en Vulkan se arreglaron retrovisor negro, franja negra en el horizonte y vídeo de menú duplicado — *"no eran culpa del driver de ARM, sino fallos de sincronización nuestros que Adreno perdona"*, más un bug de presentación señalado por la capa de validación. Lección: ante artefactos en Mali, pasar la capa de validación de Vulkan antes de culpar al driver.
- **Skate 3:** al volver del task switcher podía quedar pintando pero sin jugar (el evento de re-attach no se entregaba de forma fiable; ahora ambos eventos re-attachan); MSAA ya no encoge la escena a la esquina superior izquierda cuando el target es menor que la salida (el resolve cae en el plano de escena).

---

## 8. El renderer nativo como cambio estructural (para decidir con los ojos abiertos)

- **Origins:** sin emulación de GPU Xbox 360 — `rex/src/native_capture.cpp` intercepta en el nivel D3D `CreateVertexShader`/`CreatePixelShader` (cada shader se hashea con XXH3; ese hash es el nombre del SPIR-V) y los tres puntos de entrada de dibujo; por cada draw lee el shadow de registros Xenos, copia vértices e índices (big-endian → little-endian), decodifica las texturas, elige un pipeline y dibuja. Comparativa medida: M1 title screen, emulación 30–55 fps con stalls de 100–620 ms → nativo ~59 fps; Galaxy S23, emulación "slow, stalls" → nativo 60 fps, peor frame ~18 ms. Piezas reutilizables: hooks por hash XXH3, structs del motor, resolve por dirección física, `rexgpu-null` (parches 0003–0004), corrección `setp_inv` en SPIR-V (el escalar Xenos `setp_inv` es `src == 1 ? 0 : (src == 0 ? 1 : src)`; XenosRecomp emite `src == 0 ? 1 : src` y pierde el primer caso — dejaba invisible el primer segmento del puente del cielo), direccionado relativo de constantes (índice hasta c224 donde el bloque terminaba en c208), shaders AOT a SPIR-V sin enteros de 64 bits ni buffer device addresses.
- **Skate 3 y Gears:** renderer nativo por defecto en Android; Gears pasó de 11 a 30 fps a 720p en Snapdragon 865 tras reemplazar la ruta GPU genérica por un renderer propio (y en S25 Ultra: 52,9 fps en frío → ~37 en caliente, GPU 95–99 % el límite).
- **Zandatsu Renut:** renderer nativo de `mgr-nx-deko`, más rápido y nítido; con fallback automático a Emulado cuando pierde el dispositivo.
- **Coste honesto:** es trabajo grande (meses, no días) y en el G99 es una incógnita (Zandatsu pierde el dispositivo en este SoC). **Recomendación de este informe:** no es el primer paso. El juego ya se juega sobre la ruta Xenos; los arreglos P0/P1 de §9 se hacen primero y se miden. El renderer nativo queda como proyecto de fondo, con las piezas de Origins documentadas y reutilizables.

---

## 9. Lista priorizada de técnicas para el título

Ordenada por retorno esperado / esfuerzo, con el problema del título que ataca. Costes en trabajo de una persona que conoce el stack (C++/Vulkan/Android NDK); efectos según lo medido por los ports citados, no promesas.

### P0 — Hacer primero (barato, efecto medido en otros)

| # | Técnica | Problema el título | Coste | Efecto esperado (medido en otros) |
|---|---|---|---|---|
| 1 | **Portar el parche 0006 de Origins (`WaitMultiple` bloqueante) + 0005 (`mprotect` en vez de `/proc/self/maps`)** | CPU 96 %, mutex 8 % + trylock 6,5 % + clock 7,4 % | Bajo (parches ReXGlue existentes, adaptar) | Audio worker 73 % → 7 % de un núcleo (Origins); ~¾ núcleo → < 1 % (Gears 0078–0080); 2,49 → 1,95 núcleos (CrownPark). Un núcleo entero liberado |
| 2 | **No re-crear el swapchain ante `VK_SUBOPTIMAL_KHR`** (solo `OUT_OF_DATE`/`SURFACE_LOST`) + contar re-creaciones en log | Título sin llenar + coste por frame | Bajo (grep + cambio de condición + log) | 43–46 → 60 fps (Origins). Sospechoso nº 1 del título |
| 3 | **Verificar el bug Bionic `"a+"` en la caché de shaders** (¿se trunca en cada arranque? ¿carga `"Created N graphics pipelines from Vulkan storage"`?) | Carga de varios minutos | Bajo (leer el log + rewind si aplica, parche 0007 de Gears) | La segunda carga deja de costar minutos; si la caché se truncaba, es el arreglo entero de la carga |
| 4 | **Afinidad de hilos** (`sched_setaffinity`: main thread al Cortex-A76 grande; workers a A55) | Main XThread 96 % de un núcleo | Bajo | 60,1 fps, p95 16,66 ms (Skate 3); evita que el hilo principal caiga en un núcleo pequeño |
| 5 | **Buscar el par de constantes 16/9 y 9/16 en el binario del título** (patrón Origins: `0x8201EF58`/`0x8201EF5C`) + modo vídeo guest a juego | Título sin llenar | Bajo-medio (búsqueda de flotantes + parcheo en arranque) | Título a pantalla completa, como Origins en 19.5:9 |

### P1 — Medio esfuerzo, efecto grande

| # | Técnica | Problema el título | Coste | Efecto esperado |
|---|---|---|---|---|
| 6 | **`register-as-local` / `non_volatile_as_local`** (CTR/XER/LR en locales del host, no en `PPCContext`) | CPU 96 % | Medio (parche XenonRecomp + re-verificar todo el recomp) | −⅓ instrucciones y −⅕ ciclos del hilo guest (Gears); −22,6 % instrucciones generadas (Pinyon); 9 % de muestras (CrownPark) |
| 7 | **`clear_memory_page_state=false`** en Android + medir re-subidas por frame | CPU/GPU | Bajo-medio (cvar + medición) | 4,6 → 9,4 fps a 222 MHz fijos (Gears); render passes 1.600 → 155 |
| 8 | **Transcodificación BC → ETC2/EAC en CPU en carga** (vía Unleashed) + fallback RGBA + toggle *Force native BC* | Texturas 4× VRAM/banda | Medio-alto (transcodificador en el texture cache + medir calidad/velocidad) | Misma huella GPU que el original (¼ de VRAM/ancho de banda frente a RGBA8). Alternativa barata: −2 mips en perfil de poca memoria (Skate 3: 16× menos bytes) |
| 9 | **Pre-warm + siembra de caché de shaders** (`pipeline_cache.bin` + `pipelines.txt` al arrancar; caché templada `shader_storage_seed_root`; encadenar pre-warm a la traducción a primera vista, estilo wivi514) | Carga + tirones en zonas nuevas | Medio | Primera carga deja de ser minutos; tirones de 50–115 ms desaparecen desde la segunda sesión (Origins); frame de 1.434 ms → 0 (CrownPark). Caveat: en Mali, el `VkPipelineCache` persistente puede dar deadlock (MW2 lo desactivó) — con interruptor |
| 10 | **Montar el STFS desde dentro de la APK** (`StfsContainerDevice` + `AssetManager.openFd` + envoltorio de fichero con offset) | Tamaño 3,26 GB → < 2 GB, sin importar | Medio (envoltorio + montaje en `SetupVfs` + verificar escrituras a `d:\` + medir lecturas STFS) | Total instalado = APK sola (~1,6 GB). Sin importación, port 100 % oficial. Alternativa: pack compacto `.rdpk` (Origins 5,4 → 1,1 GB) — total ~1,1 GB pero con importación |
| 11 | **Creación asíncrona de pipelines** (estilo wivi514: worker + draw saltado mientras no está listo) | Tirones de mitad de partida | Medio | `vkCreateGraphicsPipelines` 1–200 ms fuera del hilo de frame; 534 stalls en 3 min → 0 en el hilo de frame |

### P2 — Estructural (grande, para después de P0/P1)

| # | Técnica | Problema el título | Coste | Efecto esperado |
|---|---|---|---|---|
| 12 | **Renderer nativo** (Origins: hooks D3D por hash XXH3, structs del motor, `rexgpu-null`) | Rendimiento + Mali + texturas, todo a la vez | Muy alto (meses) | 60 fps en S23 (Origins); esquiva `vertexPipelineStoresAndAtomics` de raíz. Incógnita en G99 (Zandatsu pierde el dispositivo en este SoC) |
| 13 | **Caché persistente en disco de texturas ya transcodificadas** (BC→ETC2 una sola vez) | Texturas (carga repetida) | Medio-alto (trabajo nuevo, cero precedente) | La transcodificación se paga una sola vez en vez de en cada carga |
| 14 | **FSR/CAS o resolución interna fraccionaria con HUD nítido** (Pinyon FSR 2/3; Skate 3) | Fill rate / banda | Alto (upscaler en el presenter) | Menos píxeles renderizados. Nota: en el título el límite es CPU, no fill rate (GPU 14 %) — va después de lo de CPU |
| 15 | **2 frames in flight + `k_8` como R8 con swizzle** (Origins §9/§6) | Espera GPU / vídeo | Medio | Espera GPU 3–8 ms → 0 ms; vídeo 10 ms → 0,7 ms por frame |

### Lo que NO hacer (verificado en los ports)

- **No confiar en el plugin Xenos stock en Mali sin los relajamientos** (Legends #7: crash duro en arranque, 15 issues abiertos, 0 cerrados).
- **No persistir el `VkPipelineCache` del driver en Mali sin interruptor** (MW2: deadlocks documentados; lo desactivaron).
- **No asumir que bajar la resolución lo arregla todo** (Skate 3, Gears: en Mali el límite suele ser CPU/runtime, no fill rate; "la mayor parte del coste no depende de la resolución de escena").
- **No copiar código GPL-3.0 a este repo** (Gears lo advierte en su propio `ANDROID.md`: aprender qué se rompe, nunca copiar su código a un repo BSD-3 ni a los parches ReXGlue; nuestro repo es GPL-3.0, pero los parches ReXGlue siguen la licencia del SDK — verificar antes de portar cada parche).
- **No prometer que la primera carga desaparece:** todos aceptan la primera compilación larga como coste único; lo que se elimina es repetirla.

---

## 10. Lo que NO se pudo verificar (sin inventar)

- Contenido del parche SDK 0062 de Gears (solo notas de release); código del cargador de texturas de Unleashed (README vs release notes discrepan: ¿ETC2 o RGBA en la build vigente?); mecanismo interno de NFSMW v0.5.4 ("mitad de memoria en Mali/PowerVR"); gestión de texturas de Zandatsu/Renut (no documentada en lo leído); `docs/GRAPHICS_DRIVERS.md` (Unleashed), `docs/ANDROID_STATUS.md` (GTA V), `PORTING.md` (LibertyDroid) citados pero no abiertos.
- Diffs línea por línea de los parches 0003–0007 de Origins (solo 0001–0002 leídos enteros); `docs/PROGRESS.md` §7 y `docs/D3D_MAP.md` de Origins; issues #1–#6/#8–#13 de Legends; contenido de los assets de las releases de Legends; si Origins corre en algún Mali (ninguna evidencia ni a favor ni en contra); el alias "Yato" como autor de Legends (no aparece en ninguna fuente leída).
- Relación exacta de fork entre `SansNope/UnleashedRecomp-Android` y `winnerspiros/UnleashedRecomp_Android` (solo diffs de commits leídos).
- Ausencias en lo leído (no prueba de inexistencia universal): transcodificación a **ASTC**, **caché persistente de texturas transcodificadas**, **lectura del juego desde dentro de la APK/OBB** — en ningún port encontrado.
- Búsqueda GitHub directa (code search) no hecha; pueden existir ports pequeños no indexados en los listados usados (Recompedia, awesome-*, Quiver, droidports).

---

## 11. Fuentes

### Investigadas por el coordinador (esta ronda)
- `https://droidports.com/platforms/xbox-360/` (matriz de 17 ports, leída 2026-11-13)
- `https://github.com/XDanfr/RaymanOriginsRecomp/blob/main/docs/ANDROID_PERFORMANCE.md` (leído entero y verificado: los 10 hallazgos)
- `https://github.com/arcanite24/pinyon-shift/releases/tag/v0.6.0` y `/releases` (FSR 2/3, texture caps, CTR/XER en locales, shader-pack, opt-in Mali)
- `https://github.com/OpenPort-Labs/mw2-android` (CHANGELOG v0.0.2 vía búsqueda: afinidad CPU, BC por SIMD, bypass VkPipelineCache en Mali)
- `https://developer.android.com/reference/android/content/res/AssetManager` (`openFd`: asset sin comprimir por mmap)
- SDK local `tools/rexauto/android/sdk/rexglue-sdk/src/graphics/vulkan/texture_cache.cpp` (TODO S3TC→ETC2/EAC, línea 2464; fallback BC→RGBA8 por load shader)
- SDK local `src/graphics/vulkan/pipeline_cache.cpp` (`.xsh` + pipeline storage persistentes; sin `vkGetPipelineCacheData`)
- SDK local `src/filesystem/devices/stfs_container_device.cpp` + `src/system/xam/content_manager.cpp:578` + `src/system/runtime.cpp` (`SetupVfs`, `HostPathDevice`, `d:`)
- SDK local `recomp/ppc/ppc_recomp.0.cpp:148` (`ctx.ctr.u64` en memoria, no en local)
- Clon local `tools/wivi514-casezero/docs/async-pipeline-plan.md` (creación asíncrona de pipelines, 1–200 ms por pipeline, 534 en 3 min)
- `analysis/FASE3.md` (§2: Skate 3, Gears, Pinyon, Zandatsu, NFSMW, ReXGlue v0.10.1) y `analysis/FASE4-pantalla.md` (diagnóstico de superficie/presentador)

### Borrador Rayman (`analysis/_draft-ports-rayman.md`, 38 KB)
- `https://github.com/XDanfr/RaymanOriginsRecomp` (README; HEAD `c1bf7b9`, 80 commits) + `docs/NATIVE_RENDERER.md` + `docs/ANDROID.md` + `android/rexglue-patches/` (0001–0007) + `tools/native_renderer/xenos_texture.h`
- `https://github.com/edeegg/Legends-Recomp` (README; HEAD `416031e`, 5 commits) + releases 0.1.0/0.2.0 + issues #7 (log adjunto leído entero) / #14 / #15

### Borrador Unleashed (`analysis/_draft-ports-unleashed.md`, 64 KB)
- `https://github.com/SansNope/UnleashedRecomp-Android/releases` (v0.6.1) + Quiver; `https://github.com/winnerspiros/unleashedrecomp_android/commit/098d572…` / `da2530e…` / `c1601c7…`
- `https://github.com/andrewnakas/skate3-android/releases/tag/v0.2.0` + `https://github.com/darchap/Skate3-Port` + `https://buku313.github.io/Skate3-Mobile/`
- `https://github.com/santiquiroz/gears-of-war-2-pc-port` + `docs/ANDROID.md` (487 líneas) + releases v0.3.8/v0.3.9/v0.3.10
- `https://github.com/dfdx047/Zandatsu` + `docs/KNOWN_ISSUES.md` + `docs/CHANGELOG.md`
- `https://github.com/CrownParkComputing/Xbox360-Native-Ports/blob/HEAD/docs/REXGLUE_FORK_CHANGES.md` (860 líneas)
- `https://github.com/Jauidev/LibertyDroid` · `https://github.com/victorgbd/NFSMW-Recompiled-Mobile/releases/tag/v0.5.4` · `https://github.com/freefrank/LostOdysseyRecomp` · `https://github.com/andrewnakas/gtav-recomp` · `https://github.com/player124413/rexauto`
- Listados: `https://recompedia.com/platform/xbox360` · `https://github.com/abduznik/awesome-android-native-ports` · `https://github.com/abduznik/awesome-x360-recomps`

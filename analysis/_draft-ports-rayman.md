# Rayman Origins Recompiled y Legends-Recomp — hallazgos para el título

> Borrador de investigación. Fecha: 2026-11-13.
> Solo investigación por internet; no se construyó ni modificó nada del árbol del port.
> Convención: **VERIFICADO** = leído literalmente en la fuente citada (repo, archivo, issue o log
> adjunto). **INFERIDO** = conclusión razonada a partir de lo verificado, marcada como tal.
> Lo que no se pudo leer se dice explícitamente; no se inventa su contenido.

## 1. Los dos proyectos en una página

| | Rayman Origins Recompiled | Legends-Recomp |
|---|---|---|
| Repo | `XDanfr/RaymanOriginsRecomp` | `edeegg/Legends-Recomp` |
| HEAD verificado | `c1bf7b90e95d476e63aa4a0b077905ff6dbde9bd`, 80 commits, creado 2026-09-26 | `416031ef8fed69c0ebabceb2264f354a5ac64ae6`, 5 commits, creado 2026-10-07 |
| Autor | BelmanteGu (firma de los parches: `BelmanteGu <gustavobelmanteribeiro@gmail.com>`; el README trae `git clone … BelmanteGu/RaymanOriginsRecomp.git` aunque el repo actual es `XDanfr/…` — posible traslado/renombrado, no verificado el motivo) | Cuenta `edeegg`; el alias "Yato" **no aparece en ninguna fuente leída** (README, releases, issues) — no verificado |
| Paquete Android | `io.github.belmantegu.raymanrecomp` (+ variante Compact `…raymanrecomp.compact`) | `org.rexglue.raymanlegends` según el log de runtime del issue #7; el README dice `Android/data/com.rexglue.raymanlegends` — **discrepancia**: el paquete cambió entre 0.1.0 y 0.2.0 o el README está desactualizado |
| Base | XenonRecomp (fork propio en `tools/XenonRecomp`) + ReXGlue SDK v0.10.0 + renderer nativo propio | ReXGlue SDK (sin código propio en el repo: solo `README.md` + `icon.png`) |
| Licencia | GPL-3.0 | Sin licencia visible en la raíz (solo README + icono) |
| Estado | Jugable: macOS Apple Silicon y Android (Galaxy S23, 60 fps, widescreen 19.5:9); fases 1–8 del README marcadas ✅ | Prebuilt APK en releases 0.1.0 (2026-10-07, pre-release) y 0.2.0 (2026-10-15, Latest). 15 issues abiertos, 0 cerrados |
| Motor | el motor (mismo motor que el título) | el motor (mismo motor que el título) |

Fuentes: páginas principales de ambos repos (leídas 2026-11-13),
`XDanfr/RaymanOriginsRecomp` README + `docs/ANDROID.md`, `docs/ANDROID_PERFORMANCE.md`,
`docs/NATIVE_RENDERER.md`; `edeegg/Legends-Recomp` README + releases + issues #7, #14, #15.

---

## 2. Mali — lo más importante para el G99 / Mali-G57 MC2

### 2.1 Legends issue #7: crash en Mali-G57, causa exacta leída en el log adjunto — VERIFICADO

- Issue: **"Crashing on Mali GPU"**, `edeegg/Legends-Recomp#7`, abierto 2026-10-22 por
  `Ahmad64cdI`. Dispositivo del reporte: Infinix Hot 60 Pro, Helio G200, **Mali-G57 2MC**,
  8+8 GB RAM. Sin respuesta visible del mantenedor; issue sigue abierto.
- El cuerpo del issue **no** menciona `vertexPipelineStoresAndAtomics`. Esa cadena está en el
  **log adjunto** `rayman_legends_runtime.log` (leído completo, 78 líneas, dos intentos de
  arranque idénticos), que dice literalmente:

```
[warning] [core] * 0: Mali-G57 MC2
[warning] [core] Vulkan device 'Mali-G57 MC2' doesn't support vertexPipelineStoresAndAtomics,
             which is required for Vulkan GPU emulation parity
[warning] [core] Couldn't choose a compatible Vulkan physical device or initialize a Vulkan logical device
[error] [gpu] Unable to create graphics provider
[error] [core] Graphics presentation setup failed: C0000001
```

- El log muestra además, VERIFICADO: GPU plugin `'xenos'` cargado (`librexgpu-xenos.so`);
  instancia Vulkan API **1.4.0**; perfil `Rayman Legends profile: balanced`; `language id: 1`;
  `AdrenoTools env probe` vacío (sin driver Turnip); `Game directory:
  /storage/emulated/0/Android/data/org.rexglue.raymanlegends/files/game`;
  `Cache root: …/cache/shader-cache`. El juego **nunca llega a arrancar**: falla en la
  creación del proveedor gráfico.
- **INFERIDO:** el requisito `vertexPipelineStoresAndAtomics` lo impone el plugin Xenos de
  ReXGlue (emulación de GPU Xbox 360 por Vulkan), no el juego. Es un requisito duro
  (hard fail, no fallback) en la versión de ReXGlue que usa Legends. Esto es exactamente la
  GPU del teléfono del título (Mali-G57 MC2): **si el título usara el plugin Xenos tal cual, fallaría
  igual en el arranque**. Que el título ya arranque y se juegue en el G99 indica que su runtime
  actual no tropieza con ese requisito (o lo evita) — verificar cuál es la diferencia exacta
  es trabajo del coordinador, no de este informe.

### 2.2 Legends issue #14: enemigos 3D invisibles + corrupción de texturas — VERIFICADO, con corrección

- Issue: **"Graphical Issues – Invisible 3D Enemies and Texture Corruption"**,
  `edeegg/Legends-Recomp#14`, 2026-11-09. Dispositivo: **Samsung Galaxy S20 FE
  (Snapdragon 865, 6 GB RAM)** — es decir, **Adreno 650, NO Mali** (el encargo lo describía
  como caso Mali; no lo es).
- Síntomas: (1) enemigos/bosses 3D invisibles (el dragón del primer mundo: invisible pero con
  hitbox de colisión presente); (2) corrupción progresiva de texturas tras ~3 niveles, hasta
  hacer el juego casi injugable. El reportero sugiere VRAM llena y que reiniciar lo arregla
  temporalmente — es su hipótesis, no un diagnóstico confirmado. Sin respuesta del mantenedor.
- **INFERIDO:** el patrón (invisibilidad selectiva de draws 3D + degradación progresiva)
  apunta a gestión de recursos del renderer emulado (descriptores/heaps/texturas que se agotan
  o se reutilizan mal), no a un fallo del juego. Es el mismo tipo de síntoma que el título debe
  vigilar si usa emulación Xenos; el renderer nativo de Origins (sección 4) es la vía que lo
  evita.

### 2.3 Legends issue #15: pantalla negra en S24 Exynos — VERIFICADO

- Issue: **"RAYMAN LEGENDS"**, `edeegg/Legends-Recomp#15`, 2026-11-12. Dispositivo: Samsung
  Galaxy S24 (Exynos 2400, **Xclipse 940**), Android 16, Legends Recomp **0.2.0**. Síntoma:
  el juego arranca, aparecen los controles táctiles, pero **toda la pantalla queda negra y
  sin audio**. Archivos del juego extraídos correctamente (incl. `default.xex`); reiniciar el
  teléfono no lo resuelve. El usuario pregunta si es Vulkan/Xclipse. Sin respuesta del
  mantenedor.
- **INFERIDO:** contrasta con Origins, que corre a 60 fps en Galaxy A56 (Exynos 1580,
  Xclipse 540) "sin nada específico para él" (ver §4). La diferencia más probable es
  arquitectónica: Legends usa el plugin Xenos emulado; Origins el renderer nativo. Un
  Xclipse 940 que falla donde un Xclipse 540 funciona sugiere que el punto débil es la
  emulación Xenos + driver, no el motor en sí. No verificado: haría falta el log de #15
  (el issue no adjunta log).

### 2.4 ¿Qué hizo Legends por Mali? — VERIFICADO: nada

- El README de Legends solo dice: "**Mali GPU users:** If the game does not work on your
  device, please open an issue on GitHub…" y que el rendimiento "may vary depending on the
  device and GPU drivers". Requisito declarado: "Android phone with Vulkan support
  (Snapdragon 845 or newer)". No hay código en el repo (solo README + icono), así que no hay
  ningún workaround de Mali que leer. Los 15 issues están abiertos y ninguno cerrado a
  2026-11-13. **No existe, en lo leído, ninguna solución Mali en Legends: solo reportes.**

### 2.5 ¿Qué hizo Origins por Mali? — VERIFICADO: nada específico; lo relevante es indirecto

- Búsqueda `"RaymanOriginsRecomp" Mali` no devuelve ninguna mención de Mali en el proyecto.
  Dispositivos probados y documentados (README + `docs/ANDROID_PERFORMANCE.md`):
  Galaxy S23 (Adreno 740, Vulkan 1.3), Redmi 10C (Adreno 610, Vulkan 1.1), Galaxy A56
  (Xclipse 540). **Ningún Mali probado; ningún código específico Mali.**
- Lo que sí hace Origins, y es lo transferible: **no usa el plugin Xenos emulado en absoluto**
  en su configuración por defecto. Usa `rexgpu-null` (parches 0003–0004, §5) para mantener
  vivo el protocolo GPU del huésped (ring buffer, fences, interrupciones, vblank) sin dibujar,
  y un **renderer nativo** que intercepta las llamadas D3D y dibuja directo a Vulkan con
  shaders precompilados a SPIR-V. Ese diseño **esquiva de raíz el requisito
  `vertexPipelineStoresAndAtomics`**, porque nunca entra en la emulación Xenos. La pega, en
  honor a la verdad: no está probado en Mali; es una inferencia arquitectónica, no un dato
  medido. El camino prudente para el título sería probar el renderer nativo en el G99 antes que
  intentar remendar el plugin Xenos.

---

## 3. Rendimiento — Origins: 10 hallazgos medidos antes/después (VERIFICADO)

Fuente: `docs/ANDROID_PERFORMANCE.md` (perfilado con `simpleperf` en el propio teléfono,
`adb shell top -H`, y contadores de frame del renderer leídos con
`adb logcat -s RaymanNative`). Todos los números son del mismo dispositivo antes y después
del arreglo. Resumen:

| # | Hallazgo | Antes | Después |
|---|---|---|---|
| 1 | Swapchain re-creada **en cada frame** | 47% del game thread; 43–46 fps | **60 fps a resolución completa** |
| 2 | Cada commit de memoria guest parseaba `/proc/self/maps` | tirones en mapa/mundos | un `mprotect` por commit |
| 3 | `WaitMultiple` hacía polling | audio worker: 73% de un núcleo | **7%** |
| 4 | `SDL_WaitEvent` giraba en Android (SDL3) | UI thread: 100% de un núcleo | ~0% |
| 5 | El juego seguía corriendo tras bloquear la pantalla | carga y calor con pantalla apagada | en pausa hasta volver |
| 6 | Planos de vídeo decodificados a RGBA8 texel a texel | 10 ms y 5,4 MB de uploads por frame | decoder 12× más rápido, uploads ÷4 |
| 7 | Renderer exigía Vulkan 1.2 + 5 sets | no arrancaba en Adreno 6xx (Vulkan 1.1, 4 sets) | corre en Vulkan 1.1 puro |
| 8 | Trabajo CPU por draw en el renderer | `CopyVertices` solo: 14% del game thread | solo vértices y constantes usados |
| 9 | CPU esperaba a la GPU al final de cada frame | 3–8 ms por frame | 2 frames in flight, espera 0,0 ms |
| 10 | Pipelines compilados al primer uso | tirones de 50–115 ms | caché en disco + pre-warm |

Detalle de los aplicables a el título (el resto está en el doc original):

- **#1 Swapchain (el más relevante para el título que no llena la pantalla).** VERIFICADO:
  el swapchain usaba pre-transform identidad y dejaba que el compositor rotara la imagen a
  paisaje; la pantalla reporta rotación 90°, así que `vkQueuePresentKHR` devolvía
  `VK_SUBOPTIMAL_KHR` **en cada present**. El renderer trataba SUBOPTIMAL como "stale" y
  cada frame pagaba `vkDeviceWaitIdle` + destruir el swapchain + 3 buffers nuevos de
  2340×1080 (también la fuente del chorro de mensajes `qdgralloc` en logcat). **Arreglo:**
  solo `VK_ERROR_OUT_OF_DATE_KHR` y `VK_ERROR_SURFACE_LOST_KHR` re-crean el swapchain; un
  swapchain subóptimo sigue presentando bien (`tools/native_renderer/vk_renderer.h`).
  Resultado: 43–46 → 60 fps. **INFERIDO para el título:** si el runtime del título re-crea el
  swapchain ante SUBOPTIMAL, es sospechoso nº 1 tanto del coste por frame como de los
  problemas de superficie/título. Es una comprobación barata (grep del manejo de
  SUBOPTIMAL + contar re-creaciones en logcat).
- **#3 WaitMultiple (relevante para el 96% CPU / contención de mutex del título).** VERIFICADO:
  `WaitMultiple` (equivalente a `WaitForMultipleObjects`) intentaba bloquear cada handle,
  los comprobaba, dormía 1 ms y volvía a empezar; cuando un `trylock` fallaba hacía yield y
  reintentaba **sin dormir ni comprobar el timeout**. El audio worker espera así en 9
  handles permanentemente. **Arreglo (parche 0006):** un handle señalizado incrementa un
  contador de generación global y despierta a los multi-waiters por una sola variable de
  condición; wait-any comprueba cada handle bajo su propio lock; wait-all bloquea en orden
  de dirección para no deadlock. Audio worker 73% → 7% de un núcleo. **INFERIDO para el título:**
  si el runtime del título comparte este `WaitMultiple` de ReXGlue (misma base), el patrón de
  `pthread_mutex_trylock` + CAS + `clock_gettime` en el perfil es la misma firma. El parche
  0006 es candidato directo a portar (ver §5).
- **#2 `/proc/self/maps` (parche 0005).** VERIFICADO: cada commit hacía `mmap(MAP_FIXED_NOREPLACE)`
  (siempre falla con `EEXIST` por la reserva inicial) y luego `IsRangeFullyMapped`, que leía y
  parseaba las 3.449 líneas / 351 KB de `/proc/self/maps` con el mmap lock del kernel retenido
  (los page faults de los demás hilos se atascan mientras). El `mprotect` ya falla con
  `ENOMEM` si no todo está mapeado, así que el paso 2 comprobaba algo que el kernel ya
  comprueba. Arreglo: el commit con dirección base prueba `mprotect` primero.
- **#4 SDL3.** VERIFICADO: en Android, `SDL_WaitEventTimeoutNS` mete un evento centinela en
  cada pasada; cada evento despierta el hilo y en Android un wakeup es un evento de ciclo de
  vida que termina el siguiente `Android_PumpEvents(-1)` al instante: la espera se despierta
  a sí misma para siempre. Arreglo: `SDL_POLL_SENTINEL=0` antes de arrancar SDL
  (`RaymanActivity`); el gamepad virtual empuja un evento de usuario vacío para despertar el
  hilo (`rex/src/android_touch.cpp`).
- **#6 Vídeo.** VERIFICADO: los planos Y/U/V del vídeo (1280×720 + 2× 640×360, formato `k_8`)
  se expandían a RGBA8 (4× bytes), calculaban la dirección tiled dos veces por texel y
  decodificaban a un vector nuevo a cero antes de copiar al staging. Arreglo: `k_8` como
  imagen `R8_UNORM` con swizzle RRRR en la vista (el shader lee lo mismo con ¼ de bytes),
  decodificación directa al staging, y offsets tiled por tramos de 8 bloques. Comprobado
  contra el decoder viejo en 4.000 casos aleatorios (idéntica salida). 1280×720 `k_8`:
  8,5 → 0,7 ms; uploads 5,4 → 1,35 MB por frame.
- **#7 Vulkan 1.1.** VERIFICADO (Redmi 10C, Adreno 610, driver 512.502): Vulkan 1.1.128, sin
  `VK_EXT_descriptor_indexing`, `maxBoundDescriptorSets = 4`, **sin compresión BC**
  (ETC2 y ASTC sí). Arreglo (`tools/native_renderer/spirv_patch.h`): reescribe el SPIR-V al
  cargarlo — arrays runtime (`OpTypeRuntimeArray`) pasan a arrays de tamaño fijo (1024
  texturas, 16 samplers), se quitan capacidades/decoraciones de descriptor indexing y
  `NonUniform`; los 5 sets se empaquetan en 4 (2D, 3D+cubo, samplers, constantes); todos los
  slots del heap se rellenan con un placeholder 1×1 (sin partially-bound). Los 35 shaders del
  juego pasan `spirv-val --target-env vulkan1.1`.
- **#8 Por-draw.** VERIFICADO: `CopyVertices` hacía byte-swap de **todo** el vertex buffer de
  la fetch constant en cada draw (el juego comparte buffers grandes entre muchos draws
  pequeños) → solo el rango que usa la primitiva, con índices rebasados. `FillConstants`
  hacía byte-swap de 2× 4 KB por draw → solo los bytes que lee cada shader, según su bloque
  uniforme en el SPIR-V (~la mitad de media). Claves de caché `std::string` con `to_string`
  en cada draw → claves struct planas, buffers reutilizados, firma de contenido 1× por
  textura y frame.
- **#9 Frames in flight.** VERIFICADO: 2 frames in flight, cada uno con sus buffers de
  vértices/índices/constantes/staging, command buffers, fence, semáforos, heap set y constant
  set. `BeginFrame` solo espera al frame de hace dos. Espera de GPU 0,0 ms en A56 y S23.
  Buffers por frame ~96 MB, 192 MB en total (antes 272 MB para un solo frame).
- **#10 Pipelines.** VERIFICADO: `VkPipelineCache` cargado de `files/pipeline_cache.bin` +
  lista `files/pipelines.txt`; todos los pipelines usados se crean al arrancar; ambos se
  guardan (como mucho cada 5 s) cuando aparecen nuevos. Desde la segunda sesión, un pipeline
  conocido no cuesta nada en mitad del juego (una caché de otro driver la rechaza el driver
  y se empieza vacía).
- **#5 Fondo/bloqueo.** VERIFICADO: con el teléfono bloqueado el juego seguía renderizando y
  decodificando el vídeo attract. Arreglo: event watch SDL `WILL_ENTER_BACKGROUND` /
  `DID_ENTER_FOREGROUND` y `Present` retiene el juego en background. Dos crashes encontrados
  de paso: arrancar tras la pantalla de bloqueo (sin ventana: `ANativeWindow` nulo llegaba a
  `vkCreateAndroidSurfaceKHR` y el fault handler reintentaba para siempre, 100% CPU sin
  imagen — la factoría de superficies ahora espera a la ventana) y `onPause` antes de que el
  juego arrancara (el overlay táctil tocaba un mutex destruido — el pad espera al primer
  frame presentado).

### Rendimiento de Legends — VERIFICADO (poco)

- README: "**Tested on Snapdragon 845 at 60 FPS with 100% resolution scale.** Performance may
  vary depending on the device and GPU drivers." / "Runs well on the hardware tested so far.
  No wide compatibility testing has been done across different GPUs/drivers yet."
- No hay código ni docs de rendimiento en el repo; el log de #7 muestra perfil `balanced`
  (sin que se sepa qué cambia entre perfiles — no leído en ninguna fuente).
- **INFERIDO:** Legends rinde bien en Adreno porque usa el camino emulado estándar de ReXGlue
  sin tocarlo; no hay ninguna técnica de rendimiento propia que extraer de Legends. Todo el
  material de rendimiento útil viene de Origins.

---

## 4. El renderer nativo de Origins — VERIFICADO (`docs/NATIVE_RENDERER.md`)

- El juego corre **sin emulación de GPU Xbox 360**: `rex/src/native_capture.cpp` intercepta
  en el nivel D3D `CreateVertexShader`/`CreatePixelShader` (cada shader se hashea con XXH3;
  ese hash es el nombre del SPIR-V) y los tres puntos de entrada de dibujo (DrawIndexed,
  DrawVertices, DrawVerticesUP). Por cada draw, `tools/native_renderer/vk_renderer.h` lee el
  shadow de registros Xenos que D3D guarda en la estructura del device (fetch constants,
  shader constants, render state), copia vértices e índices (big-endian → little-endian),
  decodifica las texturas, elige un pipeline por shaders + blend state y dibuja. Listas de
  triángulos, strips, fans y quad lists se convierten a listas indexadas de triángulos.
- Comparativa medida (misma fuente): M1 title screen, emulación 30–55 fps con stalls de
  100–620 ms → nativo ~59 fps; Galaxy S23, emulación "slow, stalls" → nativo 60 fps,
  peor frame ~18 ms.
- **Layout de vértices del motor (¡directamente reutilizable para el título!):** D3D parchea los
  vertex fetches de los shaders en cada draw a partir del stride del stream
  (`device + 0x3268 + stream` guarda stride/4); el stride identifica el struct de vértice
  el motor: **16 PC, 20 PT, 24 PCT, 28 font, 36 PNCT, 64 patch**. Los fetches rellenados viven
  en los bindings por declaración del objeto shader. el título usa los mismos structs del motor con
  casi total seguridad (mismo motor, misma época) — verificar contra el binario del título.
- **Render targets / resolve:** cada draw va al target nombrado por `RB_SURFACE_INFO` /
  `RB_COLOR_INFO` (`device + 0x2880 / 0x2884`) con el viewport del juego; el target más ancho
  es el back buffer, dibujado a resolución de pantalla y bliteado a la ventana con
  letterbox. `Resolve` copia un rectángulo del target a una textura registrada en la
  dirección destino, que luego se muestrea en vez de memoria guest (glow/blur de AfterFx,
  refracción). La textura destino tiene su fetch constant en `texture + 0x1C` y su dirección
  es virtual (`0xE0000000+`): se registra bajo la dirección física que usan los draws de
  muestreo, `(addr & 0x1FFFFFFF) + 0x1000`. El agua (refracción) renderiza desde que se
  arreglaron las direcciones de resolve; antes salía negra.
- **Widescreen (relevante para el título del título).** VERIFICADO: `RAYMAN_WIDESCREEN=<aspect>`
  (o `auto`) parchea las **dos constantes de ajuste 16:9 del motor (16/9 en `0x8201EF58`,
  9/16 en `0x8201EF5C`, usadas por `sub_824C8798`)** y el renderer encaja ese aspecto en la
  ventana. El modo de vídeo guest debe coincidir (p. ej. 1560×720). En Android está activo
  por defecto con el renderer nativo: la app fija un modo de vídeo de 720 líneas con el
  aspecto de la pantalla (19.5:9 en el S23). **INFERIDO para el título:** buscar en el binario de
  el título el mismo par de flotantes 16/9 y 9/16 usados por la función de ajuste de aspecto es una
  vía barata para el título que no llena la pantalla; las direcciones serán distintas
  (otro ejecutable), el patrón es el mismo.
- **Shaders:** se compilan ahead-of-time a SPIR-V con XenosRecomp (`sh tools/native_renderer/build_spirv.sh`
  → `private/native/spirv_ubo/<HASH>_{vs,ps}.spv`); `hlsl_ubo.py` mueve las constantes de
  XenosRecomp a uniform buffers, de modo que **no hacen falta enteros de 64 bits ni buffer
  device addresses** ("so stock Adreno drivers work"). Correcciones al cargar el SPIR-V
  (`spirv_patch.h`, `spirv_inputs.h`): **`setp_inv`** — el escalar Xenos `setp_inv` es
  `src == 1 ? 0 : (src == 0 ? 1 : src)`; XenosRecomp emite `src == 0 ? 1 : src` y pierde el
  primer caso; eso dejaba invisible el primer segmento del puente del cielo y rompía las
  enredaderas del mapa en trozos rectos (las "friezes" del motor evalúan una Bézier por
  vértice). `FixSetpInv` reescribe cada `select(a == 0.0, 1.0, a)` como
  `select(a == 1.0, 0.0, select(a == 0.0, 1.0, a))`. Y **direccionado relativo de
  constantes**: shaders que indexan constantes con índice calculado (`c[a0 + n]`) reciben el
  registro completo de 256, como en la consola (el índice podía llegar a c224 donde el bloque
  declarado terminaba en c208).
- **Texturas — VERIFICADO (`tools/native_renderer/xenos_texture.h`, leído entero):**
  decodifica **DXT1/3/5 → RGBA8 en CPU** (`DecodeTextureTo`; formatos Xenos 0x12 DXT1,
  0x13 DXT2/3, 0x14 DXT5, 0x06 8888, 0x02 k_8), con tiling 2D, packed mips y endian swap.
  `k_8` queda en 1 canal (R8 + swizzle RRRR). Cobertura medida offline: **las 8.358 texturas
  de los bundles del juego usan DXT1, DXT2/3, DXT4/5 o 8888, todas soportadas**. Es decir:
  Origins paga el mismo 4× del título en texturas DXT (no hay transcodificación a ETC2/ASTC en
  el renderer nativo) y aun así llega a 60 fps en un S23 — porque el coste lo dominaban
  otras cosas (swapchain, polling, por-draw). **El propio `docs/ANDROID.md` (§ "Next")
  apunta como trabajo pendiente que UnleashedRecomp-Android "already solves … BC→ETC2
  texture transcoding"** — esa es la referencia concreta para el problema DXT-en-Mali de
  el título, no Origins. Opciones para el título, en orden de esfuerzo (INFERIDO): (a) mantener
  decode-a-RGBA8 como Origins y medir si de verdad es el cuello de botella en el G99;
  (b) transcodificar DXT→ETC2/ASTC como UnleashedRecomp-Android (compresión nativa en Mali,
  ¼ de memoria/ancho de banda frente a RGBA8); (c) subir texturas como BC donde el driver
  lo soporte — no existe en Mali, descartado.
- **Depuración reproducible en PC sin teléfono.** VERIFICADO: captura de frame en el
  dispositivo (`adb shell touch …/files/captures/dump_now`, `adb pull …/native_frame.bin`) y
  reproducción en PC con `vkrender` (mismo renderer, GPU discreta), con selección de draws
  (`VKRENDER_ONLY=0,12`). El dump guarda el estado D3D de cada draw, la memoria que lee y
  los containers de shaders. `tools/native_renderer/dumpview` imprime los draws y decodifica
  las texturas. Técnica transferible tal cual a el título para los bugs de textura.
- **Headless / autopilot.** VERIFICADO: `RAYMAN_NATIVE_RENDER=offscreen`,
  `RAYMAN_OFFSCREEN_SIZE=WxH`, `SDL_VIDEO_DRIVER=offscreen`, `--audio_mute=true`,
  `RAYMAN_AUTOPILOT="18:start,22:a,25:a,50:right,52:jump"` (`rex/src/autopilot.cpp`).
- **Jumptables:** el analizador stock de XenonRecomp encuentra 0 jump tables en Rayman
  Origins (el compilador reordena instrucciones e inserta `nop`s); su detector
  `tools/jumptables` simula cada bloque switch y resuelve las **173**. También emite límites
  de función explícitos para leaf functions. Dato útil si el título tiene switches sin resolver.

---

## 5. Los 7 parches ReXGlue de Origins — VERIFICADO

Directorio `android/rexglue-patches/`, contra **ReXGlue SDK v0.10.0**
(`git am ../../../android/rexglue-patches/*.patch` sobre el clon del SDK).
Aplicación documentada en `docs/ANDROID.md` §1.

| Parche | Asunto (nombre de archivo / subject leído) | Contenido verificado |
|---|---|---|
| 0001 | `0001-Android-arm64-support-platform-detection-ANativeWind.patch` — "[PATCH 1/2] Android (arm64) support: platform detection, ANativeWindow surface, SDL_main entry, AArch64 fibers, Bionic/libc++ shims" — autor BelmanteGu, 2026-09-25 | Detección de plataforma (Android es `UNIX` sin X11/Wayland; `APPLE` no debe filtrarse del host macOS antes de `project()`); superficie `ANativeWindow` tomada de SDL3 (`surface_android.h/.cpp`); entrada `SDL_main` que fija `HOME` y la carpeta de datos; **fibras AArch64** (Bionic no tiene `getcontext`/`swapcontext`: `rex_fiber_swap` en ensamblador, guarda x19–x30, sp, d8–d15); shims libc++ (`from_chars` flotante, `clock_cast`, `jthread`) y Bionic (sin robust mutexes); el runtime localiza su plugin GPU junto a su propia librería con `dladdr` (`/proc/self/exe` es el zygote) |
| 0002 | `0002-Android-call-the-platform-AndroidInitialize-hooks-lo.patch` — "[PATCH 2/2] Android: call the platform AndroidInitialize hooks, log to app external storage" — BelmanteGu, 2026-09-25 | Llama `rex::memory::AndroidInitialize()`, `rex::thread::AndroidInitialize()`, `rex::filesystem::AndroidInitialize()` (entry points API-26+ cargados dinámicamente); `HOME` → almacenamiento interno de la app; datos por defecto en `<external>/game` (escribible por `adb push` sin permisos); log también en external (`rayman.log`, porque el default junto al ejecutable es el directorio de solo lectura de la librería nativa) |
| 0003 | `0003-rexgpu-null-plugin-command-processing-without-GPU-em.patch` | Plugin `rexgpu-null`: procesa los comandos GPU sin emulación (ring buffer, fences, interrupciones, vblank) — no leído el diff entero; descripción de `docs/NATIVE_RENDERER.md` § "rexgpu-null … keeps the guest GPU protocol running … without drawing" |
| 0004 | `0004-rexgpu-null-no-presenter-leave-the-window-to-the-nat.patch` | `rexgpu-null` sin presenter: deja la ventana al renderer nativo — no leído el diff entero; misma fuente que 0003 |
| 0005 | `0005-memory_posix-commit-guest-memory-with-mprotect-not-b.patch` | Commit de memoria guest con `mprotect` en vez de parsear `/proc/self/maps` (ver §3 #2) |
| 0006 | `0006-threading_posix-WaitMultiple-sleeps-until-a-handle-i.patch` | `WaitMultiple` duerme hasta que un handle se señaliza, en vez de polling (ver §3 #3) |
| 0007 | `0007-filesystem-PackedDevice-reads-the-game-from-a-compac.patch` | `PackedDevice`: sirve los archivos del juego desde un `game.rdpk` deduplicado y memory-mapped (ver §6) |

Notas:
- 0001 y 0002 son la pareja de soporte Android (1/2 y 2/2); 0003–0004 son la pareja
  `rexgpu-null`; 0005–0007 son los parches de rendimiento/empaquetado. Los diffs de 0003,
  0004, 0005, 0006 y 0007 **no se leyeron línea por línea** (solo nombres + descripción en
  los docs); sus efectos están documentados en `ANDROID_PERFORMANCE.md` y `NATIVE_RENDERER.md`.
- **INFERIDO:** 0001, 0002, 0005 y 0006 son genéricos de ReXGlue-en-Android y portables a el título
  casi tal cual (mismo SDK base). 0003–0004 solo tienen sentido si el título adopta renderer
  nativo. 0007 es portable si el título adopta el formato `.rdpk` o adapta `PackedDevice` a sus
  `.ipk`.

---

## 6. Empaquetado — VERIFICADO

### Origins (documentado en `docs/ANDROID.md` §4 + README fase 8)

- **El juego NO va dentro de la APK.** VERIFICADO: "The game is over 4 GB, so it can't go
  inside the APK, which is limited to Zip32." La app lee `default.xex` y los bundles de la
  carpeta de datos (`files/game`).
- **Import normal:** `python tools/make_game_pack.py` empaqueta `private/game` en
  `private/dist/RaymanOrigins-game.zip` (stored, Zip64). Se copia al teléfono, se abre la app
  y se toca **"Import game pack (.zip)"**: extrae con barra de progreso y arranca el juego.
  Alternativa por adb: `adb push private/game/. $G/` con
  `G=/sdcard/Android/data/io.github.belmantegu.raymanrecomp/files/game` (+ `chmod 777` en
  directorios, porque los creados por adb pertenecen a `shell` modo 770 y el proceso de la
  app no puede atravesarlos).
- **Compact app (la respuesta al problema de tamaño).** VERIFICADO: los bundles repiten
  muchos datos entre niveles. `python tools/make_compact_pack.py` guarda cada tramo distinto
  de bytes una sola vez y describe cada archivo como lista de piezas del almacén:
  **5,4 GB → 1,1 GB** (`private/dist/RaymanOrigins-compact.rdpk`), sin pérdidas. El script
  reconstruye cada archivo desde el pack y lo verifica byte a byte antes de escribirlo. El
  runtime lee el pack **memory-mapped, como si los archivos originales estuvieran ahí**
  (parche 0007). Es una app separada instalada junto a la normal, con su propio nombre y
  archivos (`ORG_GRADLE_PROJECT_raymanCompact=true` → **Rayman Origins Compact**,
  `io.github.belmantegu.raymanrecomp.compact`); su home importa el `.rdpk` en vez del zip.
- **Shaders dentro de la APK:** `android/build_apk.sh` empaqueta los shaders de
  `private/native/spirv_ubo` en la APK local; el launcher los extrae en el primer arranque.
- **Saves:** Opciones → Back up saves (zip a elegir, p. ej. Downloads) / Restore saves (pide
  confirmación, reemplaza y restaura los viejos si el zip no se lee).
- **Home/launcher:** pantalla propia estilo juego (Play, Options, Game files, Quit), táctil o
  con mando; el juego es solo horizontal (`docs/PORT_HOME.md`).
- **Controles:** stick flotante + A/X/B/Y/RT (Run es toggle por defecto), Back y Start
  arriba; overlay = gamepad virtual SDL3, el juego ve un mando Xbox 360 normal; mandos
  físicos y teclados por SDL; opciones por línea en `files/args.txt`.

### Legends (lo poco que hay — VERIFICADO)

- Sin código: el usuario **selecciona la carpeta del juego que contiene el `.xex`** cuando
  la app lo pide (README: "Select the game folder containing the `.xex` file when prompted").
  El log de #7 confirma la ruta efectiva: `…/Android/data/org.rexglue.raymanlegends/files/game`.
  No hay pack, ni dedup, ni importación por zip documentados. APK prebuilt en Releases
  (0.1.0 y 0.2.0, 3 assets cada una — contenido de los assets no inspeccionado).
- **INFERIDO:** Legends es esencialmente el template Android stock de ReXGlue con otro juego:
  carpeta externa + `default.xex`, sin trabajo propio de empaquetado. Nada que copiar para el
  problema de doble copia del título, salvo el principio común: el juego vive fuera de la APK.

### Lectura para el título (INFERIDO)

- El problema "APK 1,68 GB + copia extraída 1,58 GB" del título es exactamente lo que Origins
  resolvió con dos decisiones: (1) el juego nunca entra en la APK (import por zip/pack desde
  fuera); (2) el pack compacto deduplicado memory-mapped (5,4 → 1,1 GB, −80%). Los bundles
  `.ipk` del título probablemente también repiten datos entre niveles (misma estructura el motor);
  probar `make_compact_pack.py` de Origins sobre los `.ipk` del título — o reimplementar la idea
  (tramos únicos + tabla de piezas + mmap) — es la vía con más retorno para el requisito de
  <2 GB sin duplicar. La alternativa mínima (leer los `.ipk` directamente desde dentro de la
  APK vía `AssetManager`/zip sin extraer) no la hace ninguno de los dos proyectos; sería
  trabajo nuevo y choca con el límite Zip32 que cita Origins.

---

## 7. Qué técnicas se pueden aplicar a el título (INFERIDO, priorizado)

Mismo motor (el motor), mismo toolchain (XenonRecomp + ReXGlue + XenosRecomp), mismo teléfono
objetivo. Ordenado por retorno esperado / esfuerzo:

1. **Parche 0006 (`WaitMultiple` sin polling) — PRIORIDAD MÁXIMA.** el título: CPU 96% con mucha
   contención de mutex. Origins midió la misma firma (trylock + CAS + clock_gettime) y el
   audio worker pasó de 73% a 7% de un núcleo. Portar el parche y medir con `simpleperf` es
   el primer paso barato. Misma familia: parche 0005 (`mprotect` en vez de `/proc/self/maps`).
2. **Swapchain: no re-crear ante `VK_SUBOPTIMAL_KHR`.** Sospechoso nº 1 del título que no
   llena la pantalla y de coste por frame. Verificar en el runtime del título cómo se trata
   SUBOPTIMAL y contar re-creaciones (logcat `qdgralloc` es la firma que vio Origins).
   En Origins el arreglo solo valió 43–46 → 60 fps.
3. **Widescreen del motor: buscar el par de constantes 16/9 y 9/16 en el binario del título**
   (en Origins: `0x8201EF58` / `0x8201EF5C`, función `sub_824C8798`) y parchearlas según el
   aspecto real, con el modo de vídeo guest a juego (720 líneas × aspecto de pantalla).
   Directamente dirigido al título sin llenar.
4. **Renderer nativo en vez de emulación Xenos — el cambio estructural.** Es lo que permite
   a Origins correr a 60 fps en Adreno y Xclipse, y lo que esquiva el requisito
   `vertexPipelineStoresAndAtomics` que tumba a Mali-G57. Piezas reutilizables documentadas:
   hooks D3D por hash XXH3, structs de vértice el motor (16/20/24/28/36/64), resolve por
   dirección física `(addr & 0x1FFFFFFF) + 0x1000`, `rexgpu-null` (0003–0004), corrección
   `setp_inv` en SPIR-V, constantes completas de 256 registros. Es trabajo grande, pero es
   la única vía leída que resuelve a la vez rendimiento, Mali y (parcialmente) texturas.
   Requiere verificar que los shaders del título no necesiten lo que el renderer de Origins no
   soporta (p. ej. enteros 64 bits / buffer device addresses — en Origins no hacían falta).
5. **Texturas DXT:** a corto plazo, el decode-a-RGBA8 de Origins (`xenos_texture.h`) es
   reutilizable tal cual (mismos formatos Xenos 0x12/0x13/0x14/0x06/0x02, tiling y packed
   mips) y Origins demuestra que no impide 60 fps en un gama alta. A medio plazo, la
   transcodificación **BC→ETC2** (citada en `ANDROID.md` como ya resuelta por
   UnleashedRecomp-Android) es la respuesta al 4× de memoria/ancho de banda en Mali.
   Antes de optimizar: medir con los contadores de frame (ver punto 7) si las texturas son
   de verdad el cuello de botella en el G99 o si lo dominan swapchain/polling/por-draw.
6. **Pack compacto deduplicado (`.rdpk` + `PackedDevice`, parche 0007).** La respuesta
   directa al requisito oficial (<2 GB, sin duplicar): juego fuera de la APK + dedup por
   tramos + lectura memory-mapped. Probar el script de Origins sobre los `.ipk` del título y
   medir la ratio.
7. **Metodología de medición de Origins, tal cual:** contadores de frame cada 120 frames
   (draw hooks / end / gpu wait + frames >20 ms con lo que crearon), `simpleperf` por hilo
   y función, perfil off-CPU para encontrar esperas (el hallazgo #1 solo aparece así),
   `adb shell top -H`. Y la captura de frame reproducible en PC (`vkrender` + `dumpview`)
   para depurar texturas sin el teléfono.
8. **Vulkan 1.1 como suelo:** el parche SPIR-V de Origins (arrays runtime → fijos, 5 → 4
   sets, placeholders 1×1) hace que el renderer pida solo Vulkan 1.1. Útil si el título debe
   correr en gamas bajas; el G99 soporta Vulkan 1.3, así que no es urgente para el teléfono
   actual, pero amplía el parque.
9. **SDL3 `SDL_POLL_SENTINEL=0`** y pausa en background (`WILL_ENTER_BACKGROUND`): dos
   líneas con efecto medido (UI thread 100% → ~0%; sin juego corriendo tras el bloqueo).
   Barato de verificar en el título.
10. **De Legends, poco:** su único valor es negativo — demuestra qué NO hacer: confiar en el
    plugin Xenos stock en Mali (crash duro en arranque, #7) y no tener ni un solo issue
    cerrado. Su README sí deja una práctica útil: pedir a los usuarios el log
    (`Android/data/<paquete>`) junto con modelo/GPU/Android en cada reporte.

## 8. Lo que NO se pudo verificar (sin inventar)

- Diffs línea por línea de los parches 0003, 0004, 0005, 0006 y 0007 (solo nombres +
  descripción en docs; los diffs existen en el repo y se pueden leer después).
- Contenido de los logs adjuntos de #7 distintos de `rayman_legends_runtime.log`
  (`launcher-20261022-173924.log`, `launcher-20261022-173824.log`): no se leyeron.
- Los demás issues de Legends (#1–#6, #8–#13): no se revisaron; solo se leyó la lista
  (15 abiertos, 0 cerrados) y #7, #14, #15.
- El alias "Yato" como autor de Legends-Recomp: no aparece en ninguna fuente leída.
- Contenido de los assets de las releases 0.1.0/0.2.0 de Legends (3 assets cada una).
- `docs/PROGRESS.md` (sección 7, investigación del renderer) y `docs/D3D_MAP.md`,
  `docs/PORT_HOME.md` de Origins: no se leyeron; pueden contener más detalle transferible.
- Si Origins corre en algún Mali: no hay evidencia ni a favor ni en contra.
- `docs/ANDROID.md` de Origins tiene la cabecera desactualizada ("The APK has not been run
  on a device yet", "Graphics still go through … Xenos emulation") frente al README y a
  `NATIVE_RENDERER.md`, que documentan el renderer nativo ya en el S23: el doc de Android
  va por detrás del estado real.

## 9. Fuentes concretas

- `https://github.com/XDanfr/RaymanOriginsRecomp` (README; HEAD `c1bf7b90…`, 80 commits)
- `https://github.com/XDanfr/RaymanOriginsRecomp/blob/main/docs/ANDROID_PERFORMANCE.md`
- `https://github.com/XDanfr/RaymanOriginsRecomp/blob/main/docs/NATIVE_RENDERER.md`
- `https://github.com/XDanfr/RaymanOriginsRecomp/blob/main/docs/ANDROID.md`
- `https://github.com/XDanfr/RaymanOriginsRecomp/tree/main/android/rexglue-patches`
  (0001–0007; 0001 y 0002 leídos enteros, resto solo listado + descripción en docs)
- `https://github.com/XDanfr/RaymanOriginsRecomp/blob/main/tools/native_renderer/xenos_texture.h`
  (leído entero, 406 líneas)
- `https://github.com/edeegg/Legends-Recomp` (README; HEAD `416031e…`, 5 commits)
- `https://github.com/edeegg/Legends-Recomp/releases` (0.1.0 2026-10-07; 0.2.0 2026-10-15)
- `https://github.com/edeegg/Legends-Recomp/issues/7` + log adjunto
  `rayman_legends_runtime.log` (leído entero, 78 líneas)
- `https://github.com/edeegg/Legends-Recomp/issues/14`
- `https://github.com/edeegg/Legends-Recomp/issues/15`
- Búsqueda web: `"RaymanOriginsRecomp" Mali` (sin menciones de Mali en el proyecto);
  `vertexPipelineStoresAndAtomics` aparece en un post de Facebook sobre L4D2 Recomp en
  Mali-G57, no en el cuerpo del issue #7 de Legends (la cadena está en su log adjunto).

# FASE 4 — Diagnóstico de pantalla (bugs A y B)

Investigación SOLO LECTURA. Todas las afirmaciones se verificaron leyendo el
código real de `~/workspace/col-recomp/tools/rexauto/android/` (app + SDK
vendido, incluido el SDL3 de `thirdparty/sdl3`). Nada de memoria.

Fecha: 2026-11-13. Teléfono de referencia: Helio G99 / Mali-G57 MC2
(Samsung A17, pantalla 1080×2340; los cálculos usan 1080×2400 como ejemplo
genérico de vertical y 1080×2340 entre paréntesis — la forma es idéntica).

Rutas base (para no repetirlas en cada cita):
- `APP = tools/rexauto/android/app/src/main/`
- `SDK = tools/rexauto/android/sdk/rexglue-sdk/`
- `SDL = SDK/thirdparty/sdl3/`

---

## Veredicto rápido

| Bug | Causa exacta (una línea) |
|---|---|
| **A — no gira sola a horizontal** | El `sensorLandscape` del manifest **y** el `setRequestedOrientation()` de `MainActivity` son **sobrescritos por el propio SDL**: al crear la ventana, SDL llama a Java `SDLActivity.setOrientation(1280, 720, resizable=true, hint="")` y eso termina en `SCREEN_ORIENTATION_FULL_USER`, que sigue el ajuste de rotación del usuario en vez de forzar horizontal. El último escritor gana, y el último escritor es SDL. |
| **B — título aplastado en la mitad superior** | Consecuencia directa de A: la primera superficie nace **en vertical** (1080×2400) y el presentador crea el swapchain en vertical y dibuja el fotograma 16:9 del juego con letterbox dentro de esa superficie vertical: una banda de **1080×607 px** (25 % de la altura) con negro arriba y abajo. El presentador NO está roto: su matemática de letterbox es correcta para la superficie que le dan; le dan una superficie vertical. |

**Un solo arreglo cura los dos bugs**: forzar horizontal **antes** de que SDL
cree la ventana (o, equivalentemente, neutralizar la llamada `setOrientation`
de SDL), de modo que la primera superficie ya nazca en horizontal
(2400×1080 / 2340×1080) y el swapchain y el letterbox se calculen sobre ella.
Detalle en §4.

---

## 1. BUG A — La pantalla no gira sola a horizontal

### 1.1 Lo que SÍ está puesto para forzar horizontal (y por qué no basta)

Tres mecanismos intentan forzar horizontal, y los tres pierden contra SDL:

1. **Manifest** — `APP/AndroidManifest.xml:40`:
   `MainActivity` declara `android:screenOrientation="sensorLandscape"`.
   (La actividad del launcher, `SetupActivity`, es `fullUser` en
   `APP/AndroidManifest.xml:28`, por eso el launcher sí puede estar en
   vertical; eso es intencionado y no es el bug.)
2. **`MainActivity.applyOrientation()`** — `APP/java/com/rexauto/port/MainActivity.java:42-50`:
   lee la orientación del launcher (`GraphicsSettings.orientation()`,
   `"landscape"` por defecto, `GraphicsSettings.java:52`) y llama a
   `setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE)` en la
   línea 50.
3. **Pero el orden ya es tarde**: `MainActivity.java:24` ejecuta
   `super.onCreate(...)` **antes** de `applyOrientation()` en la línea 25.
   Dentro de `super.onCreate`, `SDLActivity` ya crea la superficie
   (`SDLActivity.java:458`: `mSurface = createSDLSurface(this)`) y la pone
   en pantalla (`SDLActivity.java:486`: `setContentView(mLayout)`), todavía
   en la orientación que trae la actividad al arrancar (vertical, heredada
   del launcher `fullUser`).

### 1.2 La causa exacta: SDL sobrescribe la orientación al crear la ventana

Cadena completa, eslabón por eslabón (el último escritor gana):

1. **El hilo SDL arranca en `onResume`**, no en `onCreate`:
   `SDLActivity.handleNativeState()` arranca `mSDLThread` cuando la
   superficie está lista y hay foco. Ese hilo ejecuta `SDL_main` →
   `RunAndroidApp()` en `APP/cpp/android_main.cpp`, que hace
   `SDL_InitSubSystem(SDL_INIT_VIDEO)` (`android_main.cpp:508`).
2. **La ventana se crea con 1280×720 y `RESIZABLE`**:
   `SDK/src/ui/rex_app.cpp:337` pide `Window::Create(app_context(),
   GetName(), 1280, 720)`; `SDK/src/ui/window_sdl.cpp:141-149` crea la
   ventana SDL con flags `SDL_WINDOW_RESIZABLE | SDL_WINDOW_HIGH_PIXEL_DENSITY
   | SDL_WINDOW_HIDDEN` y llama a `SDL_CreateWindow(...)` (línea 149).
   El ancho/alto pedidos (1280×720) vienen además del cvar
   `video_mode_width/height` (`window_sdl.cpp:50-55`, `window.cpp:50-54`).
3. **SDL (vendido) llama a Java para fijar la orientación**:
   `SDL/src/video/android/SDL_androidwindow.c:54`, dentro de
   `Android_CreateWindow`:
   ```c
   Android_JNI_SetOrientation(window->w, window->h,
       window->flags & SDL_WINDOW_RESIZABLE, SDL_GetHint(SDL_HINT_ORIENTATIONS));
   ```
   Es decir: `w=1280, h=720, resizable=1` (por el flag `RESIZABLE` del paso
   2). El hint `SDL_ORIENTATIONS` **no lo fija nadie** en la app ni en el
   SDK (grep de `SDL_HINT_ORIENTATIONS` / `SDL_SetHint` en `app/src` y en
   `sdk/.../src` e `include` — sin contar `thirdparty` — no devuelve nada),
   así que `SDL_GetHint` devuelve `NULL` y el JNI pasa `""`.
4. **El puente JNI** — `SDL/src/core/android/SDL_android.c:1712-1719`
   (`Android_JNI_SetOrientation`) — reenvía tal cual a
   `SDLActivity.setOrientation(w, h, resizable, hint)`.
5. **Java decide `FULL_USER`** — `APP/java/org/libsdl/app/SDLActivity.java:1114-1189`
   (`setOrientation` → `setOrientationBis`):
   - hint `""` → ni landscape ni portrait están permitidos explícitamente
     (líneas 1134-1152: ningún `contains("Landscape…")` / `contains("Portrait…")`
     casa).
   - `resizable == true` → rama de las líneas 1159-1162:
     ```java
     if (!is_portrait_allowed && !is_landscape_allowed) {
         if (resizable) {
             req = ActivityInfo.SCREEN_ORIENTATION_FULL_USER;   // línea 1162
     ```
   - Línea 1189: `mSingleton.setRequestedOrientation(req)` → **`FULL_USER`**.
6. **`FULL_USER` sigue el ajuste del usuario, no fuerza horizontal**:
   con la rotación automática desactivada (o el teléfono sostenido en
   vertical cuando cae la llamada), la actividad se queda en vertical.
   El `sensorLandscape` del manifest y el de `applyOrientation()` quedan
   anulados: SDL escribe **después** que los dos.

**Agravante — la sobrescritura no ocurre una sola vez**: cada llamada a
`SDL_SetWindowFullscreen` vuelve a llamar a `Android_JNI_SetOrientation`
(`SDL_androidwindow.c:174`, en `Android_SetWindowResizable`, y la misma
llamada en `Android_CreateWindow`). Y la app pone fullscreen dos veces:
`window_sdl.cpp:157` (bloque `#if REX_PLATFORM_ANDROID`) y
`window_sdl.cpp:182` (rama `IsFullscreen()`; `android_main.cpp:556` pasa
`--fullscreen=true`). Por tanto, **un `setRequestedOrientation()` puntual
no sirve**: hay que arreglarlo en el origen (el hint que SDL lee, o la
propia `setOrientationBis`), no con una llamada más.

### 1.3 Qué NO es la causa (descartado leyendo el código)

- **No es el launcher**: `SetupActivity` es `fullUser` a propósito
  (`AndroidManifest.xml:28`) y `launchGame()` solo hace
  `startActivity(new Intent(this, MainActivity.class))`
  (`SetupActivity.java`, al final del archivo). No toca la orientación del
  juego.
- **No es `SDLSurface`**: `SDLSurface.java` no fija orientación; su lógica
  de `skip` en `surfaceChanged()` (líneas 153-187) solo decide si la
  superficie está lista, y solo actúa cuando la orientación pedida es
  `PORTRAIT`/`SENSOR_PORTRAIT` o `LANDSCAPE`/`SENSOR_LANDSCAPE` — con
  `FULL_USER` no entra en ninguna rama, así que no bloquea ni provoca nada.
- **No es falta de `configChanges`**: `MainActivity` declara
  `orientation|screenSize|…` en el manifest (línea 37); la actividad no se
  recrea al rotar, pero eso no impide rotar.

---

## 2. BUG B — Título aplastado en la mitad superior

### 2.1 La superficie nace en vertical (consecuencia de A)

1. `SDLActivity.onCreate` crea la `SDLSurface` en vertical
   (`SDLActivity.java:458`, `setContentView` en `:486`) antes de que
   exista ninguna rotación.
2. El primer `surfaceChanged()` llega en vertical:
   `APP/java/org/libsdl/app/SDLSurface.java:117-149` — líneas 148-149:
   ```java
   SDLActivity.nativeSetScreenResolution(width, height, nDeviceWidth, nDeviceHeight, density, ...);
   SDLActivity.onNativeResize();
   ```
   con `width=1080, height=2400` (en el A17 real, 1080×2340).
   `nativeSetScreenResolution` escribe esos valores en
   `Android_SurfaceWidth/Height` (`SDL_androidvideo.c:202-205`, vía
   `Android_SetScreenResolution`).
3. **SDL descarta el 1280×720 pedido y adopta la superficie real**:
   `SDL_androidwindow.c:57-60` (`Android_CreateWindow`):
   ```c
   window->w = Android_SurfaceWidth;    // 1080
   window->h = Android_SurfaceHeight;   // 2400
   ```
   Y `window_sdl.cpp:199-201` lee `SDL_GetWindowSizeInPixels` y llama a
   `OnActualSizeUpdate(1080, 2400)`.
4. **El tamaño de superficie que ve el presentador es el del
   `ANativeWindow`**: `SDK/src/ui/surface_android.cpp:22-31`
   (`AndroidNativeWindowSurface::GetSizeImpl` devuelve
   `ANativeWindow_getWidth/Height`). El presentador lo guarda en
   `surface_width/height_in_paint_connection_` en
   `SDK/src/ui/presenter.cpp:1390-1391` (`UpdateSurfacePaintConnectionFromUIThread`,
   líneas 1346-1391).
5. **El swapchain Vulkan se crea en vertical**: el presentador reconecta
   con ese tamaño (`vulkan_presenter.cpp:769-772` y `:873-876`,
   `CreateSwapchainForVulkanSurface`) y la extensión se limita a las
   capacidades de la superficie (`vulkan_presenter.cpp:1143-1148`).
   Los framebuffers del swapchain usan esa extensión (`:1017-1018`).

### 2.2 La matemática del letterbox sobre una superficie vertical

El juego entrega su fotograma a 1280×720 y el modo de vídeo invitado es
16:9: `command_processor.cpp:2419` llama a
`Presenter::RefreshGuestOutput(guest_output_width, guest_output_height,
display_width, display_height, …)` con `display_width/height` leídos del
modo de vídeo del invitado (`VdQueryVideoMode`, líneas anteriores), y
`presenter.cpp:550-558` guarda esa relación como
`display_aspect_ratio_x/y = 16/9`.

`GetGuestOutputPaintFlow` (`presenter.cpp:857`) recibe como `host_rt`
**la extensión del swapchain** (`vulkan_presenter.cpp:1651-1655`):
`host_rt_width/height = swapchain_extent = 1080×2400`.

Cálculo (valores por defecto verificados: `present_allow_overscan_cutoff =
false` en `presenter.cpp:84`, así que `present_safe_area = 100` en las
líneas 912-916 y 948-952; `present_letterbox = true` por defecto en
`presenter.cpp:32-33` y en el launcher `GraphicsSettings.java:43`):

- Rama "superficie más alta que la fuente" (`presenter.cpp:906-907`):
  `1080×9 = 9720 < 2400×16 = 38400` → rama `else` (líneas 944-977).
- `output_width = rescale(2400, 16, 9) = 4267` (líneas 955-957) →
  excede el ancho → `letterbox = true` (líneas 959-962) →
  `output_width = 1080`, `output_height = rescale(1080×100, 9, 16×100)
  = 607` (líneas 964-967).
- `flow.output_x = 0`, `flow.output_y = (2400 − 607) / 2 = 896`
  (líneas 970, 977).
- Sin reescalado posterior: `host_rt == surface`, así que las líneas
  985-995 no cambian nada.

**Resultado**: la imagen del juego se dibuja como un rectángulo de
**1080×607 px en (0, 896)** dentro de una superficie de 1080×2400.
El rectángulo final se coloca con esas coordenadas en
`vulkan_presenter.cpp:1954-1955` (`effect_rect_x/y =
guest_output_flow.output_x/y`) y las constantes del quad en `:2019-2020`.
El viewport/scissor cubren todo el swapchain (`:1960-1963`), el resto
queda negro (rectángulos de limpieza de letterbox en
`presenter.cpp:1221-1262`).

Con la pantalla real del A17 (1080×2340): banda de 1080×607 en
`y = 866…1473`. En ambos casos la banda mide **≈25 % de la altura de la
pantalla** — eso es exactamente "aplastada": un 16:9 encajado en lo ancho
de una pantalla vertical.

### 2.3 Matiz honesto: centrada, no anclada arriba

El cálculo del código **centra** la banda verticalmente (`output_y = 896`,
no `0`): hay ~896 px negros arriba y ~897 px negros abajo. La descripción
del usuario ("mitad superior") no coincide literalmente con un anclaje
arriba (`y = 0`), y **el código no produce un anclaje arriba**: las dos
guardas "top-left" que existen en el código NO se disparan aquí, porque
exigen `output_x/output_y` entre 0 y 1:

- `presenter.cpp:1008-1022` (expande a todo el render target si la salida
  queda anclada en la esquina superior izquierda y es más pequeña que el
  RT): no se dispara, `output_y = 896`.
- `vulkan_presenter.cpp:1876-1899` (misma guarda en el pase final):
  tampoco, por el mismo motivo.

Lo que sí se puede afirmar con el código: **la imagen es una banda corta
y ancha (~25 % de la altura) con negro arriba y abajo, sobre una superficie
vertical**. Si en la captura la banda se ve en la mitad superior y no
centrada, eso no lo explica este cálculo — haría falta la captura o el
registro para distinguir entre (a) descripción aproximada del usuario de
esta misma banda centrada, o (b) otro efecto no visible en este código.
Lo que NO hay que hacer es "arreglar" la matemática del presentador: es
correcta para la superficie que recibe.

### 2.4 La recuperación al rotar SÍ está cableada (por eso el bug es el arranque)

Si el usuario fuerza la rotación, la cadena de redimensionado existe y
debería reconstruir el swapchain en horizontal:

`SDLSurface.surfaceChanged()` (nueva `width=2400, height=1080`) →
`nativeSetScreenResolution` (dimensiones frescas primero, `:148`) →
`onNativeResize` (`:149`) → JNI `onNativeResize`
(`SDL_android.c:1046-1056`) → `Android_SendResize`
(`SDL_androidvideo.c:276-301`, envía `SDL_EVENT_WINDOW_RESIZED`) →
`SDL_OnWindowResized` → `SDL_CheckWindowPixelSizeChanged`
(`SDL_video.c`, envía `SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED`) →
`WindowSDL::HandleWindowEvent` (`window_sdl.cpp:464-466`) →
`Window::OnActualSizeUpdate` (`window.cpp:548-566`) →
`Presenter::OnSurfaceResizeFromUIThread` (`presenter.cpp:390-415`) →
`UpdateSurfacePaintConnectionFromUIThread` (`:1346-1391`, relee el tamaño
con `surface_->GetSize`) → swapchain recreado en horizontal
(`vulkan_presenter.cpp:769-772`).

Es decir: **el presentador y el swapchain saben seguir una rotación; lo
que no existe es una rotación al arrancar** (bug A). Por eso el título se
ve aplastado: es el primer (y, mientras A persiste, único) pintado, sobre
la superficie vertical inicial. Si tras forzar la rotación el título
**siguiera** aplastado, eso sería un segundo defecto distinto y habría que
reabrir la investigación con captura/registro; con el código en la mano,
la rotación debería recuperarlo.

---

## 3. Qué NO es el bug B (descartado)

- **No es el `video_mode`**: 1280×720 es lo que el juego cree que es la TV
  (`GraphicsSettings.java:37-39`, `videoWidth/videoHeight`, cvars
  `video_mode_width/height` en `asCvars()` líneas 87-88). Es la entrada
  correcta del aspecto 16:9; el problema es la superficie, no el modo.
- **No es `present_letterbox`**: está en `true` por defecto
  (`presenter.cpp:32-33`, `GraphicsSettings.java:43`). Con `false`, la
  imagen se estiraría a toda la superficie vertical (deformada, no
  aplastada en banda) — no es el síntoma observado.
- **No es el escalado de resolución** (`resolution_scale = 1`,
  `draw_resolution_scale_x/y`): afecta al render interno del invitado,
  no al rectángulo de presentación.
- **No es `SDLSurface.surfaceChanged`**: su lógica de `skip` (líneas
  153-187) solo retrasa `mIsSurfaceReady` cuando la orientación pedida
  contradice el tamaño; con `FULL_USER` no se activa, y en cualquier caso
  no cambia dimensiones.

---

## 4. El arreglo concreto

### 4.1 Arreglo principal (cura A y B a la vez)

**Forzar horizontal antes de `SDL_CreateWindow`, en el origen que SDL
lee.** Dos opciones equivalentes; basta una (la primera es la más
quirúrgica porque ataca el hint que SDL ya consulta):

**Opción 1 — hint SDL (nativo, recomendada).** En
`APP/cpp/android_main.cpp`, en `RunAndroidApp()`, **antes** de
`SDL_InitSubSystem(SDL_INIT_VIDEO)` (línea 508):

```cpp
// Antes de SDL_InitSubSystem(SDL_INIT_VIDEO):
SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft");
```

Efecto verificado en el código: `SDL_androidwindow.c:54` leerá ese hint;
`setOrientationBis` (`SDLActivity.java:1134-1140`) con hint
`"LandscapeLeft"` (sin `"LandscapeRight"`) pone
`orientation_landscape = SCREEN_ORIENTATION_LANDSCAPE` (fijo, línea 1138);
como solo landscape está permitido y la ventana es redimensionable, la
rama de las líneas 1174-1175 devuelve ese `LANDSCAPE` fijo. Además, como
el hint persiste, las llamadas repetidas de `SDL_SetWindowFullscreen`
(`window_sdl.cpp:157,182`) re-aplican lo mismo en vez de volver a
`FULL_USER`. (Con `"LandscapeLeft LandscapeRight"` el resultado sería
`USER_LANDSCAPE`, línea 1136 — también horizontal pero dependiente del
sensor; el valor fijo de una sola dirección es más determinista.)

**Opción 2 — sobrescribir `setOrientationBis` en `MainActivity` (Java).**
`SDLActivity.setOrientation` es estático pero delega en la instancia
(`SDLActivity.java:1114-1117`: `mSingleton.setOrientationBis(...)`), y
`setOrientationBis` es un método de instancia sobrescribible. En
`MainActivity.java`, añadir:

```java
@Override
public void setOrientationBis(int w, int h, boolean resizable, String hint) {
    setRequestedOrientation(
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
}
```

Esto neutraliza **todas** las llamadas de SDL (creación de ventana y
cada `SDL_SetWindowFullscreen`), sin tocar el SDK ni el SDL vendido.
Combinable con la opción 1; no hace falta ninguna de las dos si se hace
la otra.

### 4.2 Lo que NO basta (no hacer solo esto)

- Cambiar solo el manifest a `sensorLandscape` — **ya está así**
  (`AndroidManifest.xml:40`) y SDL lo sobrescribe.
- Mover `applyOrientation()` antes de `super.onCreate()` en
  `MainActivity.java:24-25` — ayuda a que la superficie nazca en
  horizontal, pero SDL volvería a poner `FULL_USER` al crear la ventana;
  como mucho, acorta la ventana de tiempo. Útil como complemento, no como
  arreglo.
- Cambiar el launcher (`SetupActivity`) a horizontal — no afecta al juego
  y empeora el launcher; su `fullUser` es intencionado.

### 4.3 Defensa en profundidad (opcional, tras verificar 4.1)

- **No arrancar el hilo SDL con superficie vertical**: en
  `SDLActivity.handleNativeState()` la transición a `RESUMED` arranca
  `mSDLThread` cuando `mSurface.mIsSurfaceReady`. Si 4.1 fallara por algún
  motivo, el primer pintado seguiría siendo vertical. Solo si se observa
  ese caso, valorar retrasar el arranque hasta tener superficie
  horizontal; no es necesario si 4.1 funciona.
- **No tocar el presentador**: su letterbox (`presenter.cpp:857-1022`) y
  su reconexión al redimensionar (`presenter.cpp:390-415, 1346-1391`) son
  correctos; "arreglarlos" rompería el comportamiento en escritorio.
- **Verificación en el teléfono** (nada de esto está probado en real):
  1. Arrancar el juego con el teléfono en vertical y la rotación
     automática **apagada**: debe entrar directo en horizontal.
  2. La pantalla de título debe llenar la pantalla en horizontal
     (banda 16:9 a lo ancho, sin aplastar).
  3. Girar el teléfono durante el juego y volver: el swapchain debe
     seguir la rotación (cadena de §2.4) sin quedarse en negro ni
     aplastado.
  4. Si el título **sigue** aplastado tras forzar la rotación, reabrir
     con captura + `childoflight.log` (buscar las líneas
     `Window size: …` de `SDLSurface` y `Presenter: expanding…`).

---

## 5. Índice de citas (archivo:línea)

**Bug A**
- `APP/AndroidManifest.xml:28` — `SetupActivity: fullUser` (launcher).
- `APP/AndroidManifest.xml:40` — `MainActivity: sensorLandscape`.
- `APP/java/com/rexauto/port/MainActivity.java:24-25` — `super.onCreate`
  antes de `applyOrientation()`.
- `APP/java/com/rexauto/port/MainActivity.java:42-50` —
  `applyOrientation()` → `SENSOR_LANDSCAPE`.
- `APP/java/com/rexauto/port/MainActivity.java:43` / `APP/java/com/rexauto/port/GraphicsSettings.java:52` —
  orientación `"landscape"` por defecto.
- `APP/java/org/libsdl/app/SDLActivity.java:458,486` — superficie creada
  y `setContentView` dentro de `super.onCreate`.
- `SDK/src/ui/rex_app.cpp:337` — `Window::Create(…, 1280, 720)`.
- `SDK/src/ui/window_sdl.cpp:141-149` — `SDL_CreateWindow` con
  `SDL_WINDOW_RESIZABLE`.
- `SDK/src/ui/window_sdl.cpp:157,182` — `SDL_SetWindowFullscreen(true)`
  (re-dispara la orientación).
- `SDK/thirdparty/sdl3/src/video/android/SDL_androidwindow.c:54` —
  `Android_JNI_SetOrientation(w, h, resizable, hint)`.
- `SDK/thirdparty/sdl3/src/video/android/SDL_androidwindow.c:174` —
  misma llamada en `Android_SetWindowResizable`.
- `SDK/thirdparty/sdl3/src/core/android/SDL_android.c:1712-1719` —
  puente JNI a `SDLActivity.setOrientation`.
- `APP/java/org/libsdl/app/SDLActivity.java:1114-1117` —
  `setOrientation` → `mSingleton.setOrientationBis(...)`.
- `APP/java/org/libsdl/app/SDLActivity.java:1124-1189` —
  `setOrientationBis`; `:1159-1162` hint vacío + redimensionable →
  `FULL_USER`; `:1189` `setRequestedOrientation(req)`.

**Bug B**
- `APP/java/org/libsdl/app/SDLSurface.java:117-149` — `surfaceChanged`,
  `:148-149` `nativeSetScreenResolution` + `onNativeResize` en vertical.
- `SDK/thirdparty/sdl3/src/video/android/SDL_androidvideo.c:202-205` —
  `Android_SurfaceWidth/Height = surfaceWidth/Height`.
- `SDK/thirdparty/sdl3/src/video/android/SDL_androidwindow.c:57-60` —
  `window->w/h = Android_SurfaceWidth/Height`.
- `SDK/src/ui/window_sdl.cpp:199-201` — `OnActualSizeUpdate` con el
  tamaño en píxeles.
- `SDK/src/ui/surface_android.cpp:22-31` — `GetSizeImpl` desde
  `ANativeWindow`.
- `SDK/src/ui/presenter.cpp:1390-1391` — tamaño guardado en
  `surface_width/height_in_paint_connection_`.
- `SDK/src/ui/vulkan/vulkan_presenter.cpp:769-772,873-876` — swapchain
  (re)creado con ese tamaño; `:1143-1148` extensión limitada por la
  superficie; `:1017-1018` framebuffers con esa extensión.
- `SDK/src/graphics/vulkan/command_processor.cpp:2419` —
  `RefreshGuestOutput` con el aspecto del modo de vídeo invitado.
- `SDK/src/ui/presenter.cpp:550-558` — `display_aspect_ratio_x/y`.
- `SDK/src/ui/presenter.cpp:84` — `present_allow_overscan_cutoff = false`
  (→ `present_safe_area = 100`); `:32-33` `present_letterbox = true`.
- `SDK/src/ui/presenter.cpp:857` — `GetGuestOutputPaintFlow`;
  `:906-907,944-977` — rama superficie-más-alta, banda 1080×607 en
  `y = 896` (1080×2400) / `y = 866` (1080×2340).
- `SDK/src/ui/vulkan/vulkan_presenter.cpp:1651-1655` — `host_rt` =
  extensión del swapchain.
- `SDK/src/ui/vulkan/vulkan_presenter.cpp:1954-1955,2019-2020` —
  rectángulo final en `(output_x, output_y)`.
- `SDK/src/ui/presenter.cpp:1008-1022` y
  `SDK/src/ui/vulkan/vulkan_presenter.cpp:1876-1899` — guardas top-left
  (no se disparan aquí).
- Cadena de recuperación al rotar: `SDLSurface.java:148-149` →
  `SDL_android.c:1046-1056` → `SDL_androidvideo.c:276-301` →
  `SDL_video.c: SDL_OnWindowResized / SDL_CheckWindowPixelSizeChanged` →
  `window_sdl.cpp:464-466` → `window.cpp:548-566` →
  `presenter.cpp:390-415,1346-1391`.

**Arreglo**
- `APP/cpp/android_main.cpp:508` — punto de inserción del hint, antes de
  `SDL_InitSubSystem(SDL_INIT_VIDEO)`; `:556` `--fullscreen=true`.
- `APP/java/com/rexauto/port/MainActivity.java` — punto de la
  sobrescritura de `setOrientationBis` (opción 2).

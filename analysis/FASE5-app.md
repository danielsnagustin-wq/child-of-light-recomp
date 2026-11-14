# Fase 5 — Worker APP (v1.4): sin importar nada, juego leído desde la APK

**Fecha:** 2026-11-13. **Base:** v1.3 (HEAD 205b41c). **Alcance de este worker:**
la app Java + JNI. **NO** se tocó el SDK, ni `android_main.cpp`, ni
`MainActivity.java` (otros workers), ni la investigación de voces.
**NO** se pusheó nada. **NO** se construyó ninguna APK (árbol de build
compartido; la construye el coordinador al final).

**Requisito del usuario (2026-11-13 15:02):** port 100 % oficial — instalar →
abrir → jugar, sin importar nada, nunca. Tamaño total < 2 GB (datos ~1,5 GB +
motor ~24 MB = objetivo ~1,6 GB). La variante full pasa a ser EL producto;
la normal queda solo como variante de desarrollo.

**Precedente:** ninguno. Verificado en `analysis/FASE5-PORTS-OTROS.md` §6:
ningún port encontrado lee el juego desde dentro de la APK; el patrón
universal es "APK pequeña + juego del usuario en almacenamiento". El < 2 GB
sin duplicar es trabajo nuevo. Lo que sí existe es la pieza clave: el SDK ya
trae `StfsContainerDevice` (monta un STFS como VFS sin extraer).

## 1. Qué se hizo

### 1.1 La pantalla de importar DESAPARECE en la variante full (Bug 4, resuelto por eliminación)

Antes (v1.3, verificado en capturas `52951_0_fddx.jpg` / `52952_1_x8iy.jpg`):
`SetupActivity.continueStart()` construía **siempre** la pantalla completa
(título + ayuda + 2 botones del picker) y **después** arrancaba la
auto-importación; `setBusy(true)` solo deshabilitaba los botones, que
seguían visibles sobre el progreso ("Copiando el juego incluido 556/1512 MB").

Ahora la auto-importación **no existe**: se borraron
`maybeAutoImportBundledGame()`, `importStfsFile()` y `hasBundledGameAsset()`
de `SetupActivity.java`, y la cadena `status_bundled_copy` de `strings.xml`.
El flujo full es:

```
PortApp.onCreate → StfsAsset.ensureOpen()          (fd del asset → nativo)
MainActivity → hasValidGameRoot()? no
  → SetupActivity.continueStart()
      → FULL + StfsAsset.verifyUsable() → escribe "asset:game.stfs" en
        game_root.txt → launchGame() → MainActivity → juego
      → sin UI, sin picker, sin progreso, sin copia, sin extracción
```

- `tools/rexauto/android/app/src/main/java/com/rexauto/port/SetupActivity.java`
  - Cabecera reescrita (v1.4, sin auto-import).
  - `continueStart()` (~:83): si hay game_root válido → juego directo;
    si `BuildConfig.FULL` y `launchBundledAsset()` → juego directo, sin UI;
    en cualquier otro caso (normal/dev, o full con asset roto/ausente =
    build roto) → pantalla del picker manual, que sigue existiendo solo
    para la variante normal.
  - Nuevo `launchBundledAsset()` (~:100): verifica el asset
    (`StfsAsset.verifyUsable`, magia + Title ID), escribe el marcador
    `asset:game.stfs` en `game_root.txt` y lanza el juego. Devuelve false
    si el asset no sirve (el llamador cae al picker).
  - Borrado el bloque "bundled game" completo (~:240-330 viejos) y el
    import `android.content.res.AssetManager` (ya sin uso).
  - La ruta manual de importación (`importFile` / `importPackage` /
    `importTree` / `finish`) queda **intacta** para la variante normal.
- `AndroidManifest.xml`: `SetupActivity` pasa de `fullUser` a
  `sensorLandscape` (como `MainActivity` y el resto del port; las capturas
  del bug eran verticales porque la actividad seguía la rotación).
- `tools/run-release-build-full.sh`: solo comentarios — el script sigue
  inyectando `game.stfs` igual (entrada STORED), pero su cabecera ya no
  dice "el launcher lo auto-importa" sino "el juego lo lee desde dentro
  de la APK"; añadida la razón: STORED es obligatorio porque el VFS abre
  el asset por fd y eso falla si la entrada va comprimida.

### 1.2 Segunda entrada directa al juego (verificado, no cambiado)

`MainActivity.onCreate` (:19, solo lectura) rebota a `SetupActivity` si
`!GameFiles.hasValidGameRoot()`. Por eso el marcador tenía que ser
entendido por `hasValidGameRoot`: si no, cada arranque rebotaría en bucle
MainActivity → SetupActivity → MainActivity. Verificado por lectura de
`GameFiles.java` y `MainActivity.java`:

- `GameFiles.java`: nueva constante `ASSET_PREFIX = "asset:"`;
  `hasValidGameRoot()` ahora acepta `asset:<nombre>` cuando ese asset
  existe en la APK (vía `getAssets().list("")`); nuevos helpers
  `isAssetRoot()` y `assetName()`. La rama de carpeta
  (`default.xex` en disco) queda intacta para la variante normal.
- Consecuencia: 1ª entrada full escribe el marcador y juega; 2ª entrada
  ve el marcador válido y va directa al juego sin pasar por SetupActivity.

### 1.3 Plumbing app → nativo: el fd del asset por JNI (nuevo)

Tres archivos nuevos + 1 línea de CMake:

- `tools/rexauto/android/app/src/main/java/com/rexauto/port/StfsAsset.java`
  (nuevo, 155 líneas) — dueño del lado Java del contrato:
  - `ASSET_NAME = "game.stfs"`.
  - `verifyUsable(AssetManager, wantTitleId)`: preflight puro Java, sin
    JNI — `openFd` (falla si el asset está comprimido: esa es la prueba
    de STORED), longitud > 0, primeros 0x400 B, magia `CON `/`LIVE`/`PIRS`,
    Title ID en 0x360 big-endian contra `BuildConfig.TITLE_ID` (misma
    puerta que el viejo `finish()`; se salta si TITLE_ID es `00000000`).
    Los offsets son los mismos de `StfsExtractor.java:56-58` (`titleId`,
    `0x360`) y de `readHeader` (`0x400`).
  - `ensureOpen(AssetManager)`: idempotente por proceso. 1º comprueba
    que el asset existe con `list("")` (sin el asset no carga la librería
    nativa: en builds normales es un no-op barato); 2º `openFd` → fd +
    `getStartOffset()` + `getLength()`; 3º `System.loadLibrary("main")`;
    4º `setAssetFd(fd, offset, length)`; 5º cierra su copia Java del fd
    (el nativo ya hizo `dup()`). Nunca lanza: todo va a `catch (Throwable)`
    → false. `handedOver` es `volatile`.
  - `private static native boolean setAssetFd(int fd, long startOffset,
    long length)`.
- `tools/rexauto/android/app/src/main/java/com/rexauto/port/PortApp.java`
  (nuevo) — `Application` que llama a `StfsAsset.ensureOpen(getAssets())`
  en `onCreate()`, **antes de cualquier actividad y de cualquier código
  nativo**. Tiene que ser aquí y no en `SetupActivity`: un fd no sobrevive
  a la muerte del proceso; SetupActivity solo corre en el primer arranque,
  pero el nativo necesita el fd en **cada** arranque, antes de que
  `MainActivity` arranque el juego. En builds normales no hay asset y es
  un no-op.
- `tools/rexauto/android/app/src/main/cpp/stfs_asset.h` (nuevo) —
  contrato productor/consumidor: `rexport::StfsAssetFd(int* fd,
  std::int64_t* start_offset, std::int64_t* length)` devuelve el fd
  duplicado + offset + longitud, o false si no hay asset. Vida útil: el
  fd duplicado queda abierto todo el proceso; no cerrarlo nunca. La
  escritura ocurre una vez en el hilo principal antes de que SDL arranque
  el hilo nativo (happens-before por creación del hilo).
- `tools/rexauto/android/app/src/main/cpp/stfs_asset_jni.cpp` (nuevo) —
  `Java_com_rexauto_port_StfsAsset_setAssetFd(JNIEnv*, jclass, jint fd,
  jlong start_offset, jlong length) -> jboolean`: valida (fd ≥ 0, offset ≥
  0, length > 0), `dup(fd)` inmediato, guarda el triple en globales
  estáticos, logea por logcat. Si ya se entregó una vez, devuelve true
  sin más. Si `dup` falla, false.
- `tools/rexauto/android/app/src/main/cpp/CMakeLists.txt` (:109): añadido
  `stfs_asset_jni.cpp` al `add_library(main SHARED ...)`.
- `AndroidManifest.xml`: `<application android:name=".PortApp" ...>`.

## 2. Interfaz exacta que se entrega al lado nativo (para el worker VFS / android_main)

### 2.1 Contrato de archivos (Java → nativo, vía disco)

| Dato | Dónde | Formato |
|---|---|---|
| Marcador de raíz | `game_root.txt`, 1ª línea (mismo fichero y sitio que antes: `Android/data/<pkg>/files/game_root.txt`) | `asset:game.stfs` (literal; prefijo `asset:` definido en `GameFiles.ASSET_PREFIX`) |
| fd + offset + longitud | En memoria del proceso, no en disco | Ver §2.2 |

El marcador lo escribe `SetupActivity.launchBundledAsset()` en el primer
arranque; lo leen `GameFiles` (Java) y `android_main.cpp` (ya lee
`game_root.txt`, línea 532 — ese código es del otro worker).

### 2.2 Contrato JNI (Java → nativo, en caliente)

```
Java:  com.rexauto.port.StfsAsset.setAssetFd(int fd, long startOffset, long length)
         -> boolean
JNI:   Java_com_rexauto_port_StfsAsset_setAssetFd(JNIEnv*, jclass,
         jint fd, jlong start_offset, jlong length) -> jboolean
         (stfs_asset_jni.cpp, en libmain.so)
```

- **Qué recibe el nativo:** `fd` = descriptor del **fichero APK completo**
  (no del asset aislado); `startOffset` = byte donde empieza la entrada
  STORED `assets/game.stfs` dentro del APK; `length` = tamaño del asset
  en bytes (1.585.451.008 B en la build actual). Los tres valores salen
  de `AssetManager.openFd()` / `AssetFileDescriptor` (`getFd()`,
  `getStartOffset()`, `getLength()`).
- **Qué devuelve:** `true` si el triple quedó guardado y disponible vía
  `rexport::StfsAssetFd()`; `false` si los argumentos son inválidos o
  `dup(fd)` falló.
- **Semántica que debe respetar el consumidor:** cada lectura es sobre el
  fd del APK en `[startOffset, startOffset + length)` — **hay que sumar
  `startOffset` a todo offset relativo al contenedor**; nunca leer fuera
  de ese rango; el fd duplicado vive todo el proceso (no cerrarlo; el
  original de Java ya se cerró tras el `dup`).
- **Cuándo está disponible:** desde `PortApp.onCreate()`, antes de que
  arranque cualquier actividad y antes de `android_main`. En builds
  normales `StfsAssetFd()` devuelve false y todo sigue como antes.

### 2.3 Por qué JNI con fd y no AAssetManager en nativo (decisión verificada)

El nativo **no puede** obtener un `AAssetManager` por sí solo: tiene que
recibir el objeto `AssetManager` Java vía JNI (`AAssetManager_fromJava`),
y SDL **no** pasa uno al nativo (verificado: cero menciones a
`AssetManager` en `app/src/main/java/org/libsdl/app/*.java`; la API SDL
solo expone `SDL_GetAndroidActivity`). Abrir el fd en Java y pasar tres
números por JNI es el camino con menos piezas móviles: el VFS solo
necesita `(fd, offset, length)` y no hay que gestionar referencias JNI
globales al `AssetManager` en C++. Precedente documentado (Android
Developers, NDK `asset`): `AAsset_openFileDescriptor64` devuelve fd < 0
si el acceso directo no es posible (p. ej. asset comprimido); para
ficheros grandes usar la variante 64-bit. Nuestro `openFd` Java es el
equivalente exacto en el lado Java.

## 3. Lo que falta del lado nativo (NO es de este worker — checklist para VFS / android_main)

Verificado contra el SDK local (`tools/rexauto/android/sdk/rexglue-sdk`).
Todo lo de abajo es trabajo del worker VFS / android_main; se lista aquí
para que el coordinador lo entregue con la interfaz exacta (§2).

### 3.1 Montar el STFS en `Runtime::SetupVfs` (un solo sitio)

- Hoy (`src/system/runtime.cpp:295-326`): `SetupVfs()` exige que
  `game_data_root` exista como directorio y monta un `HostPathDevice` en
  `\\Device\\Harddisk0\\Partition1` con enlaces `game:` y `d:`.
- Cambio: cuando `rexport::StfsAssetFd()` devuelve el triple, montar un
  `StfsContainerDevice` **en ese mismo punto de montaje y con los mismos
  enlaces**, en vez del `HostPathDevice`. El dispositivo ya existe y ya se
  usa: `ContentManager::InstallContent`
  (`src/system/xam/content_manager.cpp:578`) lo construye con
  `(mount_path, package_path)` y lo inicializa — pero luego **copia** los
  archivos fuera; la copia es el paso que sobra, no el montaje.
- **Punto crítico — `ReXApp::ConstructRuntime` (`src/ui/rex_app.cpp:204-216`):**
  rechaza `--game_data_root` si está vacío o si **no es un directorio**.
  `android_main.cpp:558-559` hoy pasa `--game_data_root=<línea de
  game_root.txt>` tal cual. Con el marcador `asset:game.stfs` eso sería
  rechazado antes de llegar a `SetupVfs`. Recomendación (no implementada
  aquí, decide el worker android_main): que `android_main` detecte el
  prefijo `asset:` y **no** pase el marcador como `--game_data_root`;
  pase como raíz el directorio real de archivos externos (existe, es
  directorio — `ConstructRuntime` y las rutas de metadata
  `runtime.cpp:73-75` siguen funcionando sin cambios) y el montaje STFS
  se decida en `SetupVfs` por la presencia del triple fd, no por el
  valor del cvar. Alternativa: relajar el `is_directory` para el modo
  asset — más invasiva, no recomendada.

### 3.2 El dispositivo STFS necesita modo fd + offset (trabajo del worker VFS)

Hoy `StfsContainerDevice` toma un `std::filesystem::path` y lo abre con
`OpenFile(path, "rb")` (`stfs_container_device.cpp:106`). **Todas** sus
lecturas son `rex::filesystem::Seek(file, <offset relativo al contenedor>,
SEEK_SET)` + `fread` sobre los `FILE*` del mapa `files_` — puntos exactos
verificados:

| Uso | Sitios (`stfs_container_device.cpp`, salvo nota) |
|---|---|
| Cabecera STFS | `:194-203` (`Seek` END/Tell para tamaño, `Seek` SET, `fread` cabecera) |
| Sondas de magia SVOD | `:241, :257, :272, :287, :319, :329` |
| Entradas de directorio | `:363, :556-558` |
| Tablas de hash | `:740, :756, :773` |
| Datos de fichero | `stfs_container_file.cpp:54-55` (`Seek(file, record.offset + read_offset, SEEK_SET)` + `fread`) |
| Tamaño total (SVOD multifichero) | `:162-163` (`Seek` END + `Tell`) |

Trabajo acotado: añadir `base_offset_` (+ longitud) al dispositivo y
sumarlo en cada `Seek`, y que `files_total_size_` sea la longitud del
asset (no el tamaño del APK: un `Tell` sobre el fd del APK devolvería el
tamaño del APK). Opción equivalente: un constructor que acepte
`(fd, offset, length)` y haga `fdopen(dup(fd), "rb")` — el `dup` ya lo
hace el lado app; el dispositivo no debe cerrar el fd original. Nuestro
paquete es STFS de un solo fichero (`data_file_count = 0`, verificado
abajo), así que la rama SVOD multifichero no aplica. `ReadPackageHeader`
(`:51`, con `MappedMemory::Open` en `:791`) no tiene llamadores fuera del
fichero del dispositivo: no está en el camino del montaje.

### 3.3 Escrituras a `d:\` — verificado: el montaje STFS es seguro por construcción

- Hoy la raíz ya es **de solo lectura**: `allow_game_relative_writes`
  es `false` por defecto (`virtual_file_system.cpp:18`) y `SetupVfs`
  monta el `HostPathDevice` con `read_only = true` (`runtime.cpp:309`).
  El juego **ya se juega así** en el teléfono (v1.3).
- `StfsContainerFile::WriteSync` y `SetLength` devuelven
  `X_STATUS_ACCESS_DENIED` por construcción
  (`stfs_container_file.h:30-35`); `StfsContainerDevice::is_read_only()`
  es true para nuestro paquete (ver §4.3). Una escritura del juego a
  `game:`/`d:` falla igual que hoy: mismo modo de fallo, mismo
  comportamiento observable. Los guardados van a `user_data_root`
  (separado, `external/data`), no a la raíz del juego.

### 3.4 Resultado esperado

Total instalado = APK sola (~1,6–1,7 GB: motor ~24 MB + STFS 1,5 GB +
arte/recursos). Datos de la app: solo guardados, ajustes y logs (MB).
Desaparecen la copia temporal de 1,5 GB durante la instalación y la
extracción de 1,58 GB. La primera entrada ya no copia nada: el juego
arranca tras montar el STFS (parseo de cabecera + tabla de ficheros).

## 4. Verificaciones realizadas (todo contra el código real, nada de memoria)

1. **Java — `javac` real (JDK 17 local, `~/workspace/tools/jdk-17`):**
   todos los `.java` de la app (`com.rexauto.port` + `org.libsdl.app`)
   con `android-35/android.jar` y stubs de `BuildConfig`/`R` generados
   por grep — **exit 0**, sin errores (solo notas de deprecación
   preexistentes). Cubre `StfsAsset.java`, `PortApp.java`, `GameFiles.java`
   y `SetupActivity.java` editados.
2. **C++ — `clang++` del NDK 27.2:** `stfs_asset_jni.cpp`
   `-fsyntax-only -std=c++23` — **exit 0**; compilado a `.o` y
   verificado con `llvm-nm`: existen `Java_com_rexauto_port_StfsAsset_setAssetFd`
   y `rexport::StfsAssetFd`. El nombre JNI coincide carácter a carácter
   con el método declarado en Java
   (`private static native boolean setAssetFd(int, long, long)`).
3. **STFS real del usuario (`private/ChildOfLight-XBLA`, solo cabecera
   0x400 B leída):** magia `LIVE`, `header_size` 44302, Title ID
   `[redactado]` (= `BuildConfig.TITLE_ID`), `volume_type` 0 (STFS),
   `data_file_count` 0 (un solo fichero), flag `read_only_format = true`
   → `is_read_only()` del dispositivo será true. Tamaño 1.585.451.008 B
   (= el asset de FASE4). Esto valida `verifyUsable()` (magia + Title ID
   en 0x360) y el §3.3.
4. **Búsqueda web:** `AssetManager.openFd` / `AAsset_openFileDescriptor64`
   (Android Developers): fd < 0 si el asset está comprimido; usar la
   variante 64-bit para ficheros grandes; el `AAssetManager` nativo debe
   venir de Java vía JNI; `MediaPlayer.setDataSource(fd, offset, length)`
   es el precedente canónico del triple (fd, offset, longitud).
5. **Investigación de otros ports aplicada** (`analysis/FASE5-PORTS-OTROS.md`
   §6.3 + P0 #10): mecanismo confirmado contra el SDK local punto por
   punto (montaje en `SetupVfs`, `InstallContent:578`, `openFd` sobre
   STORED). Sin precedente de lectura desde la APK en ningún port: es
   trabajo nuevo, no receta copiable.
6. **Auditoría OpenCode (`freellmapi/auto`):** el servidor local
   freellmapi (:3001) estaba caído (SIGTERM de una limpieza de la VM);
   se restauró con `node dist/index.js` en `~/workspace/tools/freellmapi/server`
   y la auditoría corrió. Veredicto: OK en JNI, `PortApp`, `stfs_asset.h`,
   `CMakeLists`, manifest. Sus 4 supuestos bugs se evaluaron uno por uno
   contra el código y **3 son falsos**: (a) "carga `main` antes de
   comprobar el asset" — falso: `ensureOpen` comprueba `list("")` antes
   de `loadLibrary`, y la carga va en `try/catch (Throwable)` → false,
   no crash; (b) "`list("")` puede lanzar `SecurityException`" — falso
   como bug: ya va en `catch (Exception)` y el false es la dirección
   segura (el router cae al picker, que hace la comprobación fuerte);
   (c) "falta verificar que el asset contiene un XEX válido" — cierto
   como observación, pero su arreglo propuesto (comprobar
   `hasValidGameRoot` tras escribir el marcador) **no verificaría el
   XEX** (solo comprueba que el asset existe). Queda como riesgo residual
   (§5.2). Gemini (`gemini-3.5-flash-lite` vía OpenCode) no auditó:
   error de servidor `err_ff96cbb3`; no se reintentó tras restaurar
   freellmapi porque la auditoría OpenCode + las verificaciones 1–5 ya
   cubren el cambio. Codex no se usó (fuera hasta 2026-12-10).
7. **Sin referencias restantes** a lo borrado (`maybeAutoImportBundledGame`,
   `importStfsFile`, `hasBundledGameAsset`, `BUNDLED_GAME_ASSET`,
   `status_bundled_copy`) en `tools/` (solo menciones históricas en
   `analysis/FASE3.md` / `FASE4*.md`, que documentan v1.2/v1.3 y no se
   reescriben).
8. **Flujo de arranque re-verificado por lectura:** `MainActivity:19`
   (rebote a SetupActivity), `SetupActivity:83` (router),
   `GameFiles:47-62` (marcador), `android_main.cpp:532,558-559`
   (lectura de `game_root.txt` — del otro worker).

## 5. Tamaños, riesgos y bloqueadores

### 5.1 Tamaño (captura `52956_2_l2js.jpg`, v1.3-full)

| | v1.3-full (medido) | v1.4-full (objetivo, cuando el montaje nativo esté) |
|---|---|---|
| Aplicación (APK) | 1,68 GB | ~1,68 GB (igual: motor + STFS dentro) |
| Datos | 1,58 GB (juego extraído) | Solo guardados/ajustes/logs (MB) |
| **Total** | **3,26 GB** | **~1,6–1,7 GB** |
| Variante normal | 24 MB APK + importar el juego una vez (~1,6 GB) | Igual (variante de desarrollo, sin cambios) |

La parte app de v1.4 está hecha; el ahorro total solo se materializa
cuando el worker VFS entregue el montaje (§3). **Bloqueador exacto:** sin
el montaje nativo, una build full v1.4 escribe el marcador y lanza el
juego, pero el nativo no tiene de dónde leerlo (ver §3.1: además
`ConstructRuntime` rechazaría el marcador como `--game_data_root`). Es
decir: mi lado está completo y verificado en compilación, pero la
funcionalidad **no existe** hasta que el lado nativo aterrice. Si algo de
esto no se puede hacer en v1.4, el bloqueador sería ese — no hay
bloqueador del lado app: `openFd` sobre STORED funciona (probado por la
v1.3, que copió 1.512 MB desde ese mismo asset con ese mismo fd) y la
interfaz JNI está implementada y comprobada en objeto.

### 5.2 Riesgos residuales (aceptados, con su escenario exacto)

1. **El montaje STFS es trabajo nuevo sin precedente** (ningún port lo
   hace). Coste CPU de las lecturas sin medir: cada lectura atraviesa la
   tabla de hashes/bloques del STFS; el `.ipk` (1,5 GB, acceso aleatorio
   por VFS) debe comportarse igual montado que extraído. Medir en el
   teléfono: tiempo de "Cargando..." y carga de niveles vs v1.3.
2. **`verifyUsable` no confirma que haya un `default.xex` dentro del
   STFS** (solo magia + Title ID). Confirmarlo exigiría parsear la tabla
   de ficheros STFS sobre el asset, que necesita un `FileChannel` con
   offset — el diseño aplazado del §6, no implementado a propósito (sin
   compilación local de APK para probarlo). Escenario: solo un build
   roto (STFS con Title ID correcto pero sin XEX); la recuperación en
   ese caso es reinstalar una build full correcta. El fichero que se
   inyecta es byte a byte el que se jugó en v1.3.
3. **La primera entrada full depende del montaje nativo** (§5.1). Si el
   usuario instala una v1.4-full antes de que el VFS esté, el juego no
   arranca. Coordinar el orden: no entregar full v1.4 hasta que el
   montaje esté en la build.

### 5.3 Lo que NO se hizo (a propósito)

- **Extraer directamente desde el asset sin copiar a tmp** (diseño
  Java-only, sin nativo): quitaría la copia temporal de 1,5 GB pero NO
  el tamaño final (el juego extraído seguiría siendo 1,58 GB). Queda
  obsoleto con el montaje: no se implementó.
- **OBB** (expansion file): sin precedente en los ports, añade otro
  fichero a instalar; la vía del asset dentro de la APK es más directa
  para un port oficial (§6.3 punto 6 de FASE5-PORTS-OTROS).
- **Copiar solo un índice** o similar: no aplica — el STFS ya es su
  propio índice (tabla de ficheros + hashes); el montaje lo lee en
  su sitio.

## 6. Diseño aplazado: extraer desde el asset sin tmp (solo si el montaje no llegara)

Si el montaje STFS (§3) resultara inviable en v1.4, el plan B Java-only
sería extraer directamente del asset sin el fichero temporal de 1,5 GB:
`AssetFileDescriptor` → `ParcelFileDescriptor` → `FileChannel`, envuelto
en un `FileChannel` delegador que sume `startOffset` a las lecturas
posicionales y reporte `size() = length` (el `Stfs` Java solo usa
`size()` y `read(ByteBuffer, long)`). Ahorra tiempo de instalación y
pico de disco, pero **no** el total final (3,26 GB). No implementado:
sin APK compilable en local para probarlo y con el montaje como vía
principal, no se escribe código a ciegas en el camino de primer arranque.

## 7. Qué debe probar el usuario (cuando el coordinador entregue la v1.4-full completa, con el montaje nativo)

1. Instalar la v1.4-full desde cero (desinstalar antes la v1.3-full):
   al abrir, **entra directo al juego** — sin pantalla de importar, sin
   picker, sin "Copiando el juego incluido". Si aparece el picker en una
   full, es un fallo (asset ausente o con Title ID incorrecto).
2. Ajustes → Almacenamiento: **total ~1,6–1,7 GB** (Aplicación ~1,68 GB;
   Datos solo unos MB). Si Datos marca ~1,58 GB, el montaje no está
   activo y se está extrayendo.
3. Cerrar y volver a abrir: entra directo al juego, sin pantallas.
4. Jugar el inicio y medir el "Cargando..." contra v1.3 (las lecturas
   STFS pueden ser más lentas: si la carga se dispara, es el punto a
   medir del §5.2.1).
5. Variante normal (solo desarrollo): sigue mostrando el picker una vez
   e importando como antes.

## 8. Archivos tocados

| Archivo | Cambio |
|---|---|
| `tools/rexauto/android/app/src/main/java/com/rexauto/port/StfsAsset.java` | **Nuevo** (155 líneas): `verifyUsable`, `ensureOpen`, JNI `setAssetFd` |
| `tools/rexauto/android/app/src/main/java/com/rexauto/port/PortApp.java` | **Nuevo**: `Application.onCreate` → `ensureOpen` en cada arranque |
| `tools/rexauto/android/app/src/main/java/com/rexauto/port/GameFiles.java` | `ASSET_PREFIX`, rama `asset:` en `hasValidGameRoot`, `isAssetRoot`, `assetName` |
| `tools/rexauto/android/app/src/main/java/com/rexauto/port/SetupActivity.java` | Borrada la auto-importación; `continueStart` + `launchBundledAsset`; sin UI en full |
| `tools/rexauto/android/app/src/main/cpp/stfs_asset.h` | **Nuevo**: `rexport::StfsAssetFd()` |
| `tools/rexauto/android/app/src/main/cpp/stfs_asset_jni.cpp` | **Nuevo**: JNI `setAssetFd` + `dup` + triple |
| `tools/rexauto/android/app/src/main/cpp/CMakeLists.txt` | `stfs_asset_jni.cpp` en `libmain` |
| `tools/rexauto/android/app/src/main/AndroidManifest.xml` | `android:name=".PortApp"`; `SetupActivity` → `sensorLandscape` |
| `tools/rexauto/android/app/src/main/res/values/strings.xml` | Borrado `status_bundled_copy` (muerto) |
| `tools/run-release-build-full.sh` | Solo comentarios (STORED obligatorio; sin auto-import) |
| `README.md` | Sección de variantes y tamaño (v1.4) |
| `analysis/FASE5-app.md` | Este informe |

**No tocado:** `MainActivity.java`, `android_main.cpp`, nada de
`tools/rexauto/android/sdk/`, nada de `tools/rexauto/android/app/build/`,
nada de voces. Sin pushes, sin APKs.

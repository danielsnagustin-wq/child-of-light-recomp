# FASE 5.1 — v1.4.1: el crasheo al entrar (SuperNotCalledException)

Fecha: 2026-11-13. Versión: **1.4.1 / versionCode 5** (la v1.4 era vC 4).
Alcance: **solo la variante FULL** (decisión del usuario 2026-11-13 16:51: la
normal queda como dev, sin trabajo). Un solo cambio de código: una línea en
`MainActivity.java`. Nada del motor, nada del VFS, nada de texturas cambia
en esta ronda.

## §0. Resumen

La v1.4-full votaba al usuario al entrar, siempre, en cada toque al icono.
No era el fd del STFS, no era el montaje, no era la GPU: era el ciclo de
vida de Android. `MainActivity.onCreate()` hacía `return` sin llamar a
`super.onCreate()` cuando no había juego instalado, y Android mataba el
proceso con `SuperNotCalledException`. El arreglo es mover
`super.onCreate(savedInstanceState)` a la primera línea del `onCreate`.

## §1. Evidencia del log (log-v14.txt, sacado por el usuario con aShell)

El log lo dice 9 veces en 12 segundos, una por cada toque al icono:

```
11-13 16:49:45.990 27295 27295 E AndroidRuntime: android.util.SuperNotCalledException:
    Activity {com.rexauto.port.childoflight/com.rexauto.port.MainActivity}
    did not call through to super.onCreate()
```

PIDs que crashearon igual: 27295, 27402, 27536, 27601, 27658, 27704, 27766,
27802, 27838 (16:49:45–16:49:57). Cada intento: `Start proc` → `LaunchActivityItem`
de MainActivity → la excepción → `Process ... has died` → `Force finishing
activity SetupActivity`. SetupActivity nunca llegó a ejecutar su router: el
proceso moría dentro del `onCreate` de MainActivity, antes de que la
actividad siguiente corriera.

El mismo error ya estaba en la v1.3: el log trae una décima ocurrencia,
`11-13 14:46:42.384 PID 20494`, primer arranque de la v1.3-full (14:46 era
su prueba). Ver §3.

## §2. Causa raíz en el código

`tools/rexauto/android/app/src/main/java/com/rexauto/port/MainActivity.java`,
`onCreate()` (antes del arreglo):

```java
protected void onCreate(Bundle savedInstanceState) {
    if (!GameFiles.hasValidGameRoot(this)) {
        startActivity(new Intent(this, SetupActivity.class));
        finish();
        return;   // <-- sale SIN llamar a super.onCreate()
    }
    super.onCreate(savedInstanceState);
    ...
}
```

En instalación limpia `hasValidGameRoot()` es falso (todavía no existe
`game_root.txt`: lo escribe SetupActivity). El `return` temprano sale sin
llamar al super y Android exige `super.onCreate()` en todo `onCreate()`:
si no se llama, tira `SuperNotCalledException` y mata el proceso entero.
SetupActivity muere con él, el marcador `asset:game.stfs` nunca se escribe
y cada relanzamiento vuelve a caer en el mismo camino: bucle de crasheo
permanente, sin UI y sin error visible.

`SetupActivity.java` SÍ lo hace bien: su `onCreate` llama a
`super.onCreate(savedInstanceState)` primero y luego rutea. Solo hay 2
`onCreate` en toda la app (MainActivity y SetupActivity; SDLActivity es
de terceros). No hay ningún otro early-return antes de super.

## §3. Por qué la v1.3 se recuperó y la v1.4 no

El bug nació en la Fase 4 (v1.3), cuando MainActivity se volvió la
actividad lanzadora con este ruteo. La v1.3 también crasheó en su primer
arranque (14:46:42, PID 20494, misma excepción). Pero en v1.3
SetupActivity era una pantalla de importar a pantalla completa que
quedaba en la pila de actividades: tras el crasheo, al relanzar, el
sistema restauró SetupActivity encima, el usuario importó, se escribió
el marcador y el juego arrancó.

En v1.4 SetupActivity es un router sin UI: el sistema la termina a la
fuerza cuando el proceso muere (`Force finishing activity SetupActivity`
en el log) y no queda nada que restaurar. Por eso el bucle es permanente
en v1.4 y no lo era en v1.3. La hipótesis anterior (fd que no llega al
nativo / montaje STFS fallido, revisión del 16:51 sin log) queda
REFUTADA por el log: el proceso muere antes de que el fd importe.

## §4. El arreglo

```java
protected void onCreate(Bundle savedInstanceState) {
    // v1.4.1: super.onCreate() SIEMPRE primero. Antes el return temprano
    // (sin game_root.txt en instalacion limpia) salia sin llamar al
    // super y Android mataba la app con SuperNotCalledException en cada
    // toque al icono (log-v14.txt, 9 veces). SetupActivity escribe el
    // marcador asset:game.stfs y relanza; en la segunda entrada el root
    // ya es valido y se entra directo al juego.
    super.onCreate(savedInstanceState);
    if (!GameFiles.hasValidGameRoot(this)) {
        startActivity(new Intent(this, SetupActivity.class));
        finish();
        return;
    }
    applyOrientation();
    ...
}
```

Llamar a `super.onCreate()` (SDLActivity) antes de rutear es seguro: solo
inicializa SDL; el juego no arranca hasta que la superficie está lista y
la actividad se termina antes. El flujo en primer arranque queda:
MainActivity (super, rutea) → SetupActivity (escribe el marcador,
lanza MainActivity) → MainActivity (root ya válido, entra al juego).

## §5. Verificación — COMPLETA (2026-11-13 17:52)

- Build: `tools/run-release-build-full-v141.sh`, log
  `analysis/fase5.1-build-full-1.log`: Gradle `BUILD SUCCESSFUL in 1m 50s`
  (incremental: solo cambió MainActivity.java), STFS inyectado
  (`[full] entrada game.stfs unica OK`), zipalign OK, apksigner verify OK.
- APK v1.4.1-full: `private/ChildOfLight-recomp-v1.4.1-full.apk`,
  **1.610.028.992 B**, SHA-256
  `30d0e90216b6b78880cdfc0bc48d3dee59b2358427c69d1e432afa4985a2f222`,
  MD5 `4a6da2f85d24393a95e4e784be92a622`. apksigner verify OK, 1 firmante,
  keystore corazón (cert SHA-256 `3768a83d…`). aapt2 badging: package
  `com.rexauto.port.childoflight`, versionCode **5**, versionName
  **1.4.1-full**, label "el título", launchable-activity MainActivity.
- `assets/game.stfs`: **STORED** (no comprimido), 1.585.451.008 B, 29
  entradas en la APK. MD5 interno del STFS (streaming, sin extraer):
  `ad88061a06fba28e3434945f37cb3d57` **EXACTO**. `libmain.so` 55.284.352 B.
- `keyAlias` revertido a `rexauto` en `build.gradle.kts` (trap del script).
- La normal v1.4.1 no se construye en esta ronda (variante dev, sin trabajo).

## §6. Push + Drive — COMPLETO (2026-11-13 18:07)

- Push por navegador: 2 commits ya en main (HEAD f8cd94a, 112 commits):
  `ab61e1d5` MainActivity.java (el arreglo) y `f8cd94a`
  run-release-build-v141.sh. Los 3 restantes
  (run-release-build-full-v141.sh, README discreto, este informe) van en
  el lote discreto junto con los 21 informes scrubbed (tarea en curso).
- Drive privado (nunca compartir): carpeta "el port v1.4.1-full", ID
  `1a1AWrsZSLP0YaiW26Ho9YJZRIc3oGzbc`, **18 archivos, TODOS privados**
  (verificado por Aurora 18:07): 16 partes (part00–14 de 104.857.600 B,
  part15 de 37.164.992 B) + MD5SUMS + README. MD5 de la APK:
  `4a6da2f85d24393a95e4e784be92a622`.

## §7. Honestidad: qué NO prueba esta ronda

Un build en verde no prueba que el crasheo se fue: solo la prueba en el
teléfono del usuario lo prueba (abrir la v1.4.1-full instalada limpia:
debe entrar directo al juego, sin votar, y Datos debe quedar en MB, no
en 1,58 GB). Todo lo demás de la v1.4 sigue igual: la intro se seguirá
saltando (sin decodificador WMV3/WMA), el título puede seguir cortado
(el recorte está dentro de la imagen del guest) y la banda blanca sigue
sin causa señalada. Esos son trabajos aparte, no de esta ronda.

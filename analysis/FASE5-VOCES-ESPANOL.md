# FASE 5 — Voces en español de España para el port nativo del título

**Fecha:** 2026-11-13
**Estado:** Investigación completa. Veredicto + plan concreto. Sin modificar el árbol del port ni construir nada.
**Autor:** subagente de investigación (coordinación Aurora)

---

## 0. Veredicto

# FACTIBLE, con trabajo concreto y acotado

**El doblaje español SÍ existe y el camino para ponerlo en el port es real, pero NO es "activar algo que ya está en los datos de 360".** Hay que traer las voces del narrador desde la versión de Vita y sustituirlas dentro del banco de audio de 360. Eso es un trabajo de sustitución de archivos de audio, no de ingeniería inversa profunda.

**Resumen en una frase:** las cinemáticas ya llevan audio en español dentro de los datos de 360 (solo hay que elegir la pista correcta); el narrador del juego, en cambio, está en inglés en los datos de 360 y hay que traerlo en español desde Vita.

### Las tres cosas que cambian todo lo que se creía

| # | Hallazgo | Consecuencia |
|---|----------|--------------|
| 1 | **`sounds_eng.pck` está VACÍO (56 bytes, 0 bancos, 0 archivos).** Todo el audio —música, efectos Y voces— vive dentro de `sounds_sfx.pck`. | La afirmación de FASE4 ("solo sounds_eng.pck, voces solo en inglés") era una conclusión por el nombre del archivo. El interior dice otra cosa: no hay ningún banco de voces separado. |
| 2 | **Los `.wem` de 360 son Vorbis de Wwise (fmt `0xFFFF`), NO XMA.** Verificado con vgmstream en archivos reales. | **No hace falta ningún codificador XMA.** El objetivo de transcodificación es Vorbis, un códec abierto. Esto invalida la ruta XMA2Encode y abarata el trabajo enormemente. |
| 3 | **El narrador en los datos de 360 habla INGLÉS (probado, no inferido).** Transcribí un clip real del juego con reconocimiento de voz: inglés, probabilidad 0.99, texto narrativo coherente. | El doblaje español del narrador NO está en los datos de 360. Hay que traerlo de Vita. Las cinemáticas, en cambio, sí traen español (ver §6). |

---

## 1. Qué hay dentro de los bancos de audio de 360 (verificado en los archivos reales)

### 1.1 `sounds_eng.pck` — vacío

- Ruta: `private/extracted/sound/wwisebanks/x360/sounds_eng.pck`
- **56 bytes.** Cabecera AKPK v1 completa: `headerSize=48`, `version=1`, `langMapSize=16`, `bankTableSize=4`, `fileTableSize=4`, idioma `sfx`, **nBanks=0, nFiles=0**.
- Es un `.pck` válido y vacío (el stub que genera la editora para el banco de voces cuando el idioma no lleva contenido propio en esta build). No es un archivo corrupto ni truncado. **No contiene ni un solo `.wem`.**

### 1.2 `sounds_sfx.pck` — todo el audio del juego

- Ruta: `private/extracted/sound/wwisebanks/x360/sounds_sfx.pck`
- **412.957.574 bytes (~394 MiB).** AKPK v1, `headerSize=10608`.
- **46 bancos** (todos empiezan por `BKHD`, versión **88 = Wwise 2013.1**, total 51.743.693 B) + **482 archivos streamed** (todos empiezan por `RIFX`, total 361,2 MB), contiguos y cerrando exactamente en EOF.
- Además, los bancos contienen **1.393 `.wem` embebidos** en sus secciones `DATA` (50.940.969 B, verificados `RIFX` en `DATA+offset` de cada entrada `DIDX`).
- **Total real: 1.875 `.wem`**, todos dentro de `sounds_sfx.pck`.
- Tabla de bancos parseada y validada (entradas de 20 B: `id, lang, size, offset, ?`; los bancos son contiguos: el fin de uno es el inicio del siguiente). Tabla de archivos parseada y validada (entradas de 20 B: `id, lang, size, offset, ?`; primer archivo en `RIFX`, último archivo termina exactamente en EOF, offsets estrictamente crecientes).
- **Los 46 bancos tienen `dwLanguageID = 0` (SFX) y 0 tienen `bIsLanguageSpecific = 1`.** No hay ningún banco marcado por idioma.
- Totales HIRC en los 46 bancos (wwiser): **2.669 CAkSound, 2.087 CAkEvent, 1.812 CAkActionPlay, 843 CAkMusicTrack, 836 CAkMusicSegment, 530 CAkActorMixer, 72 CAkSwitchCntr, 213 CAkSwitchPackage, 0 CAkSwitchContainer, 0 CAkDialogueEvent.** Los bancos guardan solo IDs numéricos, sin nombres legibles.

### 1.3 Las voces no están en ningún `.ipk`

Escaneo completo de los 4 `.ipk` (171 MB + 627 MB + 2,5 MB + 6 MB): **0 ocurrencias de `AKPK`, `BKHD` o `RIFX`** (el contenido va comprimido; la cabecera es `50 EC 12 BA`). Los `.ipk` no contienen audio Wwise. Las voces de 360 viven embebidas/streamed dentro de `sounds_sfx.pck`, no en un `.ipk` ni en `sounds_eng.pck`.

### 1.4 El juego no tiene ningún camino de código para cargar voces en español

Cadenas literales en `default.pe` (el ejecutable del juego):

- `sound/wwisebanks/`, `%u.wem`, `English(US)`, `%s%s/guids.snd`, `sounds_sfx.pck`, `sounds_eng.pck`.
- **No existe `sounds_spa.pck`, ni `sounds_fra.pck`, ni ningún `sounds_%s.pck`.** Los dos nombres de `.pck` están fijos en el binario.
- Los enums `ITF_LANGUAGE_SPANISH` e `ITF_LANGUAGE_LATAM_SPANISH` existen en el binario, pero son para **textos/menús/subtítulos**, no para bancos de voz.
- El manifiesto de `bundle_x360.ipk` lista `wwise_eng_x360` → `sounds_eng.pck`, `wwise_sfx_x360` → `sounds_sfx.pck`, `wwise_guids_x360` → `guids.snd`, con variantes por plataforma (pc/ps3/ps4/wiiu/x360/xone). **Solo `eng`. Ningún otro idioma de voz.**

Conclusión: **aunque existiera un `sounds_spa.pck`, el juego 360 no lo cargaría.** La única vía es sustituir los `.wem` de voz dentro de `sounds_sfx.pck` (mismo ID, mismo formato), que es exactamente lo que el runtime ya sabe reproducir.

---

## 2. El códec real: Wwise Vorbis, NO XMA (corrección decisiva)

### 2.1 Prueba

Descargué **vgmstream r2168** (Linux) y lo corrí sobre `.wem` reales extraídos de `sounds_sfx.pck` (streamed y embebidos). Resultado en los 4 archivos de prueba y en los 6 clips grandes del banco de diálogo:

```
encoding: Custom Vorbis
metadata from: Audiokinetic Wwise RIFF header
sample rate: 48000 Hz
```

Ejemplos:
- `59358.wem` (streamed, 123.714 B): 48 kHz, 2 canales, 5,96 s, Vorbis, 165 kbps.
- `103922723.wem` (streamed, 482 B): 48 kHz, 1 canal, 0,5 s, Vorbis, 6 kbps.
- `228152946.wem` (embebido, banco 782826392, 741.738 B): 48 kHz, 2 canales, 31,07 s, Vorbis, 190 kbps.
- `719533722.wem` / `736244596.wem` (embebidos, 278 KB): 48 kHz, 1 canal, ~15 s, Vorbis, ~145 kbps.

La cabecera fmt es `0xFFFF` con chunk de 66 bytes — el marcador de **Vorbis de Wwise** según la tabla de códecs de bnnm (autor de vgmstream) y el script `wwise_pck_extractor.bms` (`0x0166/0x0401 = XMA`, `0xFFFF = Vorbis`). ffmpeg NO puede decodificarlos directamente ("Not yet implemented in FFmpeg, patches welcome") porque el Vorbis de Wwise guarda el codebook en el chunk `fmt` y los paquetes van sin cabeceras Ogg estándar; vgmstream sí los decodifica (reconstruye los codebooks).

### 2.2 Por qué importa: se acabó el problema del codificador XMA

- **No hace falta XMA2Encode ni ningún codificador de Microsoft.** El objetivo es Vorbis, y Vorbis se puede codificar con cualquier codificador libre (ffmpeg/libvorbis, oggenc).
- El decodificador que reproduce estos archivos en el port es **el propio Wwise estático dentro del juego** (el binario contiene `AK::BankManager`, `AK::EventManager`, `AK::IOThread`, `SoundAdapter_Wwise`). ReXGlue **no tiene código Wwise** (grep `wwise|AkSound|LoadBank|SoundEngine` en `sdk/src` + `sdk/include` = 0 resultados). ReXGlue solo aporta: registros XMA emulados + decodificación XMA por software vía FFmpeg + salida SDL3 (48 kHz, 6 canales, float). El único decodificador que su FFmpeg tiene activado en Android es XMAFRAMES (`CONFIG_XMAFRAMES_DECODER=1`, `CONFIG_VORBIS_DECODER=0`), pero **eso es irrelevante para estos archivos**: el Vorbis lo decodifica el Wwise del juego, no el FFmpeg de ReXGlue. La ruta XMA de ReXGlue queda ociosa con este juego.
- **Corrección a un informe previo:** la afirmación de que "todos los `.wem` son XMA (tag `0xFFFF`)" es incorrecta. El tag `0xFFFF` es Vorbis de Wwise. La confusión es comprensible (el tag no es el estándar XMA `0x0166`), pero vgmstream lo resuelve sin ambigüedad: `Custom Vorbis`.

### 2.3 Cómo se crea un `.wem` Vorbis de Wwise (para el plan)

No existe una herramienta open-source autónoma que codifique Ogg → `.wem` Vorbis de Wwise. Las que existen van en dirección contraria:

- **ww2ogg** (hcs64): `.wem` Vorbis de Wwise → Ogg Vorbis estándar (solo decodifica; reconstruye las cabeceras Ogg desde el chunk `fmt`).
- **wwiseutil** (Go): inspecciona, extrae y **reemplaza** `.wem` dentro de `.pck`/`.bnk`, pero el `.wem` de reemplazo tiene que existir ya en formato Wwise (no codifica).
- **Wwise Authoring (oficial, gratuito, ~2 GB)**: el camino estándar de los modders — se importa un `.wav`, se pone la conversión en Vorbis y Wwise genera el `.wem`. Es Windows-only y la versión importa (el juego usa Wwise 2013.1; un `.wem` generado con Wwise moderno probablemente se decodifica igual en el runtime de 2013 porque el formato Vorbis en `.wem` es estable, pero **hay que probarlo**, no darlo por hecho).

La alternativa sin Wwise es **escribir un pequeño reempaquetador que invierta la lógica de ww2ogg**: tomar un Vorbis estándar (codificado con ffmpeg/libvorbis), extraer el codebook/setup de sus cabeceras Ogg y escribirlo en el chunk `fmt` del `.wem` + los paquetes de audio en crudo. Es un trabajo definido (unos cientos de líneas, Python o C) y **validable por round-trip**: `.wem` 360 → ww2ogg → `.ogg` → reempaquetador → `.wem`, y comprobar que vgmstream decodifica el resultado idéntico al original. Es el camino recomendado porque corre en Linux y da control total sobre el layout exacto del chunk `fmt` de los archivos de 360.

---

## 3. El narrador en los datos de 360 habla INGLÉS (probado por escucha automática)

### 3.1 Método

1. Identifiqué el banco de diálogo: **782826392** (15.837.311 B, el más grande con diferencia; 673 clips embebidos en `DIDX`, mediana 8,3 KB, HIRC de 535.004 B con 1.739 CAkSound y 1.375 CAkEvent).
2. Extraje clips, los decodifiqué a WAV con vgmstream y los transcribí con **faster-whisper 1.2.1** (modelo `tiny`, CPU int8, detección automática de idioma).
3. Controles: 6 clips cortos (0,7–2,7 s) dieron texto vacío y probabilidad de idioma baja (0,22–0,38) — son efectos de sonido, no voz. 2 clips mono de ~15 s dieron vacío — tampoco son narración.

### 3.2 Resultado decisivo

Clip **media ID 228152946** (embebido en el banco 782826392, 741.738 B, 31,07 s, estéreo, 48 kHz, Vorbis 190 kbps):

- **Idioma detectado: inglés, probabilidad 0,99.**
- Transcripción (íntegra, coherente, narración de cuento en rima, inconfundible del juego):

> "The moon shone through her misty veil, the stars went joyfully, as a roar left the tower and made her journey to the sea. If the highest of the high was in this guy, the lowest of the low had to be beneath the white caped waves of the Lamarian Sea. The waters of the flood continued to rise. The Duke was at death's gate. Unburden knocks waited in the dark. Little time left to alter the hands of fate."

Es la narradora del título contando la historia en inglés. **El narrador del juego en los datos de 360 es inglés.**

### 3.3 Qué significa esto para la afirmación del usuario

El usuario tiene razón en que **el juego el título SÍ tiene doblaje completo en español de España**: Steam lista "Full Audio" (voces + interfaz + subtítulos) para Español de España y otros 7 idiomas, y la narración española existe como producto. **Pero en ESTA build de Xbox 360 (XBLA) que tenemos, el narrador que viene dentro es el inglés.** La build de 360 trae: voces del narrador en inglés + textos/subtítulos en español + audio de cinemáticas en español (ver §6). Es decir, el doblaje español existe, pero no está dentro de este dump de 360; está en las otras versiones (PC, Vita, etc.).

---

## 4. Qué banco es el de diálogo y qué IDs son voz (estado actual)

- **Banco 782826392** es el candidato más fuerte a banco de diálogo: 673 clips embebidos (372 B–741 KB, mediana 8,3 KB, total 15,3 MB) + el HIRC más grande (535 KB, 1.739 Sounds, 1.375 Events). Es una hipótesis reforzada por el hecho de que el clip de narración transcrito (ID 228152946) salió de este banco, pero **no todos sus 673 clips son voz** (varios muestreados son efectos).
- **Bloqueador real (parcialmente resuelto): no se sabe a priori qué ID es voz.** El HIRC solo tiene IDs numéricos, sin nombres. El mapa ID→nombre es `guids.snd` — **y `guids.snd` NO tiene nombres**: lo extraje de `bundle_x360.ipk` (77.175 B, 1.575 líneas) y es un mapa GUID (UUID) → ID numérico en texto plano (`8C2D247A-…\t0660593939`), sin ningún nombre legible (`grep VO_` = 0). Sirve para cruzar GUIDs con IDs, pero no dice qué sonido es voz.
- **Método que sí identifica las voces (probado):** decodificar + transcribir con STT. El clip de narración se identificó así. Para el inventario completo de líneas del narrador, el plan es un barrido STT sobre los clips candidatos (embebidos del banco 782826392 + streamed de duración 2–15 s): los que transcriban como narración inglesa son las líneas a sustituir. Es trabajo mecánico, no investigación.
- Rareza observada, sin explicación cerrada: 57 IDs de media aparecen **tanto** en `DIDX` de bancos (embebido) **como** en la tabla de archivos streamed, y hay 1.393 entradas embebidas para 1.338 IDs únicos (55 duplicados entre bancos). El parseo `DIDX` está verificado (entrada 0 del banco 71783646: `id=723477248 off=0 size=45973`, `RIFX` en `DATA+0`), así que el solape es real en los datos; qué significa en Wwise (media compartida/duplicada) no es verificable aquí. A la hora de sustituir, hay que reemplazar el ID en **todos** los sitios donde aparezca.

---

## 5. El archivo a tocar es `sounds_sfx.pck`, NO `sounds_eng.pck`

- Rellenar el `.pck` inglés vacío no serviría: los eventos que referencian las voces viven en los HIRC de los bancos de `sounds_sfx.pck`; un banco nuevo en `sounds_eng.pck` no sería referenciado por nada (y reutilizar un bankID existente duplicaría el banco).
- **Reglas de sustitución (verificadas contra el formato y el runtime):**
  1. **Mismo media ID.** El evento HIRC referencia el audio por ID numérico; la voz española tiene que ocupar el mismo ID que la inglesa.
  2. **Mismo formato: Vorbis de Wwise (RIFX, tag `0xFFFF`), 48 kHz, mismos canales que el original.** El runtime lo soporta (el decodificador Vorbis del Wwise del juego); cambiar el sample rate cambiaría lo que Wwise pide y es un cambio de comportamiento no probado. Lo seguro es igualar el original.
  3. **Reconstruir el contenedor, no parchear bytes.** Si el `.wem` es **streamed** (tabla de archivos del `.pck`): actualizar su `size` y **todos los offsets siguientes** de la tabla (contiguos hasta EOF). Si es **embebido** (sección `DATA` del banco): actualizar su entrada `DIDX` (offset/size relativos a `DATA`), el tamaño del banco en la tabla AKPK de bancos, y **todos los offsets de bancos y archivos siguientes**. Sin checksum que recalcular (ninguno observado en AKPK).
  4. **Duraciones:** no hay caché de duraciones en ReXGlue — el contexto XMA decodifica frames hasta agotar los buffers; los puntos de loop vienen del contexto XMA que Wwise rellena desde el `.wem`. Una voz más larga/corta que la original suena entera; el evento no sabe cuánto dura. Del lado Wwise no puedo verificarlo (código cerrado), pero nada en el formato (HIRC) almacena duraciones: los objetos Sound referencian media por ID.
  5. **Atajo posible (hipótesis, sin verificar):** el string `%u.wem` existe en `default.pe` — el juego puede cargar archivos `<id>.wem` sueltos. Si Wwise prioriza el `.wem` suelto sobre el del `.pck`, bastaría con soltar los `.wem` españoles con el ID correcto junto a los bancos, sin reconstruir el `.pck`. La prioridad no se puede establecer sin el SDK de Wwise (cerrado). Es una prueba barata que vale la pena intentar antes que la reconstrucción completa.

---

## 6. Las cinemáticas SÍ tienen audio en español dentro de los datos de 360

Este es el hallazgo que matiza la afirmación del usuario: **el doblaje español de las cinemáticas ya está en los datos de 360.**

- 11 archivos `.wmv` en `world/common/movie/`.
- **7 de ellos llevan 9 pistas de audio cada uno** (wmapro, 5.1, 44,1 kHz, todas de 74,999 s en la intro): índices 0–8 = **fre, jpn, ger, spa, ita, rus, por, spa, eng**. Hay **DOS pistas etiquetadas `spa`**.
- Los otros 4 `.wmv` tienen solo audio en inglés o ninguno (attract: solo vídeo; plan_logo, col_trial_outro, r02_cin253_cs_magnaup_bink: solo eng; r01_cin020_cs_altar_bink: solo vídeo).
- **Subtítulos:** 61 archivos `.srt` en `child_of_light_x360.ipk` (extraídos los de la intro). Hay **7 `.es.srt` y 7 `.mx.srt`** (uno por cada cinemática con audio español). El contenido del `.srt` no es texto plano: es timing + `loc=<id>` (las frases se resuelven en los archivos de localización). Los timings difieren ligeramente entre `.es.srt`, `.mx.srt` y `.en.srt` (ej. intro, entrada 1: es 02,249–04,790; mx 02,288–04,783; en 03,305–05,790), coherente con que cada idioma tiene su propia narración con su propia duración.
- **Textos:** 11 archivos de localización en `bundle_x360.ipk` (`localization_brazilian`, `localization_common`, `localization_english`, `localization_french`, `localization_german`, `localization_italian`, `localization_japanese`, `localization_latamspanish`, `localization_russian`, `localization_spanish` + `localizationconfig`). Extraídos y verificados `localization_spanish.isg.ckd` (265.593 B, texto real en español: "Dardo de madera", "Agua vital", "Vuelve a conectar el Mando Xbox 360", etc.) y `localization_english.isg.ckd` (256.334 B). **Hay `localization_spanish` (España) y `localization_latamspanish` (LatAm) como archivos separados.**

### 6.1 Las dos pistas `spa`: ¿cuál es España y cuál LatAm?

**Sin verificar.** Las dos pistas `spa` son casi seguro España y LatAm (coherente con que existen `.es.srt` + `.mx.srt` y `localization_spanish` + `localization_latamspanish`), pero **cuál es cuál no se puede saber sin escucharlas o sin transcribirlas y comparar el vocabulario con el texto de cada localización**. El juego elige la pista por el idioma de la consola (`user_language=5` + país `es=31` en v1.3); si la pista que suena es LatAm en vez de España, habría que cambiar el índice de pista. **Prueba práctica:** en el teléfono, con el juego en español, escuchar la intro y confirmar que es español de España; si es LatAm, se ajusta el índice.

### 6.2 Qué significa esto para el plan

- **Las cinemáticas no necesitan archivos de Vita.** Con `user_language=5` (ya implementado en v1.3) el juego debería elegir la pista española del `.wmv`. Falta la prueba en el teléfono.
- **El trabajo de Vita es solo para el narrador del juego** (las líneas de voz dentro de los `.wem`), no para todo el audio.

---

## 7. La versión de Vita: qué se sabe y qué NO se sabe

### 7.1 Lo que se sabe

- La versión de Vita (PCSB00598, la del launcher) **sí tiene las voces en español de España** (el usuario la jugó en español; Steam confirma doblaje completo en español de España para el juego como producto).
- El juego de Vita es "básicamente el mismo juego" (mismo contenido, mismos textos en español, misma narración; la diferencia es la plataforma y el rendimiento: Vita 544p/30 fps, 360 720p/60 fps).
- Los eventos/IDs de voz **no se pueden asumir iguales entre plataformas**: precedente negativo (Assassin's Creed III Remastered en Switch: al cambiar `sounds_eng.pck` de PC a Switch falló porque los IDs difieren entre plataformas). **Hay que comparar los IDs de Vita con los de 360, no asumirlos.**

### 7.2 Lo que NO se sabe (sin verificar, sin inventar)

- **El códec de los `.wem` de Vita.** Lo probable es **ATRAC9** (el códec nativo de Wwise en Vita; Wwise lo soporta en esa plataforma), pero **no está verificado para este juego** — no hay archivos de Vita en el workspace. La forma de verificarlo es un hexdump de un `.wem` de Vita real: tag de formato `0xFFFC` = ATRAC9. vgmstream (que ya tenemos) decodifica ATRAC9, así que el paso de decodificación está cubierto sea cual sea el códec.
- **La ruta exacta de los bancos dentro del dump de Vita.** No hay archivos de Vita para comprobarlo. Por el patrón de la editora (misma estructura de datos, carpeta por plataforma: `x360` en 360) y por la ruta de 360 (`sound/wwisebanks/x360/`), lo esperado es `sound/wwisebanks/vita/` (o `psvita`/`psp2`). **Es una inferencia, no un dato.** La petición al usuario debe ser lo bastante amplia para que no falle si la carpeta se llama distinto (ver §8).
- **Si los IDs de voz coinciden entre Vita y 360.** Se comprueba comparando: extraer los `.wem` de voz de Vita, transcribirlos o medir duraciones, y cruzar con los IDs de 360. Si no coinciden, hay que mapear por contenido (misma frase, misma duración aproximada) y sustituir por ID de 360.

---

## 8. Archivos exactos que hay que pedirle al usuario

**El usuario tiene el juego de Vita (lo juega en el launcher). Hay que pedirle los bancos de audio de Vita.**

### Petición principal (recomendada)

> **Toda la carpeta `sound/wwisebanks/` del dump de Vita (PCSB00598).**

- **Qué es:** la carpeta con todos los bancos Wwise de la versión Vita (los `.pck` de voz y de efectos + `guids.snd` si está).
- **Dónde buscarla (orientativo, no verificado):** dentro de los datos del juego de Vita, en una ruta del estilo `sound/wwisebanks/vita/` (la carpeta de plataforma se llamará `vita`, `psvita` o similar; en 360 es `x360`). En el teléfono, los datos del juego están donde el launcher/Vita3K guarda el juego importado (`ux0:app/PCSB00598/` o la carpeta de datos de Vita3K). Si no la encuentra por el nombre, que busque **cualquier archivo `.pck`** dentro de los datos del juego y envíe la carpeta que lo contiene.
- **Nombres esperados (inferidos, no verificados):** `sounds_spa.pck` (el banco de voces en español — el equivalente al `sounds_eng.pck` vacío de 360) y `sounds_sfx.pck` (efectos/música). Si la estructura es distinta (por ejemplo, todas las voces dentro de `sounds_sfx.pck`), da igual: con la carpeta entera se ve lo que hay.
- **Tamaño orientativo:** la carpeta completa probablemente ronde los ~400 MB (el `sounds_sfx.pck` de 360 pesa 394 MiB). El banco de voces en español por sí solo probablemente sea mucho menor (las líneas del narrador suman del orden de decenas de MB). **Si enviar la carpeta entera es demasiado, basta con el `.pck` de voces en español + `guids.snd` si lo hay.**
- **Cómo enviarlo:** igual que envió los DLCs (por el chat, en partes si hace falta). No hace falta el juego entero, solo esa carpeta.

### Si no puede localizar la carpeta

Que envíe **cualquier archivo `.pck` que encuentre dentro de los datos del juego de Vita** y se le dice si es el correcto. O que liste las carpetas dentro de `sound/` si existe.

### Lo que NO hace falta pedir

- **No hace falta el juego completo de Vita** (ya lo tiene el launcher) ni los vídeos (`.wmv` de cinemáticas) ni los textos (ya están en español en 360).
- **No hace falta ningún archivo de audio de PC/Steam.** La referencia es Vita porque es la versión que él jugó en español de España y la que tiene a mano.

---

## 9. Plan concreto (Fase 5, implementación)

### Fase 5.0 — Recepción y verificación de Vita (lo primero, barato y decisivo)

1. Recibir la carpeta `sound/wwisebanks/` de Vita.
2. Verificar el códec de sus `.wem` por hexdump (tag `0xFFFC` = ATRAC9; `0xFFFF` = Vorbis; otro = documentarlo). Si es ATRAC9, decodificar con vgmstream; si es Vorbis, directo.
3. Parsear el/los `.pck` de Vita con el mismo parser AKPK validado en 360 (tablas de bancos y archivos de 20 B). Inventariar bancos, IDs y tamaños.
4. **Comparar IDs:** cruzar los IDs de voz de Vita con los IDs de voz de 360. Si coinciden, el mapeo es directo. Si no, mapear por contenido (transcribir ambos lados con faster-whisper y emparejar por frase/duración).

### Fase 5.1 — Inventario completo de las líneas del narrador en 360

1. Barrido STT (faster-whisper, ya instalado y probado) sobre los clips candidatos: los 673 embebidos del banco 782826392 + los streamed de 2–15 s. Quedarse con los que transcriban como narración inglesa (texto coherente de cuento).
2. Producir la lista definitiva: media ID + banco + offset + tamaño + duración + texto transcrito (inglés) de cada línea del narrador.
3. Emparejar cada línea con su equivalente español de Vita (por ID si coincide, por transcripción/duración si no).

### Fase 5.2 — Transcodificación a Vorbis de Wwise

1. Decodificar cada `.wem` español de Vita a WAV (vgmstream; ATRAC9 o Vorbis según sea).
2. Re-codificar a Vorbis 48 kHz, mismos canales que el original de 360 (ffmpeg/libvorbis).
3. Reempaquetar a `.wem` Vorbis de Wwise con el reempaquetador (inverso de ww2ogg; a escribir, ~200–400 líneas) **o** con Wwise Authoring si se prefiere la vía oficial. Validar por round-trip contra un `.wem` original de 360 (decodificación idéntica con vgmstream).
4. Verificar que cada `.wem` español resultante tiene el mismo sample rate y canales que el inglés al que sustituye.

### Fase 5.3 — Sustitución dentro de `sounds_sfx.pck`

1. **Probar primero el atajo `%u.wem` suelto** (si el juego prioriza el `.wem` suelto, no hace falta reconstruir el `.pck`).
2. Si no, reconstruir `sounds_sfx.pck` en una copia: sustituir cada `.wem` de voz por su equivalente español (mismo ID), actualizando tamaños y todos los offsets siguientes (streamed y embebidos/DIDX + tamaños de banco en la tabla AKPK). Sin checksum que recalcular.
3. Validar la reconstrucción: parsear el `.pck` resultante con el mismo parser (tablas coherentes, último archivo en EOF) y decodificar con vgmstream varios `.wem` sustituidos.

### Fase 5.4 — Prueba en el teléfono

1. Instalar el port con el `sounds_sfx.pck` modificado (variante full, privada).
2. Jugar y confirmar: el narrador habla en español de España; las cinemáticas suenan en español (pista correcta, no LatAm); no hay crasheos ni silencios donde había voz; la duración de las líneas no corta el audio ni desincroniza los subtítulos.
3. Si una línea suena en inglés, es que su ID no se sustituyó (revisar el inventario).

---

## 10. Riesgos y preguntas abiertas

| Riesgo / pregunta | Estado | Mitigación |
|---|---|---|
| El juego 360 pida eventos de voz que no existen en Vita (o viceversa) | Abierto | Comparar IDs en Fase 5.0; si falta una línea en Vita, esa línea queda en inglés (se documenta, no se inventa). El precedente AC3 (IDs distintos entre plataformas) obliga a no asumir nada. |
| El códec de Vita no sea ATRAC9 ni Vorbis | Abierto | Verificar por hexdump al recibir los archivos; vgmstream cubre ATRAC9 y Vorbis. Si es otro códec, reevaluar. |
| La versión de Wwise (2013.1) no decodifique un `.wem` generado con Wwise moderno | Abierto | Preferir el reempaquetador propio (reproduce el layout exacto de 360) y validar por round-trip; la vía Wwise Authoring queda como alternativa con prueba. |
| Las dos pistas `spa` del `.wmv`: ¿cuál es España? | **Sin verificar** | Prueba en el teléfono (escuchar la intro en español); si es LatAm, cambiar el índice de pista. Método alternativo: transcribir ambas pistas y comparar el vocabulario con `localization_spanish` vs `localization_latamspanish`. |
| `guids.snd` no tiene nombres (solo GUID→ID) | Verificado | La identificación de voces se hace por STT (probado), no por nombres. |
| 57 IDs aparecen tanto embebidos como streamed; 55 duplicados entre bancos | Observado, sin explicación | Al sustituir, reemplazar el ID en todos los sitios donde aparezca. |
| Wwise Authoring es Windows-only y la versión 2013.1 puede no estar disponible | Conocido | El reempaquetador propio corre en Linux y no depende de Wwise. |
| `wwiseutil` y su soporte big-endian (360) | Sin verificar | No depender de él; la reconstrucción del `.pck` se hace con el parser propio ya validado. |
| El `.srt` usa `loc=<id>`, no texto plano | Verificado | El texto se resuelve en los `.isg.ckd` (ya extraídos y verificados en español). No afecta a las voces. |

---

## 11. Correcciones a afirmaciones previas (para que no se arrastren)

- **FASE4 ("solo `sounds_eng.pck`, voces solo en inglés"):** incorrecta en la forma. `sounds_eng.pck` está vacío y no hay ningún banco de voces separado; todo el audio está en `sounds_sfx.pck`. La conclusión correcta es: *el narrador en los datos de 360 es inglés (probado por transcripción), no hay banco de voces en español en 360*.
- **"Todos los `.wem` son XMA (tag `0xFFFF`)" (informe OpenCode):** incorrecto. El tag `0xFFFF` es **Vorbis de Wwise** (verificado con vgmstream: `Custom Vorbis`). La ruta XMA2Encode no aplica.
- **"Hay un montón de entradas `VO_` en `sounds_sfx.pck`" (nota en MEMORY.md):** no verificado y, en la forma en que se entiende (nombres de voz), incorrecto. `guids.snd` no contiene ningún `VO_` (grep = 0) y los bancos solo guardan IDs numéricos. La identificación de voces se hace por transcripción, no por nombres.
- **"El doblaje español ya está en 360" (interpretación de la actualización del usuario):** matizado. El doblaje español **de las cinemáticas** sí está en 360 (pistas `spa` en los `.wmv`). El doblaje español **del narrador del juego** no está en 360 (el narrador es inglés, probado). El usuario tenía razón en que el juego tiene doblaje español (Steam lo confirma) y en que las cinemáticas lo traen; la parte que necesita Vita es solo el narrador.

---

## 12. Datos técnicos de referencia (para la implementación)

### Audio de 360
- `sounds_eng.pck`: 56 B, 0 bancos, 0 archivos.
- `sounds_sfx.pck`: 412.957.574 B; 46 bancos (BKHD v88 = Wwise 2013.1, 51.743.693 B); 482 streamed (361,2 MB); 1.393 embebidos (50.940.969 B). Total 1.875 `.wem`.
- Streamed: RIFX, Vorbis, 48 kHz, tamaños 482 B–6.743.175 B, mediana 211.581 B.
- Banco de diálogo: **782826392** (15.837.311 B, offset 4.424.785; 673 clips DIDX, mediana 8.344 B; HIRC 535.004 B).
- Clip de narración probado: media ID **228152946**, 741.738 B, 31,07 s, estéreo, 48 kHz, Vorbis 190 kbps → transcripción inglesa, p=0,99.

### Cinemáticas
- 11 `.wmv` en `world/common/movie/`. 7 con 9 pistas de audio (fre, jpn, ger, spa, ita, rus, por, spa, eng), wmapro 5.1, 44,1 kHz, 74,999 s.
- 61 `.srt` en `child_of_light_x360.ipk` (7 `.es.srt` + 7 `.mx.srt`).
- 11 localizaciones `.isg.ckd` en `bundle_x360.ipk` (incl. `localization_spanish` y `localization_latamspanish`).

### `.ipk` (formato verificado)
- VER 7, big-endian, cabecera `50 EC 12 BA`, tabla en `0x30`, entradas `DUMMY1, SIZE, ZSIZE, [DUMMY1==0/2: 2 longs extra], TSTAMP(8), OFFSET(8), PATHSZ, PATH, NAMESZ, NAME, CRC, DUMMY2`; datos zlib (`clog`) o raw (`ZSIZE==0`). Para `DUMMY1==0`: extra = (tamaño descomprimido, tamaño almacenado); `SIZE` no es el tamaño descomprimido en ese caso.
- `fullLogic_x360.ipk`: 3.626 archivos. `persistentloading_x360.ipk`: 46. `bundle_x360.ipk`: 3.611 (parseados 3.609; desync en la entrada 3.609, las 2 últimas sin parsear). `child_of_light_x360.ipk`: 3.344 (completo).
- `guids.snd`: en `bundle_x360.ipk`, `sound/wwisebanks/x360/guids.snd`, 77.175 B, raw (`ZSIZE=0`), offset 9.660.183 (+ `BASE_OFF` 553.575). 1.575 líneas GUID→ID.

### Herramientas verificadas en este trabajo
- **vgmstream r2168** (`tools/audio/vgmstream/vgmstream-cli`): decodifica `.wem` Vorbis de Wwise (metadata y WAV). También decodifica ATRAC9 (para Vita).
- **faster-whisper 1.2.1** (modelo `tiny`, CPU int8): transcribe y detecta idioma. Nota: hubo que actualizar `av` (PyAV) a 19.0.2 y pasar el audio como array numpy (el `av.open` de faster-whisper falla con `metadata_errors` en esta combinación de versiones); el bypass con `wave` + numpy funciona.
- **wwiser** (`tools/audio/wwiser/wwiser.py`): parsea HIRC de bancos a XML.
- **Parser AKPK propio** (tablas de 20 B, validado por contigüidad y EOF exacto) y **parser `.ipk` propio** (`tools/audio/ipk_parse2.py`, validado por descompresión zlib exacta).
- **ww2ogg** (hcs64): `.wem` Vorbis → Ogg (dirección de decodificación; el inverso no existe como herramienta autónoma).
- Archivos de trabajo: `tools/audio/work/` (`guids.snd`, `bank_782826392.bnk`, `bank_782826392_didx.txt`, `localization_spanish.isg.ckd`, `localization_english.isg.ckd`, `r01_cin010_cs_intro_bink.{es,mx,en}.srt`, `drust_cinematicdialog.act.ckd`, `ipk_listings/*.txt`).

### Referencias web usadas
- Steam (el título): "Full Audio" para Español de España y otros 7 idiomas (8 idiomas con audio completo).
- hcs64/ww2ogg y notas de The Saboteur (formato Vorbis de Wwise, fmt `0xFFFF`, codebook en `fmt`).
- OBR Music Tool / guías de modding Wwise: el camino estándar para crear `.wem` es Wwise Authoring (gratuito, Windows); `wwiseutil` reemplaza pero no codifica.

---

## 13. Addendum — prueba v1.3-full en el teléfono (2026-11-13 14:46–14:57)

Información recibida después de redactar el informe (vía MEMORY.md, prueba del usuario en su teléfono):

- **El juego SE JUEGA.** Auto-import funciona. **Diálogo en ESPAÑOL en el juego ("Vuela, Igniculus, amigo.")** — confirma en el teléfono lo verificado aquí en archivos: los textos/subtítulos en español funcionan con `user_language=5`.
- **Caveat relevante para §6 (cinemáticas):** el log de v1.3 muestra que el juego busca `plan_logo.es.srt` (el español SÍ está activo) pero **NO lo encuentra (0xc000000f)** — el `.srt` no se resuelve por VFS aunque existe en el `.ipk` (verificado en §6: está en `child_of_light_x360.ipk`). Esto es un problema de resolución VFS de los `.srt`, **distinto** de la pista de audio del `.wmv` (el audio va incrustado en el vídeo y no depende del `.srt`). Consecuencia práctica: las cinemáticas deberían sonar en español (pista de audio), pero es posible que sus subtítulos no se muestren hasta que se resuelva el VFS de los `.srt`. Queda como bug de Fase 5/v1.4, fuera del alcance de las voces.
- Esto **no cambia el veredicto de voces**: el narrador del juego sigue siendo inglés en los datos de 360 (probado en §3) y sigue haciendo falta traerlo de Vita. El diálogo en español que ve el usuario es el texto/subtítulo, no la voz del narrador.

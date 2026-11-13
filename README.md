# Child of Light Recompiled — port nativo a Android

Recompilación estática **no oficial** de **Child of Light (Xbox 360)** para que corra
nativo en Android (ARM64), sin emulador completo.

El método es el mismo que ya funcionó con otros juegos UbiArt:

- **Rayman Origins Recompiled** (XDanfr/RaymanOriginsRecomp): Xbox 360 → ARM64 nativo
  en Android, renderer Vulkan propio, 60 fps en Galaxy S23.
- **Legends-Recomp** (edeegg/Legends-Recomp): Rayman Legends Xbox 360 → Android.

Las herramientas son **XenonRecomp** (traduce el ejecutable PowerPC de Xbox 360 a C++),
**ReXGlue SDK** (runtime: kernel, sistema de archivos, GPU, audio) y **XenosRecomp**
(convierte los shaders de Xbox 360 a SPIR-V para Vulkan).

## Estado: Fase 0 — verificación

**No se está construyendo el juego todavía.** La Fase 0 responde una sola pregunta:
¿cuánto trabajo manual exigiría recompilar *este* XEX concreto?

1. Obtener el `default.xex` **desencriptado** de la copia propia de Xbox 360.
2. Correr `XenonAnalyse` sobre él en GitHub Actions (sin PC).
3. Leer el informe: jump tables detectadas, funciones, imports kernel/XAM, shaders.
4. Decidir con datos si se pasa a la Fase 1 (esqueleto del proyecto) o se aparca.

El launcher actual con Vita3K (0.2.68) sigue su curso en paralelo y no se toca:
este es un proyecto **separado**.

## Modelo legal

Igual que todos los recomps de la comunidad:

- Este repo contiene **solo código original + configuración** (direcciones, límites de
  funciones y otros datos sobre el ejecutable). Licencia **GPL-3.0** (la misma que
  Unleashed Recompiled y Rayman Origins Recompiled, para poder reutilizar su runtime).
- **No contiene código del juego, ni assets, ni el ejecutable recompilado, ni claves.**
- Cada persona aporta **su propia copia legalmente obtenida** de Child of Light para
  Xbox 360. Sin ella no hay nada que recompilar.
- Child of Light y Rayman son marcas de Ubisoft Entertainment. Este proyecto **no está
  afiliado, respaldado ni patrocinado por Ubisoft o Microsoft**.
- No se aceptan issues ni pull requests con archivos del juego, salida recompilada ni
  enlaces para descargar el juego.

## La copia del juego (bloqueo nº 0)

- Child of Light en Xbox 360 era **solo digital (Xbox Live Arcade)**: no hubo disco.
  Title ID `5841140D`, ~1,4–1,5 GB.
- La tienda de Xbox 360 **cerró el 29-07-2024**: no se puede comprar nada nuevo.
  Lo ya comprado sigue siendo jugable y redescargable en la Xbox 360.
- **No es retrocompatible** en Xbox One/Series. Comprar la versión nativa de Xbox One
  **NO** da el `default.xex` de Xbox 360.
- Para obtener el XEX: redescargar el juego en la Xbox 360 propia → extraer el
  contenedor STFS (XBLA) con `wxPirs` o `xenia-vfs-dump` → `default.xex`
  **desencriptado**. Anotar el SHA-256 y si lleva Title Update (TU1 documentado,
  3-may-2014; hay indicios de un TU2). En la práctica, sacar los archivos suele
  hacerse con consola modificada (RGH/JTAG) + dashboard Aurora + FTP; en una consola
  retail el XEX dentro del contenedor va cifrado y ligado a licencia.

## Riesgo conocido: Mali (bloqueo nº 1)

En el hardware objetivo (Helio G99 + Mali-G57 MC2), el proyecto hermano Zandatsu
documenta que el renderer nativo Vulkan **pierde el dispositivo a los ~4 s** de abrir
(`docs/KNOWN_ISSUES.md` v1.3, punto 12, causa desconocida). Ningún recomp con renderer
nativo está verificado estable en ese SoC. Por eso el launcher final deberá llevar
**perfil Mali-G57** (Vulkan 1.1, 4 descriptor sets, sin `shaderInt64`) y **fallback
automático nativo → emulado**. La Fase 0 no resuelve esto; solo se prueba al final,
en el teléfono real.

## Cómo correr el análisis (Fase 0)

El workflow `.github/workflows/analyze-xex.yml` se lanza a mano desde la pestaña
Actions del repo (`workflow_dispatch`):

1. Sube el `default.xex` desencriptado a un alojamiento privado con **enlace de
   descarga directa** (un enlace de Drive normal no vale: tiene que ser descarga
   directa; el XEX nunca va al repo ni a ningún sitio público).
2. Lanza el workflow con:
   - `xex_url`: la URL de descarga directa del `default.xex` (**obligatorio**).
   - `xex_sha256`: el SHA-256 del archivo (**opcional**, recomendado: verifica que
     el archivo descargado es el que se dumpeó).
3. El workflow clona y compila XenonRecomp, corre `XenonAnalyse` sobre el XEX y
   genera el informe.
4. Descarga el artefacto `col-xex-analysis` de la pestaña Actions: contiene
   `report.md` (el informe), `switch_tables.toml` (jump tables detectadas) y
   `xenonanalyse.log` (salida completa de la herramienta).

### Qué mide el informe

| Dato | Fuente | Estado |
|---|---|---|
| Jump tables detectadas | `XenonAnalyse` (salida real) | ✅ medido |
| Tamaño y SHA-256 del XEX | archivo descargado | ✅ medido |
| Funciones alcanzadas solo por puntero/vtable | — | TODO: no lo produce `XenonAnalyse`; exige correr el codegen de XenonRecomp con una config mínima y contar las funciones sin `.pdata` (ver `tools/generate_analysis_report.py`) |
| Imports kernel/XAM | — | TODO: exige parsear la tabla de librerías de importación de la cabecera XEX (formato XEX2, big-endian). Ver docs de Xenia sobre el formato XEX |
| Inventario de shaders | — | TODO: exige XenosRecomp sobre los datos del juego extraídos, no solo sobre el XEX |

## Estructura del repo (Fase 0)

```
child-of-light-recomp/
├── .github/workflows/analyze-xex.yml   # Análisis del XEX en GitHub Actions
├── tools/generate_analysis_report.py   # Genera report.md a partir de XenonAnalyse
├── README.md
├── LICENSE                              # GPL-3.0
└── .gitignore                           # private/, C++ generado, builds
```

Fases siguientes (solo si la Fase 0 sale razonable): esqueleto del proyecto
(`tools/` con el fork de XenonRecomp + detector de jump tables, `rex/` con el
manifiesto ReXGlue, `runtime/` kernel/XAM, `android/` launcher + parches ReXGlue),
renderer nativo con perfil Mali-G57, y pruebas en el teléfono.

## Referencias

- Plan completo: `~/workspace/research/plan-port-nativo-col-2026-11-13.md`
- https://github.com/hedge-dev/XenonRecomp (README con uso de XenonAnalyse/XenonRecomp)
- https://github.com/rexglue/rexglue-sdk
- https://github.com/hedge-dev/XenosRecomp
- https://github.com/XDanfr/RaymanOriginsRecomp (receta completa de referencia)
- https://github.com/winnerspiros/UnleashedRecomp_Android (pipeline en GitHub Actions)
- https://github.com/dfdx047/Zandatsu (renderer nativo ReXGlue + issue de Mali G99/G57)

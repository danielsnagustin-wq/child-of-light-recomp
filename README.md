# Recompilación estática Xbox 360 → Android (ARM64)

Investigación y trabajo en curso sobre recompilación estática de un título
de Xbox 360 a Android nativo (ARM64), sin emulador completo. El método es
el que ya se usó con otros juegos del mismo motor (ver Referencias): **XenonRecomp**
traduce el ejecutable PowerPC a C++, **ReXGlue SDK** aporta el runtime
(kernel, sistema de archivos, GPU, audio) y **XenosRecomp** convierte los
shaders a SPIR-V para Vulkan.

El caso de estudio es un título de Xbox 360. El repo
no distribuye el juego ni nada derivado de él.

## Estado

Trabajo en curso (ronda v1.4.1). El runtime arranca en el teléfono de
pruebas y se depura por fases; nada de esto es una release. **Este repo no
publica APKs ni builds**: no hay releases ni artefactos con el juego.

## Modelo legal

- Este repo contiene **solo código original + configuración** (direcciones,
  límites de funciones y otros datos sobre el ejecutable). Licencia
  **GPL-3.0**. Ver `docs/DMCA.md`.
- **No contiene código del juego, ni assets, ni el ejecutable
  recompilado, ni claves, ni APKs.**
- Cada persona aporta **su propia copia legalmente obtenida** del juego
  para Xbox 360. Sin ella no hay nada que recompilar.
- Las marcas mencionadas pertenecen a sus dueños. Este proyecto **no está
  afiliado, respaldado ni patrocinado por Ubisoft o Microsoft**.
- No se aceptan issues ni pull requests con archivos del juego, salida
  recompilada ni enlaces para descargar el juego.

## Riesgo conocido: Mali

En el hardware de pruebas (Helio G99 + Mali-G57 MC2), el proyecto hermano
Zandatsu documenta que el renderer nativo Vulkan **pierde el dispositivo
a los ~4 s** de abrir (causa desconocida). Por eso el launcher lleva
perfil Mali-G57 (Vulkan 1.1, 4 descriptor sets, sin `shaderInt64`) y
fallback automático nativo → emulado. Se valida solo en el teléfono real.

## Estructura del repo

```
├── .github/workflows/        # Análisis del XEX y build Android en CI
├── analysis/                 # Informes de cada fase
├── docs/                     # Rendimiento, DMCA, notas de diseño
├── patches/XenonRecomp/      # Parches al recompilador
├── recomp/                   # Configuración y salida de XenonRecomp
├── tools/                    # Scripts de build local, detector de jump tables
├── README.md
├── LICENSE                   # GPL-3.0
└── .gitignore                # private/, C++ generado, builds
```

## Referencias

- https://github.com/hedge-dev/XenonRecomp
- https://github.com/rexglue/rexglue-sdk
- https://github.com/hedge-dev/XenosRecomp
- https://github.com/XDanfr/RaymanOriginsRecomp (receta de referencia)
- https://github.com/winnerspiros/UnleashedRecomp_Android (pipeline en GitHub Actions)
- https://github.com/dfdx047/Zandatsu (renderer nativo ReXGlue + issue de Mali G99/G57)

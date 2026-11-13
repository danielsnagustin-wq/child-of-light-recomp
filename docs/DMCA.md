# DMCA / modelo legal del port

**Fecha:** 2026-11-13.
**Proyecto:** port nativo de Child of Light (Xbox 360 → Android) por recompilación
estática, en el repo `danielsnagustin-wq/child-of-light-recomp` (público por
decisión del propietario, 2026-11-13).

## Qué hay en este repo

- **Solo código propio y herramientas**: workflows de GitHub Actions, scripts de
  análisis, documentación y (más adelante) el código recompilado generado a
  partir del ejecutable del juego.
- **NO hay código del juego, ni datos del juego, ni claves de descifrado, ni
  assets (texturas, audio, niveles, cinemáticas) en el repo.** El juego nunca
  se commitea.
- **NO hay Releases públicos con APK.** La APK solo existe como artefacto
  efímero de GitHub Actions, con retención de 7 días, y se borra sola.

## Cómo se juega

Cada persona usa **su propia copia legal** del juego de Xbox 360 (la versión
XBLA digital). El port exige esa copia: sin ella no hay juego que correr. La
APK no incluye los datos del juego; el launcher importa la copia del usuario
en su propio teléfono.

## Por qué este modelo

- La recompilación estática traduce el código del ejecutable a C++ y luego a
  ARM64; no redistribuye el juego ni sus assets.
- Ubisoft sí ha presentado DMCAs en 2026 (caso Slopsmith, 131 repos): ese
  proyecto llevaba las claves AES del juego incrustadas en el código y
  extraía las canciones base para usarlas sin el juego. Este port no hace
  ninguna de las dos cosas: no lleva claves ni extrae contenido del juego.
- Ningún proyecto de recompilación estática (recomp) ha sido tumbado por DMCA
  hasta hoy (verificado 2026-11-13). Aun así, el riesgo se concentra en la
  APK, por eso nunca se publica como Release y su artefacto caduca en 7 días.

## Contacto por retirada

Si Ubisoft o cualquier titular de derechos considera que algún contenido de
este repo infringe sus derechos, puede abrir un issue en este repo o usar el
mecanismo de retirada de GitHub; el contenido señalado se retirará de
inmediato.

#!/usr/bin/env bash
# assemble-android-tree.sh -- reconstruir el arbol Android ensamblado
# (tools/rexauto/) desde el repo + rexauto@dac49b33.
#
# El repo es un DELTA sobre rexauto: solo los archivos modificados o nuevos
# viven aqui. La copia de trabajo (~/workspace/col-recomp/tools/rexauto/)
# es el arbol ENSAMBLADO (checkout rexauto + overlay + SDK + keystore) y
# NO es un repo git: si la VM se borra, se pierde. Este script la
# reconstruye desde cero y la verifica contra la copia viva (hunt 2, P1-4).
#
#   tools/assemble-android-tree.sh <dest> [--verify-against <tree>]
#
#   <dest>              directorio nuevo que recibira el arbol ensamblado
#                       (se crea $DEST/tools/rexauto/; <dest> NO debe existir)
#   --verify-against    arbol vivo con el que comparar (por defecto, si
#                       existe ~/workspace/col-recomp/tools/rexauto, se usa
#                       ese). La comparacion excluye build/, .gradle/, .git/,
#                       el keystore y los archivos locales (local.properties,
#                       gradle.properties, settings.gradle.kts).
#
# Pasos: (1) fuente del delta: este checkout si es git, si no un clon
# --depth 1 a un temporal; (2) clon shallow de rexauto en el pin
# dac49b33; (3) overlay de SOLO los archivos trackeados del repo
# (git ls-files: asi no viajan sdk/, build/, .gradle/ ni el keystore);
# (4) setup del SDK con nuestro android_sdk.sh (10 parches); (5) diff
# contra el arbol vivo. Un diff no vacio = un archivo modificado en
# local que no esta en el repo (el bug P0-2 de la caza: VirtualPadView.java
# con el fix del pulso se habria perdido).
#
# NO reconstruye: autoports/childoflight/port (codigo generado por
# XenonRecomp/rexauto, no trackeado: hay que regenerarlo o copiarlo de un
# backup) ni ci-release.jks (keystore privado: copiarlo aparte de su backup
# o restaurarlo del secreto CI_KEYSTORE_BASE64).
set -euo pipefail

REXAUTO_REF="dac49b331f2632f0b050f469e35747721300b8b2"
REXAUTO_REPO="https://github.com/Player124413/rexauto.git"
OUR_REPO="https://github.com/danielsnagustin-wq/child-of-light-recomp.git"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DEST="${1:-}"
VERIFY_AGAINST=""
if [ "${2:-}" = "--verify-against" ]; then
  VERIFY_AGAINST="${3:-}"
fi
[ -n "$DEST" ] || { echo "uso: $0 <dest> [--verify-against <tree>]" >&2; exit 1; }
[ -e "$DEST" ] && { echo "$DEST ya existe; borrarlo o elegir otro" >&2; exit 1; }

# 1. Fuente del delta: este checkout si es git, si no un clon temporal.
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  SRC="$ROOT"
  TMP_SRC=""
else
  TMP_SRC="$(mktemp -d)"
  trap 'rm -rf "$TMP_SRC"' EXIT
  echo "[assemble] clonando el repo a $TMP_SRC/repo (el checkout local no es git)"
  git clone -q --depth 1 "$OUR_REPO" "$TMP_SRC/repo"
  SRC="$TMP_SRC/repo"
fi

# 2. rexauto en el pin (shallow: solo ese commit).
echo "[assemble] clonando rexauto@$REXAUTO_REF a $DEST/tools/rexauto"
mkdir -p "$DEST/tools"
git init -q "$DEST/tools/rexauto"
git -C "$DEST/tools/rexauto" remote add origin "$REXAUTO_REPO"
git -C "$DEST/tools/rexauto" fetch -q --depth 1 origin "$REXAUTO_REF"
git -C "$DEST/tools/rexauto" checkout -q "$REXAUTO_REF"

# 3. Overlay: SOLO los archivos trackeados del repo bajo tools/rexauto/.
echo "[assemble] overlay de los archivos trackeados del repo"
cd "$SRC"
COUNT=$(git ls-files tools/rexauto/ | wc -l)
[ "$COUNT" -gt 0 ] || { echo "ERROR: git ls-files no devolvio nada bajo tools/rexauto/" >&2; exit 1; }
overlay_tracked() {
  git ls-files tools/rexauto/ | while IFS= read -r f; do
    mkdir -p "$DEST/$(dirname "$f")"
    cp "$SRC/$f" "$DEST/$f"
  done
}
overlay_tracked
echo "[assemble] $COUNT archivos del repo copiados"
# Sanity P0-2: el archivo del fix del pulso debe estar en el arbol.
test -f "$DEST/tools/rexauto/android/app/src/main/java/com/rexauto/port/gamepad/VirtualPadView.java" \
  || { echo "ERROR: VirtualPadView.java no llego al arbol ensamblado (P0-2)" >&2; exit 1; }

# 4. SDK con nuestro android_sdk.sh (10 parches + libadrenotools + SDL glue).
echo "[assemble] setup del SDK (nuestro android_sdk.sh)"
"$DEST/tools/rexauto/tools/android_sdk.sh"

# 4b. Re-overlay DESPUES del SDK (hallazgo de la prueba 1, 2026-11-13):
# android_sdk.sh hace rm -rf del SDK y lo reconstruye SOLO desde los
# parches; los archivos SDK trackeados (17 + los 4 nuevos del STFS de Fase
# 5: stfs_data_file.h/cpp, stfs_asset.h, stfs_asset_source.cpp) y las
# ediciones a mano (WaitMultiple, mprotect, STFS fd...) NO los produce
# ningun parche. Sin este segundo overlay, un arbol reconstruido perderia
# el soporte STFS. Los archivos trackeados ganan a los parches.
echo "[assemble] re-overlay de los archivos trackeados (gana al SDK recien clonado)"
overlay_tracked

# 4c. Stage de xbox360_titles.json (el CI lo stagea desde rexauto/tools/;
# el repo no lo trackea y el overlay no lo trae).
cp "$DEST/tools/rexauto/tools/xbox360_titles.json" \
   "$DEST/tools/rexauto/android/app/src/main/assets/xbox360_titles.json"
echo "[assemble] xbox360_titles.json stageado en assets/"

# 5. Verificacion contra el arbol vivo (si se pidio o existe el default).
if [ -z "$VERIFY_AGAINST" ] && [ -d "$HOME/workspace/col-recomp/tools/rexauto" ]; then
  VERIFY_AGAINST="$HOME/workspace/col-recomp/tools/rexauto"
fi
if [ -n "$VERIFY_AGAINST" ]; then
  echo "[assemble] comparando con $VERIFY_AGAINST ..."
  # Excluidos: build/ y .cxx/ (salidas de build), .gradle/, .git/, out/
  # (salida de build dentro del SDK), keystore y archivos locales.
  # --no-dereference: hay symlinks colgando en thirdparty/moltenvk que
  # diff no puede seguir (ruido en ambas copias).
  if diff -rq --no-dereference \
      -x build -x .cxx -x .gradle -x .git -x out \
      -x ci-release.jks -x local.properties -x gradle.properties \
      -x settings.gradle.kts \
      "$DEST/tools/rexauto" "$VERIFY_AGAINST" > /tmp/assemble-diff.txt 2>&1; then
    echo "[assemble] OK: el arbol ensamblado es identico al arbol vivo (salvo build/, .cxx/, .gradle/, .git/, out/, keystore y archivos locales)"
  else
    echo "[assemble] DIFERENCIAS con el arbol vivo (un archivo aqui y no alli = modificado en local sin pushear, clase P0-2):"
    cat /tmp/assemble-diff.txt
    exit 1
  fi
fi
echo "[assemble] hecho: $DEST/tools/rexauto"

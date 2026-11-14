#!/usr/bin/env bash
# Build XenonRecomp for Child of Light from a CLEAN upstream + our patches.
#
# Usage: tools/build-xenonrecomp.sh
#
# Resets tools/XenonRecomp to pristine upstream HEAD, applies every patch in
# patches/XenonRecomp/ in numeric order, then configures (Clang >= 18, Ninja,
# Release) and builds. The patched tree is a build artifact: the patches are
# the source of truth, never hand-edits in tools/XenonRecomp/.
#
# XenonRecomp REQUIRES Clang >= 18 (GCC fails on xbox.h:248, anonymous
# aggregate with a member that has a constructor).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
XR="$ROOT/tools/XenonRecomp"
PATCHES="$ROOT/patches/XenonRecomp"

if [ ! -d "$XR/.git" ]; then
    echo "tools/XenonRecomp is not a git checkout; aborting" >&2
    exit 1
fi

cd "$XR"
# Reset to pristine upstream (keeps submodules; drops the build dir).
git checkout -- .
git clean -fd
git submodule update --init --recursive -q

# Hunt 2, P2-8: si un parche falta del dir, el build saldria sin ese parche
# y sin avisar. Hoy son 15 (0001, 0004-0006, 0008, 0011, 0012, 0015-0022).
PATCH_COUNT=$(ls "$PATCHES"/*.patch | wc -l)
[ "$PATCH_COUNT" -eq 15 ] || { echo "expected 15 patches in $PATCHES, found $PATCH_COUNT" >&2; exit 1; }

# Clang >= 18 obligatorio (GCC falla en xbox.h:248, anonymous aggregate
# with a member that has a constructor).
CLANG_MAJOR=$(clang --version | head -1 | grep -oE '[0-9]+' | head -1)
[ "$CLANG_MAJOR" -ge 18 ] || { echo "Clang >= 18 required, found $CLANG_MAJOR" >&2; exit 1; }

for p in "$PATCHES"/*.patch; do
    [ -e "$p" ] || continue
    echo "applying $(basename "$p")"
    git apply "$p"
done

cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ -G Ninja
cmake --build build -j2

echo "built: $XR/build/XenonAnalyse/XenonAnalyse"
echo "built: $XR/build/XenonRecomp/XenonRecomp"

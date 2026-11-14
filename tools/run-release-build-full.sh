#!/bin/bash
# OBSOLETO (hunt 2, P2-2, 2026-11-13): NO USAR. Sin el sed del keystore
# (llego en v13): hoy fallaria con "No key with alias 'rexauto' found"
# porque el keystore corazon trae el alias childoflight<3. Usar
# tools/run-release-build-full-v141.sh. Se conserva como historia.
# Fase 3 (v1.2-full): variante PRIVADA con el juego adentro. Log: analysis/fase3-build-full-1.log
#
# SOLO local, NUNCA CI, NUNCA repo: el STFS del juego (1,5 GB, copia privada
# del usuario) va DENTRO de la APK como asset game.stfs y el juego lo lee
# DESDE DENTRO DE LA APK, sin copiarlo ni extraerlo nunca (Fase 5, v1.4:
# PortApp/StfsAsset pasan su fd al VFS, que monta el STFS sobre el APK;
# la auto-importacion (copia + extraccion, 3,26 GB) se elimino del todo).
#
# POR QUE NO COMO ASSET DE GRADLE: :app:compressReleaseAssets lee CADA asset
# entero en memoria (readAllBytes) y con game.stfs (1,5 GB) muere siempre
# con "Java heap space", ni con noCompress ni con heap 1536m (v1.2: el
# mismo fallo lo causo un game.stfs residual en intermediates/). Asi que
# Gradle construye la APK normal (FULL=true, sin el asset) y el STFS se
# INYECTA despues en el ZIP como entrada STORED, se alinea y se vuelve a
# firmar con el mismo keystore corazon. El launcher lee assets/game.stfs
# igual que si lo hubiera empaquetado Gradle. STORED es obligatorio: el
# VFS abre el asset por fd (AssetManager.openFd / AAsset_openFileDescriptor),
# y eso falla si la entrada va comprimida.
set -eu
RECOMP=/home/hatch/workspace/col-recomp
ANDROID_DIR=$RECOMP/tools/rexauto/android
APK=$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk
STFS=$RECOMP/private/ChildOfLight-XBLA
KEYSTORE=$ANDROID_DIR/ci-release.jks
FINAL=$RECOMP/private/ChildOfLight-recomp-v1.2-full.apk
cd $RECOMP
# Un build full anterior matado a mitad deja game.stfs en los intermediates
# y el siguiente build intenta comprimirlo: limpiar antes (rutas ABSOLUTAS:
# el trap de la primera version usaba rutas relativas y no borraba nada
# porque el script ya habia hecho cd a tools/rexauto/android).
rm -rf $ANDROID_DIR/app/build/intermediates/assets $ANDROID_DIR/app/build/intermediates/compressed_assets
# La APK de salida se reconstruye siempre desde cero: un intento anterior
# pudo dejarla con game.stfs ya inyectado y sin firma (apksigner la
# despoja de META-INF al empezar a firmar); reinyectar duplicaria la entrada.
rm -f "$APK" "${APK%.apk}-aligned.apk"
cleanup() { rm -rf $ANDROID_DIR/app/build/intermediates/assets $ANDROID_DIR/app/build/intermediates/compressed_assets; }
trap cleanup EXIT
cd $ANDROID_DIR
export ANDROID_HOME=$HOME/workspace/android-sdk
export JAVA_HOME=$HOME/workspace/tools/jdk-17
export GRADLE_USER_HOME=$HOME/.gradle
export GRADLE_OPTS="-Djava.net.preferIPv4Stack=true"
export CMAKE_BUILD_PARALLEL_LEVEL=1
export PATH=$JAVA_HOME/bin:$HOME/workspace/tools/gradle-8.14/bin:$PATH
/home/hatch/workspace/tools/gradle-8.14/bin/gradle :app:assembleRelease \
  --no-daemon --max-workers=1 --offline \
  -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease \
  -PrexName=childoflight -PrexTitle="Child of Light" -PrexTitleId=5841140D \
  -PrexPortDir=$HOME/workspace/col-recomp/autoports/childoflight/port \
  -PrexSdkDir=$HOME/workspace/col-recomp/tools/rexauto/android/sdk/rexglue-sdk \
  -PrexVersionCode=2 -PrexVersionName=1.2-full -PrexFull=true || exit 1
echo "[full] APK base construida: $(stat -c%s "$APK") B"
# --- inyeccion del STFS como entrada STORED (sin comprimir) ---
python3 - "$APK" "$STFS" <<'EOF'
import sys, zipfile
apk, stfs = sys.argv[1], sys.argv[2]
info = zipfile.ZipInfo('assets/game.stfs', date_time=(1980, 1, 1, 0, 0, 0))
info.compress_type = zipfile.ZIP_STORED
info.external_attr = 0o644 << 16
with zipfile.ZipFile(apk, 'a', allowZip64=True) as z:
    with z.open(info, 'w', force_zip64=True) as dest:
        with open(stfs, 'rb') as src:
            while True:
                chunk = src.read(1 << 20)
                if not chunk:
                    break
                dest.write(chunk)
print('[full] game.stfs inyectado como assets/game.stfs (STORED)')
EOF
N=$(unzip -l "$APK" | grep -c "assets/game.stfs") || true
[ "$N" = "1" ] || { echo "[full] ERROR: $N entradas assets/game.stfs en la APK"; exit 1; }
echo "[full] entrada game.stfs unica OK"
BT=$HOME/workspace/android-sdk/build-tools/34.0.0
ALIGNED=${APK%.apk}-aligned.apk
"$BT/zipalign" -f 4 "$APK" "$ALIGNED" && mv "$ALIGNED" "$APK"
echo "[full] zipalign OK"
# apksigner escribe su APK temporal en java.io.tmpdir (por defecto /tmp:
# un tmpfs de 512 MB). Con una APK de 1,6 GB muere con "No space left on
# device" a mitad de la firma y deja la APK sin META-INF. $TMPDIR solo NO
# basta (la JVM lo ignora aqui): hay que pasarlo con -Djava.io.tmpdir via
# JAVA_TOOL_OPTIONS, que la JVM recoge en cada arranque.
mkdir -p $RECOMP/private/tmp
export TMPDIR=$RECOMP/private/tmp
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$RECOMP/private/tmp"
export JAVA_HOME=$HOME/workspace/tools/jdk-17
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:rexauto \
  --key-pass pass:rexauto --ks-key-alias 'childoflight<3' \
  --v1-signing-enabled true --v2-signing-enabled true "$APK"
"$BT/apksigner" verify --print-certs "$APK" | head -3
cp "$APK" "$FINAL"
echo "[full] APK final: $FINAL ($(stat -c%s "$FINAL") B, SHA $(sha256sum "$FINAL" | cut -d' ' -f1))"

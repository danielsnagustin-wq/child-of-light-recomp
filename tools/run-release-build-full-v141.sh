#!/bin/bash
# Fase 5.1 (v1.4.1-full): variante PRIVADA con el juego adentro. Log: analysis/fase5.1-build-full-1.log
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
#
# LOS 7 DLC (M1, v1.4.2) van por el mismo camino: se inyectan despues como
# assets/dlc/<content_id> (STORED, con verificacion de tamano+MD5 por
# paquete) y DlcAssets los copia a <external>/data/dlc/ en cada arranque.
# Los STFS de los DLC nunca entran en el repo: viven en private/dlc/.
set -euo pipefail
RECOMP=/home/hatch/workspace/col-recomp
ANDROID_DIR=$RECOMP/tools/rexauto/android
APK=$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk
STFS=$RECOMP/private/ChildOfLight-XBLA
KEYSTORE=$ANDROID_DIR/ci-release.jks
FINAL=$RECOMP/private/ChildOfLight-recomp-v1.4.1-full.apk
DLC_DIR=$RECOMP/private/dlc
# Tabla DLC (M1, v1.4.2): nombre del archivo en private/dlc | content_id del
# header STFS (offset 0x32C, 40 hex en mayusculas) | MD5 del fuente. Los
# content_id se verificaron por parseo binario de los 7 (caza ronda 2 DLC,
# P1-1); el sufijo del nombre del dump NO es el Content ID. La entrada en
# la APK se llama assets/dlc/<content_id>.
DLC_TABLE="
Golem-775BBBBCEBBE90A5FE533A042A8BBE3CB0BBD2EB58|59AAD9B1F3DD5A03D8EB66466FA476847498EFF4|a21e628bd06eed90e2529bb1ad25e676
Light-AADBD67854A470A8FB5A9E73225CAD290BB52C1058|83FEB636B64A47F8FEC9EA2A55E254DCE89D00C0|9ec94e6183ebcc683135a4a56c1cd952
Dark-70D7B15761AFF21B4DEE7DE329ABE8ED4B0B05FC58|A199ACED8CEDB007C46061ABA294E05EA2E241EC|55493377f7dfa2413df13c2072f9cde7
Stardust-6674A1F912536CAD7FB7F5DEAD5DB32FB09B146158|52BFDD521AA72DE41A97B13C656332E79C093DAB|bb2fbd72ab816b94b24537c27727bc1d
Rough-AAE0CC0D4062788BD43880664F6BC1FB70B0008F58|0811DFC11EC23BE00C74477AC169DA4F77A207C8|ed00afdffad0e25db0c902edd7bbaf4e
Tumbled-D38D4792DB8566BB709F0967FB464DECB463E7FA58|10E4CBDC2A9A2F3C60A44FEC0A856A423CC627E4|94383465dcace58af95039ff22b090c8
Faceted-767965C3EBCCCFD10C839B63A74E2B90B196305058|8841A722359BB5191DF248F105C09A57CE9E2619|4ac8b8f015aa32e284e25f6965b9310f
"
cd $RECOMP
# Pre-chequeos (hunt 2, P1-3/P2-4): fallar al inicio, no tras ~17 min de
# Gradle (STFS) ni en el apksigner sign (keystore).
test -f "$STFS" || { echo "[full] ERROR: STFS no encontrado: $STFS"; exit 1; }
test -f "$KEYSTORE" || { echo "[full] ERROR: keystore no encontrado: $KEYSTORE"; exit 1; }
# Pre-chequeo DLC (M1): los 7 paquetes deben existir antes del build, con
# el MD5 de DLC_TABLE (hunt 3, P2-5). Antes solo se comprobaba que el
# archivo existiera y un paquete equivocado (mismo nombre, contenido
# distinto) solo se detectaba tras ~17 min de Gradle, en la verificacion
# post-inyeccion. md5sum de 14,5 MB son segundos.
while IFS='|' read -r _dlc_file _dlc_cid _dlc_md5; do
  [ -n "$_dlc_file" ] || continue
  test -f "$DLC_DIR/$_dlc_file" || { echo "[full] ERROR: DLC no encontrado: $DLC_DIR/$_dlc_file"; exit 1; }
  _dlc_actual=$(md5sum "$DLC_DIR/$_dlc_file" | cut -d' ' -f1)
  [ "$_dlc_actual" = "$_dlc_md5" ] || { echo "[full] ERROR: MD5 del DLC $_dlc_file distinto del esperado ($_dlc_actual != $_dlc_md5)"; exit 1; }
done <<< "$DLC_TABLE"
# Un build full anterior matado a mitad deja game.stfs en los intermediates
# y el siguiente build intenta comprimirlo: limpiar antes (rutas ABSOLUTAS:
# el trap de la primera version usaba rutas relativas y no borraba nada
# porque el script ya habia hecho cd a tools/rexauto/android).
rm -rf $ANDROID_DIR/app/build/intermediates/assets $ANDROID_DIR/app/build/intermediates/compressed_assets
# La APK de salida se reconstruye siempre desde cero: un intento anterior
# pudo dejarla con game.stfs ya inyectado y sin firma (apksigner la
# despoja de META-INF al empezar a firmar); reinyectar duplicaria la entrada.
rm -f "$APK" "${APK%.apk}-aligned.apk"
cleanup() {
  rm -rf $ANDROID_DIR/app/build/intermediates/assets $ANDROID_DIR/app/build/intermediates/compressed_assets
  # El tmpdir de apksigner (1,6 GB si se interrumpe el sign): si no se
  # limpia aqui, nadie lo borra (hunt 2, P2-3).
  rm -rf $RECOMP/private/tmp
  # Revertir el alias del keystore (ver el sed de abajo): el archivo en el
  # repo debe seguir diciendo rexauto; CI lo cambia con su propio sed.
  sed -i 's/keyAlias = "childoflight<3"/keyAlias = "rexauto"/' $ANDROID_DIR/app/build.gradle.kts
}
trap cleanup EXIT
cd $ANDROID_DIR
# Keystore local (igual que en run-release-build-v14.sh): el keystore corazon
# trae el alias childoflight<3; build.gradle.kts dice rexauto. Sin este sed,
# :app:packageRelease falla con "No key with alias 'rexauto' found".
sed -i 's/keyAlias = "rexauto"/keyAlias = "childoflight<3"/' app/build.gradle.kts
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
  -PrexVersionCode=5 -PrexVersionName=1.4.1-full -PrexFull=true || exit 1
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
# Verificar el STFS inyectado (hunt 2, P1-3): tamano + MD5 del stream
# extraido contra el fuente. Sin esto, un STFS corrupto o truncado daria
# una APK firmada "exitosa" que fallaria al montar en el telefono.
SRC_SIZE=$(stat -c%s "$STFS")
INJ_SIZE=$(unzip -l "$APK" | awk '/assets\/game\.stfs/ {print $1}')
[ "$SRC_SIZE" = "$INJ_SIZE" ] || { echo "[full] ERROR: tamano del STFS inyectado ($INJ_SIZE) distinto del fuente ($SRC_SIZE)"; exit 1; }
SRC_MD5=$(md5sum "$STFS" | cut -d' ' -f1)
INJ_MD5=$(unzip -p "$APK" assets/game.stfs | md5sum | cut -d' ' -f1)
[ "$SRC_MD5" = "$INJ_MD5" ] || { echo "[full] ERROR: MD5 del STFS inyectado distinto del fuente"; exit 1; }
if [ "$INJ_MD5" != "ad88061a06fba28e3434945f37cb3d57" ]; then
  echo "[full] AVISO: MD5 del STFS ($INJ_MD5) distinto del dump conocido ad88061a... (revisar que el fuente sea el dump bueno)"
fi
echo "[full] STFS inyectado verificado: $INJ_SIZE B, MD5 $INJ_MD5"
# --- inyeccion de los 7 DLC como assets/dlc/<content_id> (STORED) ---
# M1 (v1.4.2, caza ronda 2 DLC): los STFS de private/dlc/ van dentro de la
# APK como assets; el launcher (DlcAssets, Java) los copia a
# <external>/data/dlc/ y el instalador nativo (OnPostSetup) los instala.
# La entrada se llama por el Content ID del header STFS, no por el sufijo
# del dump (P1-1). STORED es obligatorio igual que game.stfs
# (AssetManager.openFd lo exige). La tabla viaja por argv: el programa de
# python viene por stdin (heredoc) y no puede leer la tabla de ahi.
python3 - "$APK" "$DLC_DIR" "$DLC_TABLE" <<'EOF'
import sys, zipfile
apk, dlc_dir, table = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(apk, 'a', allowZip64=True) as z:
    for line in table.splitlines():
        line = line.strip()
        if not line:
            continue
        filename, content_id, _md5 = line.split('|')
        info = zipfile.ZipInfo('assets/dlc/' + content_id,
                               date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_STORED
        info.external_attr = 0o644 << 16
        with z.open(info, 'w', force_zip64=True) as dest:
            with open(dlc_dir + '/' + filename, 'rb') as src:
                while True:
                    chunk = src.read(1 << 20)
                    if not chunk:
                        break
                    dest.write(chunk)
        print('[full] DLC inyectado como assets/dlc/' + content_id)
EOF
N_DLC=$(unzip -l "$APK" | grep -c "assets/dlc/") || true
[ "$N_DLC" = "7" ] || { echo "[full] ERROR: $N_DLC entradas assets/dlc/ en la APK (esperadas 7)"; exit 1; }
echo "[full] 7 entradas DLC OK"
# Verificar cada DLC inyectado: tamano + MD5 del stream extraido contra
# el fuente (mismo patron que game.stfs, hunt 2 P1-3).
python3 - "$APK" "$DLC_DIR" "$DLC_TABLE" <<'EOF'
import sys, zipfile, hashlib
apk, dlc_dir, table = sys.argv[1], sys.argv[2], sys.argv[3]
ok = True
with zipfile.ZipFile(apk) as z:
    for line in table.splitlines():
        line = line.strip()
        if not line:
            continue
        filename, content_id, expected_md5 = line.split('|')
        entry = 'assets/dlc/' + content_id
        try:
            data = z.read(entry)
        except KeyError:
            print('[full] ERROR: falta la entrada ' + entry)
            ok = False
            continue
        src = open(dlc_dir + '/' + filename, 'rb').read()
        md5 = hashlib.md5(data).hexdigest()
        if len(data) != len(src) or md5 != expected_md5 or md5 != hashlib.md5(src).hexdigest():
            print('[full] ERROR: DLC ' + content_id + ' distinto del fuente')
            ok = False
        else:
            print('[full] DLC verificado: ' + content_id + ' ' + str(len(data)) + ' B MD5 ' + md5)
sys.exit(0 if ok else 1)
EOF
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
# Con pipefail (arriba) el fallo de verify mata el script; la salida
# completa queda en el log. Antes era `| head -3` sin pipefail: el exit
# code era el de head (0) y un verify fallido se tragaba (hunt 2, P1-1).
"$BT/apksigner" verify --print-certs "$APK" | tee "$RECOMP/private/verify-v141.txt"
cp "$APK" "$FINAL"
[ "$(stat -c%s "$APK")" = "$(stat -c%s "$FINAL")" ] || { echo "[full] ERROR: la copia final tiene tamano distinto"; exit 1; }
echo "[full] APK final: $FINAL ($(stat -c%s "$FINAL") B, SHA $(sha256sum "$FINAL" | cut -d' ' -f1))"

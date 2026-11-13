#!/bin/bash
# Fase 5.1 (v1.4.1): full release build (local, offline). Log: analysis/fase5.1-build-1.log
# Cambios frente a v1.3: STFS leido desde dentro de la APK sin importar ni
# copiar (PortApp/StfsAsset fd -> VFS monta el STFS; pantalla de importar
# eliminada en full); WaitMultiple bloqueante + mprotect primero (P0 #1);
# afinidad del hilo principal a los nucleos grandes (P0 #4); rewind Bionic
# "a+" en la cache de shaders (P0 #3); frame-skip de placeholders
# desactivado por defecto; log de geometria de pintado (diagnostico Bug 1).
cd /home/hatch/workspace/col-recomp/tools/rexauto/android
# Keystore local: el keystore corazon (ci-release.jks) trae el alias
# childoflight<3, pero build.gradle.kts dice rexauto (CI lo cambia con sed
# tras restaurar el secreto). Espejo local: cambiarlo para el build y
# revertirlo SIEMPRE al salir (trap), para que el archivo en el repo siga
# diciendo rexauto. Sin esto, :app:packageRelease falla con
# "No key with alias 'rexauto' found" (build 1 de v1.3).
sed -i 's/keyAlias = "rexauto"/keyAlias = "childoflight<3"/' app/build.gradle.kts
trap "sed -i 's/keyAlias = \"childoflight<3\"/keyAlias = \"rexauto\"/' app/build.gradle.kts" EXIT
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
  -PrexVersionCode=5 -PrexVersionName=1.4.1

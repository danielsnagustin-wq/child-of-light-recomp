#!/bin/bash
# Fase 3 (v1.2): full release build (local, offline). Log: analysis/fase3-build-1.log
# Cambios frente a v1.1: parche SDK rexglue-sdk-v0.10.0-mali-vertex-stores
# (relaja vertexPipelineStoresAndAtomics + fillModeNonSolid en Mali) y rediseño
# de la home del launcher en español estilo Child of Light.
cd /home/hatch/workspace/col-recomp/tools/rexauto/android
export ANDROID_HOME=$HOME/workspace/android-sdk
export JAVA_HOME=$HOME/workspace/tools/jdk-17
export GRADLE_USER_HOME=$HOME/.gradle
export GRADLE_OPTS="-Djava.net.preferIPv4Stack=true"
export CMAKE_BUILD_PARALLEL_LEVEL=1
export PATH=$JAVA_HOME/bin:$HOME/workspace/tools/gradle-8.14/bin:$PATH
exec /home/hatch/workspace/tools/gradle-8.14/bin/gradle :app:assembleRelease \
  --no-daemon --max-workers=1 --offline \
  -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease \
  -PrexName=childoflight -PrexTitle="Child of Light" -PrexTitleId=5841140D \
  -PrexPortDir=$HOME/workspace/col-recomp/autoports/childoflight/port \
  -PrexSdkDir=$HOME/workspace/col-recomp/tools/rexauto/android/sdk/rexglue-sdk \
  -PrexVersionCode=2 -PrexVersionName=1.2

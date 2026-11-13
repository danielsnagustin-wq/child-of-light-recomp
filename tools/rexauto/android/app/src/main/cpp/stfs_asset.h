#pragma once

// Producer/consumer contract for the bundled STFS asset fd (Fase 5, v1.4).
//
// Producer (Java, app side — this file's owner):
//   StfsAsset.ensureOpen() opens assets/game.stfs via AssetManager.openFd
//   and passes (fd, startOffset, length) through JNI
//   (Java_com_rexauto_port_StfsAsset_setAssetFd, in stfs_asset_jni.cpp).
//   The JNI function dup()s the fd immediately, so the Java copy can be
//   closed right after the call.
//
// Consumer (android_main.cpp + SDK/VFS worker):
//   StfsAssetFd() returns the dup'd fd plus the byte offset and length of
//   the STORED game.stfs asset inside the APK file, or false when no asset
//   fd was handed over (normal/dev builds, or a full build whose asset
//   could not be opened). The fd refers to the whole APK file: every read
//   must add start_offset, and reads must stay within [start, start+len).
//
// Lifetime: the dup'd fd stays open for the whole process. Never close it,
// and never use it after the process ends (it is re-opened on every launch
// by PortApp.onCreate, before any native code runs).
//
// Threading: the triple is written once, on the main thread, before SDL
// starts the native thread, so the write happens-before any read.

#include <cstdint>

namespace rexport {

// Returns false when no asset fd was handed over.
bool StfsAssetFd(int* fd, std::int64_t* start_offset, std::int64_t* length);

}  // namespace rexport

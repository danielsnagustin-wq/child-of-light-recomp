// JNI receiver for the bundled STFS asset fd (Fase 5, v1.4).
//
// Java (StfsAsset.ensureOpen, called from PortApp.onCreate on every launch)
// opens assets/game.stfs via AssetManager.openFd and calls
//   StfsAsset.setAssetFd(fd, startOffset, length)
// which lands here. This file dup()s the fd at once and stores the triple
// for android_main.cpp / the VFS worker (see stfs_asset.h).
//
// The fd refers to the whole APK file; startOffset/length delimit the
// STORED game.stfs entry inside it. The VFS side must add the offset to
// every read — the STFS container device opens by path today, so it needs
// an fd+base-offset mode (SDK worker's part, see analysis/FASE5-app.md).

#include "stfs_asset.h"

#include <android/log.h>
#include <jni.h>
#include <unistd.h>

namespace {
int g_fd = -1;
std::int64_t g_start_offset = 0;
std::int64_t g_length = 0;
}  // namespace

namespace rexport {

bool StfsAssetFd(int* fd, std::int64_t* start_offset, std::int64_t* length) {
  if (g_fd < 0 || g_length <= 0) return false;
  *fd = g_fd;
  *start_offset = g_start_offset;
  *length = g_length;
  return true;
}

}  // namespace rexport

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rexauto_port_StfsAsset_setAssetFd(JNIEnv* /*env*/, jclass /*clazz*/,
                                          jint fd, jlong start_offset,
                                          jlong length) {
  if (fd < 0 || start_offset < 0 || length <= 0) return JNI_FALSE;
  if (g_fd >= 0) return JNI_TRUE;  // already handed over once per process
  const int duped = ::dup(fd);
  if (duped < 0) {
    __android_log_print(ANDROID_LOG_ERROR, "StfsAsset",
                        "dup(asset fd) failed");
    return JNI_FALSE;
  }
  g_fd = duped;
  g_start_offset = static_cast<std::int64_t>(start_offset);
  g_length = static_cast<std::int64_t>(length);
  __android_log_print(ANDROID_LOG_INFO, "StfsAsset",
                      "asset fd handed over: fd=%d offset=%lld length=%lld",
                      g_fd, static_cast<long long>(g_start_offset),
                      static_cast<long long>(g_length));
  return JNI_TRUE;
}

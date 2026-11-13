/**
 ******************************************************************************
 * ReXGlue : Xbox 360 recompilation runtime                                 *
 ******************************************************************************
 * Copyright 2026 Tom Clay. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include <rex/filesystem/stfs_asset.h>

#include <mutex>
#include <utility>

#if defined(__ANDROID__) || defined(__linux__) || defined(__APPLE__)
#include <unistd.h>
#endif

namespace rex::filesystem {
namespace {

std::mutex g_stfs_asset_mutex;
StfsAssetSource g_stfs_asset_source;

void CloseSourceFd(StfsAssetSource& source) {
  if (source.fd >= 0) {
#if defined(__ANDROID__) || defined(__linux__) || defined(__APPLE__)
    ::close(source.fd);
#endif
    source.fd = -1;
  }
}

}  // namespace

void SetStfsAssetSource(int fd, uint64_t base_offset, uint64_t length) {
  // The app passes the raw AssetManager.openFd() fd; dup() it so the app
  // can close its own copy (and its AssetFileDescriptor) immediately.
  int owned_fd = -1;
  if (fd >= 0) {
#if defined(__ANDROID__) || defined(__linux__) || defined(__APPLE__)
    owned_fd = ::dup(fd);
#else
    owned_fd = fd;
#endif
  }
  if (owned_fd < 0) {
    return;
  }
  std::lock_guard<std::mutex> lock(g_stfs_asset_mutex);
  CloseSourceFd(g_stfs_asset_source);
  g_stfs_asset_source = StfsAssetSource{owned_fd, base_offset, length};
}

bool HasStfsAssetSource() {
  std::lock_guard<std::mutex> lock(g_stfs_asset_mutex);
  return g_stfs_asset_source.valid();
}

StfsAssetSource TakeStfsAssetSource() {
  std::lock_guard<std::mutex> lock(g_stfs_asset_mutex);
  StfsAssetSource source = g_stfs_asset_source;
  g_stfs_asset_source = StfsAssetSource{};
  return source;
}

}  // namespace rex::filesystem

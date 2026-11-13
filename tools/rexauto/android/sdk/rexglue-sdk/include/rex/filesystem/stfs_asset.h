/**
 ******************************************************************************
 * ReXGlue : Xbox 360 recompilation runtime                                 *
 ******************************************************************************
 * Copyright 2026 Tom Clay. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 *
 * @file        stfs_asset.h
 * @brief       Hand-off of an STFS container that lives inside the APK.
 *
 * Full (official-style) builds ship the game as an uncompressed
 * `game.stfs` asset (see app/build.gradle.kts: `noCompress += "stfs"`).
 * There is no host path for it, so the Android app hands the native side
 * the AssetManager.openFd() triple instead:
 *
 *   fd     = AssetFileDescriptor.getParcelFileDescriptor().getFd()
 *            (the fd of the whole APK file)
 *   offset = AssetFileDescriptor.getStartOffset()
 *   length = AssetFileDescriptor.getLength()
 *
 * The app calls SetStfsAssetSource() from JNI before the runtime is
 * created (before Runtime::Setup -> SetupVfs). The stored fd is a dup()
 * owned by this module; Runtime::SetupVfs takes ownership of it when it
 * mounts the container, and the StfsContainerDevice closes it on
 * shutdown. When no source is registered the old behaviour is unchanged:
 * the game is read from the extracted game_data_root directory.
 */

#pragma once

#include <cstdint>

namespace rex::filesystem {

struct StfsAssetSource {
  int fd = -1;
  uint64_t base_offset = 0;
  uint64_t length = 0;

  bool valid() const { return fd >= 0; }
};

// Stores a dup() of `fd` plus the asset slice [base_offset,
// base_offset + length). Replaces any previously stored source (closing
// its fd). Must be called before the runtime is created.
void SetStfsAssetSource(int fd, uint64_t base_offset, uint64_t length);

bool HasStfsAssetSource();

// Transfers ownership of the stored source to the caller and clears it.
// Returns an invalid source when none was registered.
StfsAssetSource TakeStfsAssetSource();

}  // namespace rex::filesystem

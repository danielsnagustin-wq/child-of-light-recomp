/**
 ******************************************************************************
 * ReXGlue : Xbox 360 recompilation runtime                                 *
 ******************************************************************************
 * Copyright 2026 Tom Clay. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 *
 * @file        stfs_data_file.h
 * @brief       One contiguous byte range backing an STFS/SVOD container
 *              fragment.
 *
 * A container fragment is either a whole host file (path mode, the
 * historical behaviour) or a slice of a larger file (fd mode): an
 * uncompressed asset inside an APK, addressed by the fd returned by
 * AssetManager.openFd plus its start offset and length. The STFS driver
 * only ever needs positional reads, so both backends expose the same
 * ReadAt() primitive; pread() in fd mode is position-independent and
 * therefore safe to call from several game threads at once, unlike the
 * fseek()/fread() pair used in path mode.
 */

#pragma once

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <memory>

namespace rex::filesystem {

class StfsDataFile {
 public:
  // Opens a whole host file. Returns nullptr when it cannot be opened or
  // its size cannot be determined.
  static std::unique_ptr<StfsDataFile> OpenPath(const std::filesystem::path& path);

  // Takes ownership of `fd` (the caller must dup() it first if it still
  // needs its own copy). The readable slice is [base_offset,
  // base_offset + length) inside the file `fd` refers to. Returns nullptr
  // when the slice lies outside the file.
  static std::unique_ptr<StfsDataFile> OpenFd(int fd, uint64_t base_offset, uint64_t length);

  ~StfsDataFile();

  StfsDataFile(const StfsDataFile&) = delete;
  StfsDataFile& operator=(const StfsDataFile&) = delete;

  // Reads exactly `size` bytes at `offset` (relative to the start of this
  // slice) into `buffer`. Returns false on a short read or any error.
  bool ReadAt(uint64_t offset, void* buffer, size_t size) const;

  // Reads up to `size` bytes at `offset`, returning how many were actually
  // read (0 on error or when `offset` is past the end). Used by the
  // container file reader, which keeps the historical short-read behaviour
  // for truncated/corrupt packages.
  size_t ReadAtPartial(uint64_t offset, void* buffer, size_t size) const;

  uint64_t size() const { return size_; }
  bool is_fd_backed() const { return fd_ >= 0; }

 private:
  StfsDataFile(FILE* file, uint64_t size);
  StfsDataFile(int fd, uint64_t base_offset, uint64_t size);

  FILE* file_ = nullptr;
  int fd_ = -1;
  uint64_t base_offset_ = 0;
  uint64_t size_ = 0;
};

}  // namespace rex::filesystem

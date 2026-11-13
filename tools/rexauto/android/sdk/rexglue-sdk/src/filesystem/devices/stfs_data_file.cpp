/**
 ******************************************************************************
 * ReXGlue : Xbox 360 recompilation runtime                                 *
 ******************************************************************************
 * Copyright 2026 Tom Clay. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#include <rex/filesystem/devices/stfs_data_file.h>

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <utility>

#include <rex/filesystem.h>
#include <rex/logging.h>

#if defined(__ANDROID__) || defined(__linux__) || defined(__APPLE__)
#include <sys/stat.h>
#include <unistd.h>
#define REX_STFS_FD_BACKED 1
#endif

namespace rex::filesystem {

std::unique_ptr<StfsDataFile> StfsDataFile::OpenPath(const std::filesystem::path& path) {
  FILE* file = rex::filesystem::OpenFile(path, "rb");
  if (!file) {
    return nullptr;
  }
  if (!rex::filesystem::Seek(file, 0L, SEEK_END)) {
    fclose(file);
    return nullptr;
  }
  int64_t size = rex::filesystem::Tell(file);
  if (size < 0) {
    fclose(file);
    return nullptr;
  }
  return std::unique_ptr<StfsDataFile>(new StfsDataFile(file, uint64_t(size)));
}

std::unique_ptr<StfsDataFile> StfsDataFile::OpenFd(int fd, uint64_t base_offset, uint64_t length) {
  // Takes ownership of `fd` in all cases: close it here on failure too.
  struct FdCloser {
    int fd;
    ~FdCloser() {
      if (fd >= 0) {
#ifdef REX_STFS_FD_BACKED
        ::close(fd);
#endif
      }
    }
  } closer{fd};

#ifdef REX_STFS_FD_BACKED
  if (fd < 0) {
    return nullptr;
  }
  struct stat st {};
  if (::fstat(fd, &st) != 0 || st.st_size < 0) {
    REXFS_ERROR("StfsDataFile: fstat failed for fd {}: {}", fd, std::strerror(errno));
    return nullptr;
  }
  if (base_offset > uint64_t(st.st_size) || length > uint64_t(st.st_size) - base_offset) {
    REXFS_ERROR("StfsDataFile: asset slice [{}, {}) is outside the file ({} bytes)",
                base_offset, base_offset + length, uint64_t(st.st_size));
    return nullptr;
  }
  auto result = std::unique_ptr<StfsDataFile>(new StfsDataFile(fd, base_offset, length));
  closer.fd = -1;  // Ownership moved to the StfsDataFile.
  return result;
#else
  (void)base_offset;
  (void)length;
  REXFS_ERROR("StfsDataFile: fd-backed containers are not supported on this platform");
  return nullptr;
#endif
}

StfsDataFile::StfsDataFile(FILE* file, uint64_t size) : file_(file), size_(size) {}

StfsDataFile::StfsDataFile(int fd, uint64_t base_offset, uint64_t size)
    : fd_(fd), base_offset_(base_offset), size_(size) {}

StfsDataFile::~StfsDataFile() {
  if (file_) {
    fclose(file_);
  }
#ifdef REX_STFS_FD_BACKED
  if (fd_ >= 0) {
    ::close(fd_);
  }
#endif
}

size_t StfsDataFile::ReadAtPartial(uint64_t offset, void* buffer, size_t size) const {
  if (!buffer || size == 0 || offset >= size_) {
    return 0;
  }
  size = std::min(size, size_t(size_ - offset));
  auto* out = static_cast<uint8_t*>(buffer);

#ifdef REX_STFS_FD_BACKED
  if (fd_ >= 0) {
    // pread() is position-independent, so concurrent reads from several
    // game threads need no locking around this file.
    size_t total = 0;
    while (total < size) {
      ssize_t got = ::pread(fd_, out + total, size - total,
                            off_t(base_offset_ + offset + total));
      if (got < 0) {
        if (errno == EINTR) {
          continue;
        }
        return total;
      }
      if (got == 0) {
        return total;  // Unexpected EOF inside the slice.
      }
      total += size_t(got);
    }
    return total;
  }
#endif

  // Path mode: the historical fseek()/fread() pair. Matches the previous
  // behaviour of the STFS driver exactly (including its lack of locking:
  // callers already serialize through the VFS where it matters).
  if (!rex::filesystem::Seek(file_, int64_t(offset), SEEK_SET)) {
    return 0;
  }
  return fread(out, 1, size, file_);
}

bool StfsDataFile::ReadAt(uint64_t offset, void* buffer, size_t size) const {
  return ReadAtPartial(offset, buffer, size) == size;
}

}  // namespace rex::filesystem

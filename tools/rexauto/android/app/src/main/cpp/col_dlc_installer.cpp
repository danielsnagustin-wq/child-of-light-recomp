// DLC installer for the Child of Light native port (M1, v1.4.2).
//
// Adapts the Skate 3 port's InstallDlcPackages
// (tools/rexauto/ports/skate3/src/skate3_app_common.cpp:1151-1254). The
// differences from Skate 3 are, in order of importance:
//
//  - P1-1: the staged file is named by the STFS header content ID (40
//    uppercase hex digits from offset 0x32C), NOT by the dump filename.
//    InstallContent uses filename() as the installed folder name, so a
//    package staged as "Golem-775B..." would install under that wrong name.
//    The content ID is derived from the header already read for validation.
//  - P0-1b: license_mask is forced to 0xFFFFFFFF before scanning. The game
//    checks bit 0 of the GLOBAL mask before enumerating marketplace content
//    (sub_82A4DD10, recomp/ppc/ppc_recomp.262.cpp:14575); settings.txt
//    already writes the same value, this is defense in depth.
//  - The packages are staged by the Java side (DlcAssets): the full build
//    script injects the 7 STFS files as APK assets assets/dlc/<content_id>
//    and DlcAssets copies them to <user_data>/dlc/ on every launch, so
//    that folder is the normal source. The configured root (col_dlc_root)
//    adds folders for packages dropped in by hand.
//  - The installed-content header lives where the SDK writes it:
//    <user_data>/0000000000000000/<title_id>/Headers/00000002/<name>.header
//    (Skate 3's InstalledMarketplaceHeaderPath), NOT beside the install dir.

#include "col_dlc_installer.h"

#include <cstdint>
#include <filesystem>
#include <iomanip>
#include <memory>
#include <sstream>
#include <string>
#include <unordered_set>
#include <vector>

#include <rex/cvar.h>
#include <rex/filesystem.h>
#include <rex/filesystem/devices/stfs_container_device.h>
#include <rex/logging.h>
#include <rex/runtime.h>
#include <rex/system/flags.h>
#include <rex/system/kernel_state.h>
#include <rex/system/xam/content_manager.h>
#include <rex/system/xcontent.h>

namespace col_dlc {
namespace {

// Feature flag + configurable source root, mirroring Skate 3's
// skate3_auto_install_dlc / skate3_dlc_root. The staging folder
// <user_data>/dlc is always scanned; the root adds extra folders.
REXCVAR_DEFINE_BOOL(col_auto_install_dlc, true, "DLC",
                    "Install DLC packages found in the configured DLC folders");
REXCVAR_DEFINE_STRING(col_dlc_root, "", "DLC",
                      "Extra directory to scan for DLC package files");

constexpr char kDlcDirectoryName[] = "dlc";

std::string Hex8(uint32_t value) {
  std::ostringstream stream;
  stream << std::uppercase << std::hex << std::setw(8) << std::setfill('0')
         << value;
  return stream.str();
}

// The 20-byte content ID from the STFS header (offset 0x32C), as 40
// uppercase hex digits. This is the name the installed folder must carry:
// the filename suffix of a dump is NOT the content ID (hunt 2, P1-1).
std::string ContentIdHex(const rex::filesystem::StfsHeader& header) {
  static constexpr char kHex[] = "0123456789ABCDEF";
  std::string out;
  out.reserve(40);
  for (uint8_t byte : header.header.content_id) {
    out.push_back(kHex[byte >> 4]);
    out.push_back(kHex[byte & 0xF]);
  }
  return out;
}

std::filesystem::path InstalledMarketplaceContentPath(
    const std::filesystem::path& content_root, uint32_t title_id,
    const std::filesystem::path& package_path) {
  return content_root / "0000000000000000" / Hex8(title_id) / "00000002" /
         package_path.filename();
}

std::filesystem::path InstalledMarketplaceHeaderPath(
    const std::filesystem::path& content_root, uint32_t title_id,
    const std::filesystem::path& package_path) {
  return content_root / "0000000000000000" / Hex8(title_id) / "Headers" /
         "00000002" / (package_path.filename().string() + ".header");
}

// P1-4: InstallContent has no already-installed guard of its own —
// without this, every launch would re-extract every package.
bool IsInstalledMarketplaceContent(const std::filesystem::path& content_root,
                                   uint32_t title_id,
                                   const std::filesystem::path& package_path) {
  return std::filesystem::is_directory(
             InstalledMarketplaceContentPath(content_root, title_id,
                                             package_path)) &&
         std::filesystem::is_regular_file(
             InstalledMarketplaceHeaderPath(content_root, title_id,
                                            package_path));
}

std::vector<std::filesystem::path> DiscoverDlcSourceDirectories(
    const std::filesystem::path& executable_root,
    const std::filesystem::path& game_data_root,
    const std::filesystem::path& user_data_root) {
  std::vector<std::filesystem::path> dirs;
  auto add_dir = [&](std::filesystem::path dir) {
    if (dir.empty()) {
      return;
    }
    std::error_code ec;
    dir = std::filesystem::absolute(dir, ec);
    if (ec) {
      return;
    }
    for (const auto& existing : dirs) {
      if (std::filesystem::equivalent(existing, dir, ec)) {
        return;
      }
      ec.clear();
    }
    dirs.push_back(std::move(dir));
  };

  const std::string configured_root = REXCVAR_GET(col_dlc_root);
  if (!configured_root.empty()) {
    add_dir(configured_root);
  }
  add_dir(executable_root / std::string(kDlcDirectoryName));
  add_dir(game_data_root / std::string(kDlcDirectoryName));
  add_dir(user_data_root / std::string(kDlcDirectoryName));
  return dirs;
}

}  // namespace

void InstallDlcPackages(rex::Runtime* runtime) {
  if (!REXCVAR_GET(col_auto_install_dlc) || !runtime ||
      !runtime->kernel_state() ||
      !runtime->kernel_state()->content_manager()) {
    return;
  }

  // P0-1b (defense in depth): the game checks bit 0 of the GLOBAL license
  // mask before it enumerates marketplace content, and settings.txt already
  // writes license_mask=4294967295. Set it here too so the DLCs are accepted
  // even if the settings line is ever lost. Set unconditionally (not only
  // when a package installs): already-installed DLCs are enumerated by the
  // game on later launches too.
  REXCVAR_SET(license_mask, 0xFFFFFFFF);

  const uint32_t title_id = runtime->kernel_state()->title_id();
  if (title_id == 0) {
    REXLOG_WARN("Skipping DLC install; title ID is not available");
    return;
  }

  const std::filesystem::path user_dlc_root =
      runtime->user_data_root() / std::string(kDlcDirectoryName);
  std::error_code create_ec;
  std::filesystem::create_directories(user_dlc_root, create_ec);
  if (create_ec) {
    REXLOG_WARN("Could not create DLC drop folder {}: {}",
                user_dlc_root.string(), create_ec.message());
  }

  const auto source_dirs = DiscoverDlcSourceDirectories(
      rex::filesystem::GetExecutableFolder(), runtime->game_data_root(),
      runtime->user_data_root());
  std::unordered_set<std::string> seen_packages;
  size_t installed_count = 0;
  size_t skipped_count = 0;

  for (const auto& source_dir : source_dirs) {
    if (!std::filesystem::is_directory(source_dir)) {
      continue;
    }

    std::error_code iter_ec;
    for (const auto& entry :
         std::filesystem::recursive_directory_iterator(source_dir, iter_ec)) {
      if (iter_ec) {
        REXLOG_WARN("Could not scan DLC folder {}: {}", source_dir.string(),
                    iter_ec.message());
        break;
      }
      if (!entry.is_regular_file()) {
        continue;
      }

      const auto package_path = entry.path();
      std::error_code canonical_ec;
      auto package_key =
          std::filesystem::weakly_canonical(package_path, canonical_ec)
              .string();
      if (canonical_ec) {
        package_key = std::filesystem::absolute(package_path).string();
      }
      if (!seen_packages.insert(package_key).second) {
        continue;
      }

      const auto header =
          rex::filesystem::StfsContainerDevice::ReadPackageHeader(
              package_path);
      if (!header) {
        REXLOG_WARN("Skipping DLC candidate with invalid STFS header: {}",
                    package_path.string());
        ++skipped_count;
        continue;
      }

      const auto content_type =
          static_cast<rex::system::XContentType>(header->metadata.content_type);
      if (content_type != rex::system::XContentType::kMarketplaceContent) {
        REXLOG_WARN("Skipping non-DLC content package {} with type {:08X}",
                    package_path.filename().string(),
                    static_cast<uint32_t>(content_type));
        ++skipped_count;
        continue;
      }

      const uint32_t package_title_id =
          header->metadata.execution_info.title_id;
      if (package_title_id != 0 && package_title_id != title_id) {
        REXLOG_WARN("Skipping DLC package {} for title {:08X}; running title is {:08X}",
                    package_path.filename().string(), package_title_id,
                    title_id);
        ++skipped_count;
        continue;
      }

      // P1-1: InstallContent uses filename() as the installed folder name,
      // so the staged file must be named by the STFS header content ID (40
      // uppercase hex), not by whatever the dump file was called. Copy
      // (never rename: the source may be a package the user dropped in).
      // The Java staging already names files by content ID, so this copy
      // only triggers for hand-dropped packages.
      const std::string content_id = ContentIdHex(*header);
      std::filesystem::path staged_path = package_path;
      if (package_path.filename().string() != content_id) {
        staged_path = package_path.parent_path() / content_id;
        if (!std::filesystem::is_regular_file(staged_path)) {
          std::error_code copy_ec;
          std::filesystem::copy_file(package_path, staged_path, copy_ec);
          if (copy_ec) {
            REXLOG_WARN("Could not stage DLC package {} as {}: {}",
                        package_path.string(), content_id,
                        copy_ec.message());
            ++skipped_count;
            continue;
          }
        }
        // The copy sits in the folder being scanned: mark it seen so the
        // walk does not process it a second time.
        std::error_code staged_ec;
        seen_packages.insert(
            std::filesystem::weakly_canonical(staged_path, staged_ec)
                .string());
      }

      if (IsInstalledMarketplaceContent(runtime->user_data_root(), title_id,
                                        staged_path)) {
        REXLOG_INFO("DLC package already installed: {}", content_id);
        ++skipped_count;
        continue;
      }

      REXLOG_INFO("Installing DLC package: {}", content_id);
      const auto result = runtime->kernel_state()
                              ->content_manager()
                              ->InstallContent(staged_path);
      if (result == 0) {
        ++installed_count;
        REXLOG_INFO("Installed DLC package: {}", content_id);
      } else {
        ++skipped_count;
        REXLOG_WARN("Failed to install DLC package {}: {:08X}", content_id,
                    static_cast<uint32_t>(result));
      }
    }
  }

  if (installed_count || skipped_count) {
    REXLOG_INFO("DLC scan complete: {} installed, {} skipped",
                installed_count, skipped_count);
  } else {
    REXLOG_INFO("No DLC packages found. Drop DLC package files in {}",
                user_dlc_root.string());
  }
}

}  // namespace col_dlc

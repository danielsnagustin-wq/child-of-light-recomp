// DLC installer for the Child of Light native port (M1, v1.4.2).
//
// Adapts the Skate 3 port's InstallDlcPackages
// (tools/rexauto/ports/skate3/src/skate3_app_common.cpp:1151-1254) to this
// port: it scans the configured DLC folders for STFS packages, validates
// their headers (marketplace content, matching title ID), names them by the
// STFS header content ID (P1-1), skips already-installed ones, and installs
// the rest through the XAM ContentManager. Called from
// ColDlcApp::OnPostSetup, after the runtime (kernel state, content manager)
// is fully set up.
//
// The packages themselves never live in the repo: the full build script
// injects them as APK assets (assets/dlc/<content_id>) and the Java side
// (DlcAssets) stages them to <user_data>/dlc/ before native code starts.

#pragma once

namespace rex {
class Runtime;
}  // namespace rex

namespace col_dlc {

// Installs any DLC packages found in the configured DLC folders. Safe to
// call on every launch: already-installed packages are skipped, and nothing
// happens when the folders are empty or the feature is off.
void InstallDlcPackages(rex::Runtime* runtime);

}  // namespace col_dlc

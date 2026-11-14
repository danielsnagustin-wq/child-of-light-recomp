// App registration for the Child of Light native port (M1, v1.4.2).
//
// This replaces the port's src/main.cpp in the `main` lib (see CMakeLists):
// it registers ColDlcApp — ChildOfLightApp plus the OnPostSetup DLC hook —
// under the same "childoflight" identifier android_main.cpp looks up with
// WindowedApp::GetCreator(kAppIdentifier). The port's own main.cpp is left
// untouched in autoports/ so a future `rexglue init` stays harmless.

#include "col_dlc_app.h"

#include <rex/ui/windowed_app.h>

REX_DEFINE_APP(childoflight, ColDlcApp::Create)

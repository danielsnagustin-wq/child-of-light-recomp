// App subclass that installs DLC packages after the runtime is ready
// (M1, v1.4.2).
//
// The generated port app (autoports/childoflight/port/src/childoflight_app.h)
// has no ReXApp hooks at all, so there is no place to run post-setup work.
// This subclass adds the one hook the port needs: OnPostSetup runs the DLC
// installer (col_dlc_installer) once the kernel state and content manager
// exist, before the game starts. It keeps ChildOfLightApp as its base so
// any behaviour the generated app gains later is inherited.
//
// Registration lives in col_main.cpp (REX_DEFINE_APP), compiled into the
// `main` lib instead of the port's src/main.cpp, so a future `rexglue init`
// regenerating the port directory cannot drop the hook.

#pragma once

#include <memory>

#include "childoflight_app.h"
#include "col_dlc_installer.h"

class ColDlcApp : public ChildOfLightApp {
 public:
  using ChildOfLightApp::ChildOfLightApp;

  // NOTE: the return type must be unique_ptr<WindowedApp> (the Creator
  // type), exactly like ChildOfLightApp::Create — NOT unique_ptr<ColDlcApp>.
  // A function returning unique_ptr<Derived> does NOT convert to a function
  // pointer returning unique_ptr<Base>, so REX_DEFINE_APP rejects it
  // ("no matching constructor for CreatorRegistration"). The inner
  // unique_ptr<ColDlcApp> converts to unique_ptr<WindowedApp> on return.
  static std::unique_ptr<rex::ui::WindowedApp> Create(
      rex::ui::WindowedAppContext& ctx) {
    return std::unique_ptr<ColDlcApp>(
        new ColDlcApp(ctx, "childoflight", PPCImageConfig));
  }

 protected:
  void OnPostSetup() override { col_dlc::InstallDlcPackages(runtime()); }
};

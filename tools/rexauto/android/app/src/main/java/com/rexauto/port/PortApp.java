package com.rexauto.port;

import android.app.Application;

/**
 * Application choke point: runs before any activity, on EVERY launch.
 *
 * Full builds only: opens the bundled game.stfs asset fd and hands it to
 * native code (StfsAsset.ensureOpen). This must happen here — not in
 * SetupActivity — because a file descriptor does not survive process
 * death: SetupActivity only runs on first launch, but the native side
 * needs the fd on every launch, before MainActivity starts the game.
 * In normal/dev builds the APK has no bundled asset and this is a
 * no-op (one asset listing, no native library load).
 *
 * Full builds also stage the bundled DLC packages (DlcAssets.ensureStaged,
 * M1 v1.4.2): assets/dlc/* are copied to <external>/data/dlc/ so the
 * native installer can mount them. Same rule — here, every launch.
 */
public class PortApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        StfsAsset.ensureOpen(getAssets());
        DlcAssets.ensureStaged(this);
    }
}

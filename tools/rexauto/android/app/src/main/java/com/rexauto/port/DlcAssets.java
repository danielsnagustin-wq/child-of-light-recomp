package com.rexauto.port;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stages bundled DLC packages (M1, v1.4.2) before native code starts.
 *
 * Full builds inject the 7 DLC STFS files as APK assets
 * {@code assets/dlc/<content_id>} (see tools/run-release-build-full-v141.sh;
 * the entry name is the STFS header content ID, 40 uppercase hex — NOT the
 * dump filename). This copies each one to {@code <external>/data/dlc/},
 * where the native installer (ColDlcApp::OnPostSetup) picks it up. The copy
 * runs on EVERY launch, but only when the staged file is missing or its
 * size differs from the asset: a normal launch after the first one copies
 * nothing. A failed copy never blocks the game — the native installer
 * simply skips that package and logs it.
 *
 * In normal/dev builds the APK has no {@code assets/dlc/} folder and this
 * is a no-op (one asset listing).
 */
public final class DlcAssets {
    private static final String TAG = "DlcAssets";
    private static final String ASSET_DIR = "dlc";

    private DlcAssets() { }

    public static void ensureStaged(Context ctx) {
        AssetManager assets = ctx.getAssets();
        String[] names;
        try {
            names = assets.list(ASSET_DIR);
        } catch (IOException e) {
            Log.w(TAG, "no se pudo listar los DLC: " + e);
            return;
        }
        if (names == null || names.length == 0) {
            return;
        }

        File ext = ctx.getExternalFilesDir(null);
        File base = ext != null ? ext : ctx.getFilesDir();
        File dlcDir = new File(new File(base, "data"), ASSET_DIR);
        if (!dlcDir.isDirectory() && !dlcDir.mkdirs()) {
            Log.w(TAG, "no se pudo crear " + dlcDir);
            return;
        }

        for (String name : names) {
            String assetPath = ASSET_DIR + "/" + name;
            File dest = new File(dlcDir, name);
            try {
                // Size check: skip the copy when the staged file already
                // matches the asset. openFd only works on STORED assets
                // (ours are injected STORED); if it fails, fall back to
                // copy-if-missing.
                long assetLen = -1;
                try (AssetFileDescriptor fd = assets.openFd(assetPath)) {
                    assetLen = fd.getLength();
                } catch (IOException ignored) {
                    // Compressed asset: cannot verify size, keep any copy.
                }
                if (dest.isFile() && assetLen >= 0 && dest.length() == assetLen) {
                    continue;
                }
                if (dest.isFile() && assetLen < 0) {
                    continue;
                }
                // Copy via a temp file + rename: a launch killed mid-copy
                // must not leave a truncated package behind.
                File tmp = new File(dlcDir, name + ".tmp");
                try (InputStream in = assets.open(assetPath);
                     OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                if (!tmp.renameTo(dest)) {
                    // renameTo can fail across filesystems; fall back to
                    // overwriting in place (same directory, same fs here).
                    try (InputStream in = assets.open(assetPath);
                         OutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                    tmp.delete();
                }
                Log.i(TAG, "DLC preparado: " + name);
            } catch (IOException e) {
                Log.w(TAG, "no se pudo preparar el DLC " + name + ": " + e);
            }
        }
    }
}

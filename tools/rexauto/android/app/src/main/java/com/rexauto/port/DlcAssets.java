package com.rexauto.port;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Stages bundled DLC packages (M1, v1.4.2) before native code starts.
 *
 * Full builds inject the 7 DLC STFS files as APK assets
 * {@code assets/dlc/<content_id>} (see tools/run-release-build-full-v141.sh;
 * the entry name is the STFS header content ID, 40 uppercase hex — NOT the
 * dump filename). This copies each one to {@code <external>/data/dlc/},
 * where the native installer (ColDlcApp::OnPostSetup) picks it up. The copy
 * runs on EVERY launch, but only when the staged file is missing or differs
 * from the asset: a normal launch after the first one copies nothing. A
 * failed copy never blocks the game — the native installer simply skips
 * that package and logs it.
 *
 * Verification (hunt 3, P2-2): a staged file is trusted only when its size
 * AND its MD5 match the asset. Size alone is not enough — a staged file
 * with the right size but wrong bytes (bitrot, tampering) would otherwise
 * be kept forever. The expected MD5s live in {@link #DLC_MD5} and mirror
 * DLC_TABLE in tools/run-release-build-full-v141.sh: keep the two in sync
 * when a package changes. A package whose name is not in the table is
 * verified by size only (forward-compatible with hand-added assets).
 *
 * Cost: the MD5 check reads every staged file once per launch (up to
 * 14.5 MB total, sequential read) on the UI thread — see P2-3. It is the
 * price of catching a corrupt staged copy; the copy itself still only
 * happens when a file is missing or differs.
 *
 * P2-3 (evaluated, not changed): this runs synchronously on the UI thread
 * from PortApp.onCreate, so the first launch copies up to 14.5 MB before
 * the first frame (on modern flash well under the ANR threshold, but real
 * I/O on the main thread). Moving it to a background thread was NOT done:
 * the staging must finish before the native installer scans in
 * OnPostSetup, so a background copy would need a join on the path that
 * starts the runtime (through MainActivity) to prove the ordering — that
 * plumbing is out of scope for this round. The copy cost is first-launch
 * only (later launches copy nothing); the per-launch MD5 read above is
 * the steady-state cost.
 *
 * In normal/dev builds the APK has no {@code assets/dlc/} folder and this
 * is a no-op (one asset listing).
 */
public final class DlcAssets {
    private static final String TAG = "DlcAssets";
    private static final String ASSET_DIR = "dlc";

    // Expected MD5 (lowercase hex) of each bundled DLC package, keyed by
    // asset name (= STFS header content ID). Mirrors DLC_TABLE in
    // tools/run-release-build-full-v141.sh (verified against private/dlc/
    // by binary header parse + MD5, hunt 2 DLC P1-1).
    private static final Map<String, String> DLC_MD5 = new HashMap<>();
    static {
        DLC_MD5.put("59AAD9B1F3DD5A03D8EB66466FA476847498EFF4", "a21e628bd06eed90e2529bb1ad25e676");
        DLC_MD5.put("83FEB636B64A47F8FEC9EA2A55E254DCE89D00C0", "9ec94e6183ebcc683135a4a56c1cd952");
        DLC_MD5.put("A199ACED8CEDB007C46061ABA294E05EA2E241EC", "55493377f7dfa2413df13c2072f9cde7");
        DLC_MD5.put("52BFDD521AA72DE41A97B13C656332E79C093DAB", "bb2fbd72ab816b94b24537c27727bc1d");
        DLC_MD5.put("0811DFC11EC23BE00C74477AC169DA4F77A207C8", "ed00afdffad0e25db0c902edd7bbaf4e");
        DLC_MD5.put("10E4CBDC2A9A2F3C60A44FEC0A856A423CC627E4", "94383465dcace58af95039ff22b090c8");
        DLC_MD5.put("8841A722359BB5191DF248F105C09A57CE9E2619", "4ac8b8f015aa32e284e25f6965b9310f");
    }

    private DlcAssets() { }

    /** MD5 (lowercase hex) of a file, or null if it cannot be read. */
    private static String md5Of(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            StringBuilder hex = new StringBuilder(32);
            for (byte b : md.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

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
                // copy-if-missing (see the assetLen < 0 branch below).
                long assetLen = -1;
                try (AssetFileDescriptor fd = assets.openFd(assetPath)) {
                    assetLen = fd.getLength();
                } catch (IOException ignored) {
                    // Compressed asset: cannot verify size, keep any copy.
                }
                String expectedMd5 = DLC_MD5.get(name);
                if (dest.isFile() && assetLen >= 0 && dest.length() == assetLen) {
                    // Hunt 3, P2-2: size alone is not enough. A staged
                    // file with the right size but wrong bytes is
                    // re-copied from the asset instead of kept forever.
                    // Unknown names (not in DLC_MD5) are trusted by size.
                    if (expectedMd5 == null || expectedMd5.equals(md5Of(dest))) {
                        continue;
                    }
                    Log.w(TAG, "DLC con MD5 distinto, re-copiando: " + name);
                }
                if (dest.isFile() && assetLen < 0) {
                    // Compressed asset (openFd failed): the length is
                    // unknown, so neither size nor MD5 can be checked
                    // without streaming the asset. Keep any existing copy
                    // unverified. Our builds inject STORED assets, so
                    // this path should not trigger; if a future build
                    // compressed assets/dlc/, a stale or corrupt staged
                    // copy would be kept silently.
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
                // Hunt 3, P2-2: verify the fresh copy before it becomes
                // the staged file. A bad asset (wrong bytes injected at
                // build time) must never be renamed into place.
                if (expectedMd5 != null && !expectedMd5.equals(md5Of(tmp))) {
                    tmp.delete();
                    Log.w(TAG, "DLC copiado con MD5 distinto, descartado: " + name);
                    continue;
                }
                if (!tmp.renameTo(dest)) {
                    // Hunt 3, P2-2: the old fallback re-copied overwriting
                    // in place, which can leave a truncated package if
                    // the launch dies mid-copy — the exact failure the
                    // .tmp+rename design exists to avoid. Never do that:
                    // delete both files and fail this package with a
                    // warning. The destination here is missing or known
                    // bad (a good staged file would have been skipped
                    // above), so deleting it loses nothing. The next
                    // launch retries from the asset; the game is never
                    // blocked.
                    tmp.delete();
                    dest.delete();
                    Log.w(TAG, "no se pudo instalar el DLC (rename fallido): " + name);
                    continue;
                }
                Log.i(TAG, "DLC preparado: " + name);
            } catch (IOException e) {
                Log.w(TAG, "no se pudo preparar el DLC " + name + ": " + e);
            }
        }
    }
}

package com.rexauto.port;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.InputStream;

/**
 * Full (official-port) builds: the game lives inside the APK as the
 * uncompressed asset {@code game.stfs} (STORED, never deflated — see
 * {@code build.gradle.kts} {@code noCompress += "stfs"} and the post-build
 * injection in {@code tools/run-release-build-full.sh}, which writes the
 * entry with {@code ZIP_STORED}).
 *
 * The game is NEVER copied or extracted. Instead, once per process, this
 * class opens the asset as a file descriptor and hands the triple
 * (fd, startOffset, length) to native code through JNI, where the VFS
 * worker mounts the STFS container directly over the APK file:
 *
 *   Java:  AssetManager.openFd("game.stfs") -> fd + offset + length
 *   JNI:   StfsAsset.setAssetFd(fd, startOffset, length) -> boolean
 *   C++:   stfs_asset_jni.cpp dup()s the fd and stores the triple;
 *          android_main.cpp reads it back via rexport::StfsAssetFd()
 *          (stfs_asset.h) and hands it to the runtime/VFS.
 *
 * Why the fd is opened in Java, once per process, instead of in native
 * code: the native side cannot obtain an AAssetManager by itself — it must
 * be handed the AssetManager Java object through JNI first
 * (AAssetManager_fromJava), and SDL does not pass one to native. Opening
 * the fd here keeps all Android asset plumbing in one place, and the
 * Application subclass (PortApp) guarantees it runs on EVERY launch
 * before any native code starts — a fd does not survive process death,
 * so handing it over only on first launch (SetupActivity) would leave
 * every later launch without a fd.
 *
 * Lifetime: the native side dup()s the fd at handover time, so the Java
 * copy is closed right after the JNI call and the dup'd fd stays open
 * for the whole process. Never close it from Java afterwards.
 */
public final class StfsAsset {
    private static final String TAG = "StfsAsset";

    /** Asset name inside the APK (also the game_root.txt marker payload). */
    public static final String ASSET_NAME = "game.stfs";

    private static volatile boolean handedOver;

    private StfsAsset() { }

    /**
     * Preflight: the asset exists, opens as a file descriptor (which only
     * works for STORED/uncompressed assets — a compressed asset throws
     * here), has a non-zero length, and looks like an Xbox 360 STFS
     * package (CON / LIVE / PIRS magic) for the title this APK was built
     * for. The title-ID check mirrors the old import gate
     * (SetupActivity.finish): a wrong game is refused before launch.
     * Pure Java, no native call.
     */
    public static boolean verifyUsable(AssetManager am, String wantTitleId) {
        AssetFileDescriptor afd = null;
        try {
            afd = am.openFd(ASSET_NAME);
            if (afd.getLength() <= 0) return false;
            byte[] head = new byte[0x400];
            int got = 0;
            try (InputStream in = afd.createInputStream()) {
                int n;
                while (got < head.length && (n = in.read(head, got, head.length - got)) > 0) {
                    got += n;
                }
            }
            if (got < 0x364) return false;
            String magic = new String(head, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!magic.equals("CON ") && !magic.equals("LIVE") && !magic.equals("PIRS")) {
                return false;
            }
            if (wantTitleId != null && !wantTitleId.isEmpty() && !wantTitleId.equals("00000000")) {
                int id = ((head[0x360] & 0xFF) << 24) | ((head[0x361] & 0xFF) << 16)
                        | ((head[0x362] & 0xFF) << 8) | (head[0x363] & 0xFF);
                String gotId = String.format("%08X", id);
                if (!gotId.equalsIgnoreCase(wantTitleId)) {
                    Log.w(TAG, "bundled game is " + gotId + ", this APK is for " + wantTitleId);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (afd != null) {
                try { afd.close(); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * Opens the bundled asset fd and hands (fd, startOffset, length) to
     * native code. Idempotent per process: a second call is a no-op that
     * returns the first result. Returns false when there is no bundled
     * asset (normal/dev builds), when the asset cannot be opened as an fd
     * (compressed or missing), or when the native side could not store
     * it. Never throws.
     *
     * The native library is loaded here on purpose: this runs from
     * PortApp.onCreate, before any activity — and therefore before SDL
     * loads libmain — so the JNI method would otherwise be unlinked.
     * The load is skipped entirely when the APK has no bundled asset.
     */
    public static boolean ensureOpen(AssetManager am) {
        if (handedOver) return true;
        try {
            boolean found = false;
            for (String n : am.list("")) {
                if (ASSET_NAME.equals(n)) { found = true; break; }
            }
            if (!found) return false;
        } catch (Exception e) {
            return false;
        }
        AssetFileDescriptor afd = null;
        try {
            afd = am.openFd(ASSET_NAME);
            if (afd.getLength() <= 0) return false;
            ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();
            if (pfd == null) return false;
            System.loadLibrary("main");
            handedOver = setAssetFd(pfd.getFd(), afd.getStartOffset(), afd.getLength());
            return handedOver;
        } catch (Throwable e) {
            Log.w(TAG, "cannot hand bundled asset fd to native", e);
            return false;
        } finally {
            if (afd != null) {
                try { afd.close(); } catch (Exception ignored) { }
            }
        }
    }

    /** True once this process handed the asset fd to native code. */
    public static boolean isHandedOver() {
        return handedOver;
    }

    /**
     * JNI, implemented in app/src/main/cpp/stfs_asset_jni.cpp (libmain.so).
     * Receives the fd of the whole APK file plus the byte range where the
     * STORED game.stfs asset lives inside it. The native side dup()s the
     * fd immediately, so the Java copy may be closed right after this
     * returns. Returns true when the triple was stored and is available
     * to native code via rexport::StfsAssetFd().
     */
    private static native boolean setAssetFd(int fd, long startOffset, long length);
}

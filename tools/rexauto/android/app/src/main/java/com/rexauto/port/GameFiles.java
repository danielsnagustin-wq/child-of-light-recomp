package com.rexauto.port;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Where the game lives, what this APK was built for, and how to tell them apart. */
public final class GameFiles {
    private GameFiles() { }

    /**
     * Marker written as the first line of game_root.txt by full (official-
     * port) builds: the game is NOT extracted anywhere — it is read from
     * the game.stfs asset inside the APK (see StfsAsset). The native side
     * reads the same marker from game_root.txt. Format: {@code asset:<name>}.
     */
    public static final String ASSET_PREFIX = "asset:";

    /** Folder the app extracts into / reads from: Android/data/<pkg>/files/game */
    public static File gameDir(Context ctx) {
        File ext = ctx.getExternalFilesDir(null);
        return new File(ext != null ? ext : ctx.getFilesDir(), "game");
    }

    public static File configFile(Context ctx) {
        File ext = ctx.getExternalFilesDir(null);
        return new File(ext != null ? ext : ctx.getFilesDir(), "game_root.txt");
    }

    public static File settingsFile(Context ctx) {
        File ext = ctx.getExternalFilesDir(null);
        return new File(ext != null ? ext : ctx.getFilesDir(), "settings.txt");
    }

    public static String configuredGameRoot(Context ctx) {
        try {
            File config = configFile(ctx);
            if (!config.isFile()) return null;
            byte[] bytes = new byte[(int) Math.min(config.length(), 8192)];
            try (FileInputStream in = new FileInputStream(config)) {
                if (in.read(bytes) <= 0) return null;
            }
            String line = new String(bytes).split("\n", 2)[0].trim();
            return line.isEmpty() ? null : line;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasValidGameRoot(Context ctx) {
        String root = configuredGameRoot(ctx);
        if (root == null) return false;
        if (root.startsWith(ASSET_PREFIX)) {
            // Full build: the game lives inside the APK as an asset, not in
            // a folder. Valid when the named asset is actually in the APK.
            String name = root.substring(ASSET_PREFIX.length()).trim();
            if (name.isEmpty()) return false;
            try {
                for (String n : ctx.getAssets().list("")) {
                    if (name.equals(n)) return true;
                }
            } catch (Exception ignored) {
            }
            return false;
        }
        return new File(root, "default.xex").isFile();
    }

    /** True when game_root.txt points at a bundled asset instead of a folder. */
    public static boolean isAssetRoot(Context ctx) {
        String root = configuredGameRoot(ctx);
        return root != null && root.startsWith(ASSET_PREFIX);
    }

    /** Asset name from an asset game root ({@code asset:game.stfs} -> {@code game.stfs}), or null. */
    public static String assetName(Context ctx) {
        String root = configuredGameRoot(ctx);
        if (root == null || !root.startsWith(ASSET_PREFIX)) return null;
        String name = root.substring(ASSET_PREFIX.length()).trim();
        return name.isEmpty() ? null : name;
    }

    /** Title ID (8 hex chars) from a XEX2 header, or null. Mirrors extract.py. */
    public static String xexTitleId(InputStream in) {
        try {
            byte[] head = new byte[0x4000];
            int got = 0, n;
            while (got < head.length && (n = in.read(head, got, head.length - got)) > 0) got += n;
            if (got < 0x18 || head[0] != 'X' || head[1] != 'E' || head[2] != 'X' || head[3] != '2') return null;
            ByteBuffer b = ByteBuffer.wrap(head, 0, got).order(ByteOrder.BIG_ENDIAN);
            int cnt = b.getInt(0x14);
            if (cnt < 0 || cnt > 4096) return null;
            for (int i = 0; i < cnt && 0x18 + i * 8 + 8 <= got; i++) {
                int key = b.getInt(0x18 + i * 8), val = b.getInt(0x1C + i * 8);
                if (key == 0x00040006 && val >= 0 && val + 0x10 <= got) {
                    return String.format("%08X", b.getInt(val + 0x0C));
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static String xexTitleId(File xex) {
        try (FileInputStream in = new FileInputStream(xex)) {
            return xexTitleId(in);
        } catch (IOException e) {
            return null;
        }
    }
}

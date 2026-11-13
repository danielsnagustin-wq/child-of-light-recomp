package com.rexauto.port;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * First-run router + minimal import screen. Official-port UX (v1.4): tapping
 * the icon goes straight to the game — this activity has NO home anymore,
 * and in full builds it shows NO screen at all.
 *
 *   - Game already installed -> launchGame() at once, no UI at all.
 *   - Full build, first run -> write the asset marker (game.stfs lives
 *     inside the APK, read in place by the VFS — nothing is copied or
 *     extracted, ever) and launch. No picker, no progress screen.
 *   - Normal build, first run -> one minimal screen: pick the game once.
 *     The normal build is a development variant only.
 *
 * Knows exactly which game this APK was recompiled for (title + title ID
 * baked in at build time via BuildConfig), so it can refuse the wrong disc.
 * Takes an ISO, a bare default.xex or an already-extracted folder, unpacks
 * into the app's own storage (no permissions needed). Graphics, GPU driver,
 * logs and reset moved to the hidden in-game menu (gear on the virtual
 * gamepad, see GraphicsDialog / LogsDialog / PadSettingsDialog).
 */
public class SetupActivity extends Activity {
    private static final int REQ_PICK_FILE = 1;
    private static final int REQ_PICK_TREE = 2;

    private TextView status;
    private ProgressBar progress;
    private Button pickBtn, pickDirBtn;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean busy;

    // --- palette: aurora boreal sobre negro AMOLED -------------------------
    // Azul hielo #8CCBEB, azul lavanda #6578B8, lavanda #A99ACD,
    // turquesa #75D6CE, plata #DCE8F5, fondo #10131D. Nada de amarillo/dorado.
    private static final int COL_BG = 0xFF10131D;
    private static final int COL_LAVENDER = 0xFFA99ACD;
    private static final int COL_TURQUOISE = 0xFF75D6CE;
    private static final int COL_SILVER = 0xFFDCE8F5;
    private static final int COL_MUTED = 0xFFB9C4D6;
    private static final int COL_ERROR = 0xFFE8A0B0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Crash report first: the native side wipes crash.txt on a fresh
        // launch, so it must be shown (and renamed away) before the game
        // starts. When shown, the router continues on dismiss.
        if (LogsDialog.showCrashReportIfAny(this, this::continueStart)) return;
        continueStart();
    }

    /** Router: straight to the game when installed, else the import screen. */
    private void continueStart() {
        if (GameFiles.hasValidGameRoot(this)) {
            launchGame();
            finish();
            return;
        }
        if (BuildConfig.FULL && launchBundledAsset()) {
            // Full build with a usable bundled asset: marker written, game
            // launched, no UI shown at all. Nothing is copied or imported.
            finish();
            return;
        }
        // Normal/dev build, or a full build whose asset is missing or for
        // the wrong title (broken build): fall back to the manual picker.
        buildFirstRunScreen();
    }

    /**
     * Full builds: the game ships inside the APK as the game.stfs asset and
     * is read in place by the VFS (StfsAsset hands its fd to native code in
     * PortApp.onCreate). All this does is verify the asset is really a
     * usable STFS package for this title, write the {@code asset:game.stfs}
     * marker into game_root.txt, and launch the game. Returns false when
     * there is no usable bundled asset — the caller falls back to the
     * manual import screen.
     */
    private boolean launchBundledAsset() {
        if (!StfsAsset.verifyUsable(getAssets(), BuildConfig.TITLE_ID)) return false;
        try (PrintWriter w = new PrintWriter(new FileWriter(GameFiles.configFile(this), false))) {
            w.println(GameFiles.ASSET_PREFIX + StfsAsset.ASSET_NAME);
        } catch (Exception e) {
            return false;
        }
        launchGame();
        return true;
    }

    /** The only screen this activity ever shows: pick the game, once. */
    private void buildFirstRunScreen() {
        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, (int) (12 * dp), pad, pad);
        root.setBackgroundColor(COL_BG);

        TextView title = new TextView(this);
        title.setText(BuildConfig.GAME_TITLE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView edition = new TextView(this);
        edition.setText(getString(R.string.edition, BuildConfig.TITLE_ID));
        edition.setTextSize(13);
        edition.setTypeface(Typeface.DEFAULT_BOLD);
        edition.setTextColor(COL_SILVER);
        edition.setPadding(0, (int) (4 * dp), 0, (int) (12 * dp));
        root.addView(edition);

        TextView help = new TextView(this);
        help.setText(getString(R.string.first_run_help, BuildConfig.GAME_TITLE));
        help.setTextSize(14);
        help.setTextColor(COL_MUTED);
        help.setPadding(0, 0, 0, (int) (16 * dp));
        root.addView(help);

        pickBtn = button(root, R.string.pick_iso, v -> pickFile());
        pickDirBtn = button(root, R.string.pick_folder, v -> pickFolder());

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(COL_LAVENDER);
        status.setPadding(0, (int) (16 * dp), 0, 0);
        root.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(COL_TURQUOISE));
        progress.setIndeterminateTintList(ColorStateList.valueOf(COL_TURQUOISE));
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progLp.topMargin = (int) (10 * dp);
        progress.setLayoutParams(progLp);
        root.addView(progress);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(COL_BG);
        sv.addView(root);
        setContentView(sv);
    }

    /** Boton secundario: superficie oscura, borde azul lavanda, texto plata. */
    private Button button(LinearLayout parent, int text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(COL_SILVER);
        b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.btn_secondary);
        b.setOnClickListener(l);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (4 * dp);
        lp.bottomMargin = (int) (4 * dp);
        b.setLayoutParams(lp);
        parent.addView(b);
        return b;
    }

    // --- input pickers ------------------------------------------------------
    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-iso9660-image", "*/*"});
        startActivityForResult(i, REQ_PICK_FILE);
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_PICK_TREE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_PICK_FILE) importFile(uri);
        else if (req == REQ_PICK_TREE) importTree(uri);
    }

    private void setBusy(boolean b) {
        busy = b;
        pickBtn.setEnabled(!b);
        pickDirBtn.setEnabled(!b);
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(true);
    }

    private void post(String s) { ui.post(() -> status.setText(s)); }

    private void postProgress(String s, long done, long total) {
        ui.post(() -> {
            progress.setIndeterminate(total <= 0);
            if (total > 0) progress.setProgress((int) (done * 1000 / total));
            status.setText(String.format(Locale.US, "%s  %d / %d MB", s, done >> 20, total >> 20));
        });
    }

    /** ISO or bare default.xex picked with the document picker. */
    private void importFile(Uri uri) {
        setBusy(true);
        String name = displayName(uri);
        post(getString(R.string.status_reading, name));
        new Thread(() -> {
            File dest = GameFiles.gameDir(this);
            try {
                IsoExtractor iso = IsoExtractor.open(getContentResolver(), uri);
                File xex;
                if (iso != null) {
                    dest.mkdirs();
                    xex = iso.extractAll(dest, this::postProgress);
                    iso.close();
                    if (xex == null) throw new Exception(getString(R.string.err_no_xex_in_iso));
                } else if ((xex = importPackage(uri, name, dest)) != null) {
                    // CON/LIVE/PIRS handled
                } else {
                    // not a disc: accept a raw default.xex
                    try (InputStream probe = getContentResolver().openInputStream(uri)) {
                        if (GameFiles.xexTitleId(probe) == null)
                            throw new Exception(getString(R.string.err_not_iso, name));
                    }
                    xex = new File(dest, "default.xex");
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        IsoExtractor.copyStream(in, xex);
                    }
                }
                finish(xex);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }, "import").start();
    }

    /**
     * CON / LIVE / PIRS picked as a single file: STFS (XBLA, arcade titles) is
     * unpacked entry by entry; a single-file Games-on-Demand image goes through
     * the GDFX path. A multi-part GoD needs its ".data" folder, so the user is
     * pointed at the folder picker. Returns null when the file is not a package.
     */
    private File importPackage(Uri uri, String name, File dest) throws Exception {
        java.nio.ByteBuffer hdr;
        android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) return null;
        java.nio.channels.FileChannel ch = new java.io.FileInputStream(pfd.getFileDescriptor()).getChannel();
        hdr = StfsExtractor.readHeader(ch);
        if (hdr == null) { ch.close(); pfd.close(); return null; }
        dest.mkdirs();
        if (StfsExtractor.volumeType(hdr) == StfsExtractor.VOLUME_SVOD) {
            ch.close(); pfd.close();
            if (hdr.getInt(0x39D) > 1)
                throw new Exception(getString(R.string.err_god_needs_folder, name));
            StfsExtractor.Found f = new StfsExtractor.Found(uri, name, hdr, null);
            post(getString(R.string.status_reading, name + " (Games on Demand)"));
            IsoExtractor god = StfsExtractor.openSvod(getContentResolver(), f, null);
            try {
                File xex = god.extractAll(dest, this::postProgress);
                if (xex == null) throw new Exception(getString(R.string.err_no_xex_in_iso));
                return xex;
            } finally { god.close(); }
        }
        post(getString(R.string.status_reading, name + " (STFS)"));
        try {
            File xex = new StfsExtractor.Stfs(ch, hdr).extractAll(dest, this::postProgress);
            if (xex == null) throw new Exception(getString(R.string.err_no_xex_in_stfs, name));
            return xex;
        } finally { ch.close(); pfd.close(); }
    }

    /** Already-extracted folder picked with the tree picker: copy it in. */
    private void importTree(Uri tree) {
        setBusy(true);
        post(getString(R.string.status_copying));
        new Thread(() -> {
            File dest = GameFiles.gameDir(this);
            try {
                dest.mkdirs();
                File xex;
                StfsExtractor.Found pkg = StfsExtractor.findInTree(getContentResolver(), tree, DocumentsContract.getTreeDocumentId(tree), 0);
                if (pkg != null && pkg.volumeType() == StfsExtractor.VOLUME_SVOD) {
                    // Games on Demand dump: <id>/<header> + <header>.data/Data0000..
                    post(getString(R.string.status_reading, pkg.name + " (Games on Demand)"));
                    IsoExtractor god = StfsExtractor.openSvod(getContentResolver(), pkg, tree);
                    try { xex = god.extractAll(dest, this::postProgress); } finally { god.close(); }
                    if (xex == null) throw new Exception(getString(R.string.err_no_xex_in_iso));
                } else if (pkg != null) {
                    post(getString(R.string.status_reading, pkg.name + " (STFS)"));
                    android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(pkg.headerDoc, "r");
                    java.nio.channels.FileChannel ch = new java.io.FileInputStream(pfd.getFileDescriptor()).getChannel();
                    try { xex = new StfsExtractor.Stfs(ch, pkg.hdr).extractAll(dest, this::postProgress); }
                    finally { ch.close(); pfd.close(); }
                    if (xex == null) throw new Exception(getString(R.string.err_no_xex_in_stfs, pkg.name));
                } else {
                    long[] counter = {0};
                    copyTree(tree, DocumentsContract.getTreeDocumentId(tree), dest, counter, 0);
                    xex = findXex(dest, 0);
                }
                if (xex == null) throw new Exception(getString(R.string.err_no_xex_in_folder));
                finish(xex);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }, "import-tree").start();
    }

    private void copyTree(Uri tree, String docId, File dest, long[] counter, int depth) throws Exception {
        if (depth > 12) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        List<String[]> rows = new ArrayList<>();
        try (Cursor c = getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (c != null && c.moveToNext()) rows.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
        }
        for (String[] r : rows) {
            String name = r[1] == null ? "file" : r[1].replace("/", "_");
            File out = new File(dest, name);
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(r[2])) {
                out.mkdirs();
                copyTree(tree, r[0], out, counter, depth + 1);
            } else {
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, r[0]);
                try (InputStream in = getContentResolver().openInputStream(doc)) {
                    IsoExtractor.copyStream(in, out);
                }
                counter[0]++;
                post(getString(R.string.status_copied, counter[0], name));
            }
        }
    }

    private static File findXex(File dir, int depth) {
        if (depth > 4) return null;
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File f : kids) if (f.isFile() && f.getName().equalsIgnoreCase("default.xex")) return f;
        for (File f : kids) if (f.isDirectory()) { File x = findXex(f, depth + 1); if (x != null) return x; }
        return null;
    }

    /**
     * Title-ID gate + write game_root.txt, then straight into the game.
     * Official-port UX: no "installed" screen, no JUGAR button — the game
     * starts right away. Called from import threads, so the launch runs on
     * the UI thread.
     */
    private void finish(File xex) throws Exception {
        String got = GameFiles.xexTitleId(xex);
        String want = BuildConfig.TITLE_ID;
        if (want != null && !want.isEmpty() && !want.equals("00000000") && got != null && !got.equalsIgnoreCase(want)) {
            String other = TitleDb.lookup(this, got);
            throw new Exception(getString(R.string.err_wrong_game,
                    other != null ? other : ("Title ID " + got), BuildConfig.GAME_TITLE, want));
        }
        File root = xex.getParentFile();
        try (PrintWriter w = new PrintWriter(new FileWriter(GameFiles.configFile(this), false))) {
            w.println(root.getAbsolutePath());
        }
        new GraphicsSettings(this).write(this);
        ui.post(() -> {
            setBusy(false);
            launchGame();
            finish();
        });
    }

    private void fail(String msg) {
        ui.post(() -> {
            setBusy(false);
            status.setText(msg == null ? getString(R.string.err_generic) : msg);
            status.setTextColor(COL_ERROR);
        });
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return uri.getLastPathSegment();
    }

    private void launchGame() {
        try { new GraphicsSettings(this).write(this); } catch (Exception ignored) { }
        startActivity(new Intent(this, MainActivity.class));
    }
}

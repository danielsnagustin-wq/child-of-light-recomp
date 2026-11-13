package com.rexauto.port;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Log viewer + crash report. Moved verbatim from SetupActivity (v1.2).
 * In v1.3 the launcher home is gone, so the full log viewer lives in the
 * hidden in-game menu (the gear on the virtual gamepad) and the crash report
 * is shown by the first-run router before the game starts — never on the way
 * into the game.
 */
public final class LogsDialog {
    private LogsDialog() {
    }

    private static File logsDir(Activity activity) {
        File ext = activity.getExternalFilesDir(null);
        return new File(ext, "logs");
    }

    private static File crashFile(Activity activity) { return new File(logsDir(activity), "crash.txt"); }

    private static File gameLog(Activity activity) { return new File(logsDir(activity), BuildConfig.PROJECT + ".log"); }

    private static String tail(File f, int maxChars) {
        if (f == null || !f.isFile()) return "";
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            long len = raf.length();
            long from = Math.max(0, len - maxChars);
            raf.seek(from);
            byte[] buf = new byte[(int) (len - from)];
            raf.readFully(buf);
            return new String(buf, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * libmain.so writes logs/crash.txt from its signal handler; show it once.
     * Returns true when a report was shown (the caller must wait for the
     * dialog: onDismiss runs after the report is renamed away).
     */
    public static boolean showCrashReportIfAny(Activity activity, Runnable onDismiss) {
        File c = crashFile(activity);
        if (!c.isFile()) return false;
        String report = tail(c, 6000);
        String log = tail(gameLog(activity), 3000);
        String text = report + "\n--- últimas líneas del registro ---\n" + log;
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int p = (int) (12 * activity.getResources().getDisplayMetrics().density);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(activity);
        sv.addView(tv);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.crash_title)
                .setView(sv)
                .setPositiveButton(R.string.share, (d, w) -> shareText(activity, activity.getString(R.string.crash_title), text))
                .setNeutralButton(R.string.copy, (d, w) -> copyText(activity, text))
                .setNegativeButton(android.R.string.ok, null)
                .setOnDismissListener(d -> {
                    c.renameTo(new File(logsDir(activity), "crash.prev.txt"));
                    if (onDismiss != null) onDismiss.run();
                })
                .show();
        return true;
    }

    public static void show(Activity activity) {
        String prev = tail(new File(logsDir(activity), "crash.prev.txt"), 4000);
        String cur = tail(crashFile(activity), 4000);
        String log = tail(gameLog(activity), 6000);
        String prof = tail(new File(logsDir(activity), "profile.txt"), 5000);
        String text = (cur.isEmpty() ? prev : cur);
        if (!text.isEmpty()) text = "--- último fallo ---\n" + text + "\n";
        if (!prof.isEmpty()) text += "--- perfil (dónde va el tiempo de CPU) ---\n" + prof + "\n";
        text += "--- " + BuildConfig.PROJECT + ".log (final) ---\n" + (log.isEmpty() ? activity.getString(R.string.log_empty) : log);
        final String all = text;
        TextView tv = new TextView(activity);
        tv.setText(all);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int p = (int) (12 * activity.getResources().getDisplayMetrics().density);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(activity);
        sv.addView(tv);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.logs)
                .setView(sv)
                .setPositiveButton(R.string.share, (d, w) -> shareText(activity, BuildConfig.PROJECT + " log", all))
                .setNeutralButton(R.string.copy, (d, w) -> copyText(activity, all))
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }

    private static void shareText(Activity activity, String subject, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, text);
        try { activity.startActivity(Intent.createChooser(i, subject)); } catch (Exception e) { copyText(activity, text); }
    }

    private static void copyText(Activity activity, String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("log", text));
        Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show();
    }
}

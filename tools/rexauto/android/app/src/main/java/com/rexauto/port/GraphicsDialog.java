package com.rexauto.port;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Hidden settings, reachable only from the in-game menu (the gear on the
 * virtual gamepad). The launcher home is gone in v1.3 (official-port UX),
 * so graphics, GPU driver, logs and reset live here instead — nothing
 * settings-like shows on the way into the game.
 *
 * Moved verbatim from SetupActivity (v1.2) except for the Activity parameter:
 * these are static so any activity can open them. Changes apply on the next
 * launch (the runtime reads settings.txt once, at startup).
 */
public final class GraphicsDialog {
    /** Request code for the driver-zip picker; MainActivity forwards it here. */
    public static final int REQ_PICK_DRIVER = 3;

    private GraphicsDialog() {
    }

    public static void show(Activity activity) {
        GraphicsSettings gs = new GraphicsSettings(activity);
        float dp = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * dp);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);

        label(activity, box, R.string.gfx_preset);
        Spinner preset = new Spinner(activity);
        String[] presets = {"performance", "balanced", "accuracy"};
        preset.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{activity.getString(R.string.preset_performance), activity.getString(R.string.preset_balanced), activity.getString(R.string.preset_accuracy)}));
        for (int i = 0; i < presets.length; i++) if (presets[i].equals(gs.preset())) preset.setSelection(i);
        box.addView(preset);

        label(activity, box, R.string.gfx_fps_cap);
        Spinner fps = new Spinner(activity);
        int[] caps = {0, 30, 60};
        fps.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{activity.getString(R.string.fps_off), activity.getString(R.string.fps_30), activity.getString(R.string.fps_60)}));
        for (int i = 0; i < caps.length; i++) if (caps[i] == gs.fpsCap()) fps.setSelection(i);
        box.addView(fps);

        label(activity, box, R.string.gfx_scale);
        Spinner scale = new Spinner(activity);
        scale.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                activity.getResources().getStringArray(R.array.gfx_scale_options)));
        scale.setSelection(gs.resolutionScale() - 1);
        box.addView(scale);

        label(activity, box, R.string.gfx_effect);
        Spinner effect = new Spinner(activity);
        String[] effects = {"bilinear", "cas", "fsr"};
        effect.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                activity.getResources().getStringArray(R.array.gfx_effect_options)));
        for (int i = 0; i < effects.length; i++) if (effects[i].equals(gs.presentEffect())) effect.setSelection(i);
        box.addView(effect);

        label(activity, box, R.string.gfx_video_mode);
        Spinner mode = new Spinner(activity);
        int[][] modes = {{1280, 720}, {1920, 1080}, {1024, 576}, {960, 540}};
        mode.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                activity.getResources().getStringArray(R.array.gfx_mode_options)));
        for (int i = 0; i < modes.length; i++) if (modes[i][0] == gs.videoWidth()) mode.setSelection(i);
        box.addView(mode);

        label(activity, box, R.string.gfx_orientation);
        Spinner orient = new Spinner(activity);
        String[] orients = {"landscape", "portrait", "auto"};
        orient.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{activity.getString(R.string.orient_landscape), activity.getString(R.string.orient_portrait), activity.getString(R.string.orient_auto)}));
        for (int i = 0; i < orients.length; i++) if (orients[i].equals(gs.orientation())) orient.setSelection(i);
        box.addView(orient);

        CheckBox vsync = new CheckBox(activity);
        vsync.setText(R.string.gfx_vsync);
        vsync.setChecked(gs.vsync());
        box.addView(vsync);

        CheckBox letterbox = new CheckBox(activity);
        letterbox.setText(R.string.gfx_letterbox);
        letterbox.setChecked(gs.letterbox());
        box.addView(letterbox);

        CheckBox tolerant = new CheckBox(activity);
        tolerant.setText(R.string.gfx_tolerant);
        tolerant.setChecked(gs.tolerant());
        box.addView(tolerant);

        label(activity, box, R.string.gfx_extra);
        EditText extra = new EditText(activity);
        extra.setHint("key=value");
        extra.setText(gs.extra());
        extra.setMinLines(2);
        box.addView(extra);

        ScrollView sv = new ScrollView(activity);
        sv.addView(box);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.graphics)
                .setView(sv)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    gs.setPreset(presets[preset.getSelectedItemPosition()]);
                    gs.setFpsCap(caps[fps.getSelectedItemPosition()]);
                    gs.setResolutionScale(scale.getSelectedItemPosition() + 1);
                    gs.setPresentEffect(effects[effect.getSelectedItemPosition()]);
                    int[] m = modes[mode.getSelectedItemPosition()];
                    gs.setVideoMode(m[0], m[1]);
                    gs.setOrientation(orients[orient.getSelectedItemPosition()]);
                    gs.setVsync(vsync.isChecked());
                    gs.setLetterbox(letterbox.isChecked());
                    gs.setTolerant(tolerant.isChecked());
                    gs.setExtra(extra.getText().toString());
                    try { gs.write(activity); } catch (Exception e) { Toast.makeText(activity, e.toString(), Toast.LENGTH_LONG).show(); }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void label(Activity activity, LinearLayout parent, int text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFDCE8F5);
        t.setPadding(0, 12, 0, 4);
        parent.addView(t);
    }

    // --- custom GPU driver (Turnip, Adreno only) ----------------------------

    public static void showDriverDialog(Activity activity) {
        GpuDriver gd = new GpuDriver(activity);
        List<GpuDriver.Info> list = gd.installed();
        String[] names = new String[list.size() + 1];
        names[0] = activity.getString(R.string.gpu_driver_system);
        int checked = 0;
        for (int i = 0; i < list.size(); i++) {
            GpuDriver.Info d = list.get(i);
            names[i + 1] = d.name + (d.version.isEmpty() ? "" : "  (" + d.version + ")");
            if (d.slug.equals(gd.selected())) checked = i + 1;
        }
        final int[] pick = {checked};
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(R.string.gpu_driver)
                .setSingleChoiceItems(names, checked, (d, w) -> pick[0] = w)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        gd.select(pick[0] == 0 ? null : list.get(pick[0] - 1).slug);
                        Toast.makeText(activity, pick[0] == 0 ? activity.getString(R.string.gpu_driver_system) : list.get(pick[0] - 1).name, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(activity, e.toString(), Toast.LENGTH_LONG).show(); }
                })
                .setNeutralButton(R.string.gpu_driver_import, (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
                    activity.startActivityForResult(i, REQ_PICK_DRIVER);
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (!GpuDriver.isAdreno()) {
            b.setMessage(R.string.gpu_driver_not_adreno);
        }
        AlertDialog dlg = b.create();
        dlg.getListView().setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos == 0) return true;
            GpuDriver.Info d = list.get(pos - 1);
            new AlertDialog.Builder(activity).setMessage(activity.getString(R.string.gpu_driver_remove, d.name))
                    .setPositiveButton(android.R.string.ok, (dd, ww) -> {
                        try { gd.remove(d.slug); } catch (Exception e) { Toast.makeText(activity, e.toString(), Toast.LENGTH_LONG).show(); }
                        dlg.dismiss();
                        showDriverDialog(activity);
                    }).setNegativeButton(android.R.string.cancel, null).show();
            return true;
        });
        dlg.show();
    }

    /** Called by the host activity's onActivityResult for REQ_PICK_DRIVER. */
    public static void onDriverPicked(Activity activity, Intent data) {
        if (data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            try {
                GpuDriver gd = new GpuDriver(activity);
                String name = uri.getLastPathSegment();
                GpuDriver.Info info = gd.importZip(activity.getContentResolver(), uri, name);
                gd.select(info.slug);
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, activity.getString(R.string.gpu_driver_installed, info.name), Toast.LENGTH_LONG).show();
                    showDriverDialog(activity);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, e.getMessage() == null ? activity.getString(R.string.err_generic) : e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "import-driver").start();
    }
}

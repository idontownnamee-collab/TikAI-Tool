package com.tiktokaitool;

import android.animation.ObjectAnimator;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_REQ = 1001;
    private EditText etUrl;
    private TextView tvStatus, tvApiWarn;
    private CardView cardOverlay;
    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs     = new PrefsManager(this);
        etUrl     = findViewById(R.id.et_url);
        tvStatus  = findViewById(R.id.tv_status);
        tvApiWarn = findViewById(R.id.tv_api_warn);
        cardOverlay = findViewById(R.id.card_overlay);

        animateCards();

        findViewById(R.id.btn_open_tiktok).setOnClickListener(v -> { animBtn(v); openTikTok(); });
        findViewById(R.id.btn_ai_finder).setOnClickListener(v -> { animBtn(v); startAiFinder(); });
        findViewById(R.id.btn_paste).setOnClickListener(v -> pasteUrl());
        findViewById(R.id.btn_download).setOnClickListener(v -> { animBtn(v); startDownload(); });
        findViewById(R.id.btn_stop_overlay).setOnClickListener(v -> stopOverlay());
        findViewById(R.id.btn_history).setOnClickListener(v ->
            startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.tv_api_warn).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void animateCards() {
        int[] ids = {R.id.card_header, R.id.card_actions, R.id.card_download, R.id.card_bottom};
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            if (v == null) continue;
            v.setAlpha(0f); v.setTranslationY(60f);
            v.animate().alpha(1f).translationY(0f)
                .setDuration(400).setStartDelay(i * 80L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        }
    }

    private void animBtn(View v) {
        v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80)
            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                .setDuration(180).setInterpolator(new OvershootInterpolator()).start())
            .start();
    }

    private void openTikTok() {
        String[] pkgs = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};
        for (String pkg : pkgs) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) { startActivity(i); return; }
            } catch (Exception ignored) {}
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.zhiliaoapp.musically"))); }
        catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically"))); }
    }

    private void startAiFinder() {
        if (prefs.getApiKey().isEmpty()) {
            Toast.makeText(this, "Set Claude API key in Settings first!", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class)); return;
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Overlay Permission Needed")
                .setMessage("TikAI needs to draw over other apps to show the FIND button while you browse TikTok.")
                .setPositiveButton("Grant", (d, w) -> startActivityForResult(
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), OVERLAY_REQ))
                .setNegativeButton("Cancel", null).show();
            return;
        }
        launchOverlayAndTikTok();
    }

    private void launchOverlayAndTikTok() {
        Intent si = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si);
        else startService(si);
        new Handler().postDelayed(this::openTikTok, 500);
        Toast.makeText(this, "FIND button active! Drag it anywhere.", Toast.LENGTH_LONG).show();
        updateStatus();
    }

    private void stopOverlay() {
        Intent i = new Intent(this, OverlayService.class);
        i.setAction("STOP"); startService(i);
        new Handler().postDelayed(this::updateStatus, 400);
    }

    private void pasteUrl() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
            CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
            if (t != null && (t.toString().contains("tiktok.com") || t.toString().contains("vm.tiktok"))) {
                etUrl.setText(t.toString());
                Toast.makeText(this, "✓ Pasted", Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, "No TikTok URL in clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownload() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { Toast.makeText(this, "Paste a TikTok URL first", Toast.LENGTH_SHORT).show(); return; }
        if (!url.contains("tiktok") ) { Toast.makeText(this, "Not a valid TikTok URL", Toast.LENGTH_SHORT).show(); return; }
        startActivity(new Intent(this, DownloadsActivity.class).putExtra("url", url));
    }

    private void updateStatus() {
        boolean active = OverlayService.isRunning;
        tvStatus.setText(active ? "● AI Overlay Active" : "● AI Overlay Off");
        tvStatus.setTextColor(getResources().getColor(active ? R.color.green : R.color.text_secondary));
        findViewById(R.id.btn_stop_overlay).setVisibility(active ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        tvApiWarn.setVisibility(prefs.getApiKey().isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == OVERLAY_REQ && Settings.canDrawOverlays(this)) launchOverlayAndTikTok();
    }
}

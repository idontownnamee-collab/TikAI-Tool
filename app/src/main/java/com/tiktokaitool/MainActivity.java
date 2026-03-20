package com.tiktokaitool;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.tiktokaitool.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_REQ = 1001;
    private ActivityMainBinding binding;
    private PrefsManager prefs;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = new PrefsManager(this);

        animateEntrance();
        setupButtons();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type   = intent.getType();

        // Shared text (from TikTok share button)
        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && (shared.contains("tiktok.com") || shared.contains("vm.tiktok"))) {
                binding.etUrl.setText(shared.trim());
                Toast.makeText(this, "TikTok link received!", Toast.LENGTH_SHORT).show();
                // Auto-open downloads
                handler.postDelayed(this::openDownloads, 400);
            }
        }

        // Direct URL view
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.contains("tiktok")) {
                binding.etUrl.setText(url);
                handler.postDelayed(this::openDownloads, 400);
            }
        }
    }

    private void setupButtons() {
        binding.btnOpenTiktok.setOnClickListener(v -> { pulse(v); openTikTok(); });
        binding.btnAiFinder.setOnClickListener(v -> { pulse(v); startAiFinder(); });
        binding.btnPaste.setOnClickListener(v -> pasteFromClipboard());
        binding.btnDownload.setOnClickListener(v -> { pulse(v); openDownloads(); });
        binding.btnStopOverlay.setOnClickListener(v -> stopOverlay());
        binding.btnHistory.setOnClickListener(v ->
            startActivity(new Intent(this, HistoryActivity.class)));
        binding.btnSettings.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        binding.tvApiWarn.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        binding.fabPaste.setOnClickListener(v -> {
            pasteFromClipboard();
            binding.fabPaste.hide();
        });
    }

    private void animateEntrance() {
        View[] cards = {binding.cardHeader, binding.cardActions,
                        binding.cardDownload, binding.cardBottom};
        for (int i = 0; i < cards.length; i++) {
            cards[i].setAlpha(0f);
            cards[i].setTranslationY(50f);
            cards[i].animate().alpha(1f).translationY(0f)
                .setDuration(380).setStartDelay(i * 70L)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
        }
    }

    private void pulse(View v) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70)
            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                .setDuration(200).setInterpolator(new OvershootInterpolator(2f)).start())
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
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.zhiliaoapp.musically")));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically")));
        }
    }

    private void startAiFinder() {
        if (prefs.getApiKey().isEmpty()) {
            Toast.makeText(this, "Set your Claude API key in Settings first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("TikAI needs 'Draw over other apps' to show the FIND button on top of TikTok.")
                .setPositiveButton("Grant", (d, w) -> startActivityForResult(
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), OVERLAY_REQ))
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }
        launchOverlay();
    }

    private void launchOverlay() {
        Intent si = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si);
        else startService(si);
        handler.postDelayed(this::openTikTok, 500);
        Toast.makeText(this, "FIND button active! Tap it to analyze anything.", Toast.LENGTH_LONG).show();
        updateStatus();
    }

    private void stopOverlay() {
        Intent i = new Intent(this, OverlayService.class);
        i.setAction("STOP");
        startService(i);
        handler.postDelayed(this::updateStatus, 300);
    }

    private void openDownloads() {
        String url = binding.etUrl.getText().toString().trim();
        if (!url.isEmpty() && (url.contains("tiktok") || url.contains("vm.tiktok"))) {
            startActivity(new Intent(this, DownloadsActivity.class).putExtra("url", url));
        } else {
            startActivity(new Intent(this, DownloadsActivity.class));
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
            CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
            if (t != null) {
                String txt = t.toString();
                if (txt.contains("tiktok") || txt.contains("vm.tiktok")) {
                    binding.etUrl.setText(txt);
                    Toast.makeText(this, "✓ TikTok link pasted!", Toast.LENGTH_SHORT).show();
                    binding.fabPaste.hide();
                } else {
                    Toast.makeText(this, "No TikTok URL found in clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void checkClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
            ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                String txt = item.getText().toString();
                if (txt.contains("tiktok.com") || txt.contains("vm.tiktok")) {
                    // Don't show if already pasted
                    String current = binding.etUrl.getText().toString();
                    if (!current.equals(txt.trim())) {
                        binding.fabPaste.show();
                    }
                    return;
                }
            }
        }
        binding.fabPaste.hide();
    }

    private void updateStatus() {
        boolean active = OverlayService.isRunning;
        binding.tvStatus.setText(active ? "● AI Overlay Active" : "● AI Overlay Off");
        binding.tvStatus.setTextColor(getResources().getColor(
            active ? R.color.green : R.color.text_secondary));
        binding.btnStopOverlay.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        binding.tvApiWarn.setVisibility(prefs.getApiKey().isEmpty() ? View.VISIBLE : View.GONE);
        checkClipboard();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == OVERLAY_REQ && Settings.canDrawOverlays(this)) launchOverlay();
    }
}

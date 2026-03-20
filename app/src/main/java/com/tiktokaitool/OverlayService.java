package com.tiktokaitool;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.nio.ByteBuffer;

public class OverlayService extends Service {
    public static boolean isRunning = false;
    public static MediaProjection sProjection = null;
    private WindowManager wm;
    private View overlayView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final String CH = "tikai_ch";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && "CAPTURE".equals(intent.getAction())) { doCapture(); return START_STICKY; }
        createChannel();
        startForeground(1, buildNotif());
        showOverlay();
        isRunning = true;
        return START_STICKY;
    }

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        p.x = 0; p.y = 0;

        final int[] lastY = {0};
        final long[] downTime = {0};
        final boolean[] moved = {false};

        overlayView.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastY[0] = (int) e.getRawY();
                    downTime[0] = System.currentTimeMillis();
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dy = (int) e.getRawY() - lastY[0];
                    if (Math.abs(dy) > 8) moved[0] = true;
                    if (moved[0]) { p.y += dy; wm.updateViewLayout(overlayView, p); }
                    lastY[0] = (int) e.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) onFindTapped();
                    return true;
            }
            return false;
        });
        wm.addView(overlayView, p);
    }

    private void onFindTapped() {
        if (overlayView != null) {
            overlayView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                if (sProjection != null) {
                    handler.postDelayed(this::doCapture, 200);
                } else {
                    Intent i = new Intent(this, ScreenCaptureActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    if (overlayView != null) overlayView.animate().alpha(1f).setDuration(200).start();
                }
            }).start();
        }
    }

    private void doCapture() {
        if (sProjection == null) return;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int w = dm.widthPixels, h = dm.heightPixels, dpi = dm.densityDpi;

        ImageReader reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        VirtualDisplay vd = sProjection.createVirtualDisplay("TikAI",w,h,dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);

        reader.setOnImageAvailableListener(r -> {
            Image img = null;
            try {
                img = r.acquireLatestImage();
                if (img == null) return;
                Image.Plane[] planes = img.getPlanes();
                ByteBuffer buf = planes[0].getBuffer();
                int ps = planes[0].getPixelStride(), rs = planes[0].getRowStride();
                Bitmap bmp = Bitmap.createBitmap(rs/ps, h, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buf);
                bmp = Bitmap.createBitmap(bmp, 0, 0, w, h);
                vd.release();
                File dir = new File(getCacheDir(), "caps"); dir.mkdirs();
                File f = new File(dir, "cap_"+System.currentTimeMillis()+".jpg");
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fo);
                }
                final String path = f.getAbsolutePath();
                handler.post(() -> {
                    if (overlayView != null) overlayView.animate().alpha(1f).setDuration(200).start();
                    Intent intent = new Intent(this, CropActivity.class);
                    intent.putExtra("path", path);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> { if (overlayView != null) overlayView.animate().alpha(1f).setDuration(200).start(); });
            } finally { if (img != null) img.close(); }
        }, handler);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH, "TikAI Overlay", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotif() {
        Intent stop = new Intent(this, OverlayService.class); stop.setAction("STOP");
        PendingIntent pi = PendingIntent.getService(this, 0, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CH)
            .setContentTitle("TikAI Active")
            .setContentText("Tap FIND button to analyze items")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .addAction(android.R.drawable.ic_delete, "Stop", pi)
            .setPriority(NotificationCompat.PRIORITY_LOW).build();
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        super.onDestroy(); isRunning = false;
        if (overlayView != null) { try { wm.removeView(overlayView); } catch(Exception e){} }
        if (sProjection != null) { sProjection.stop(); sProjection = null; }
    }
}

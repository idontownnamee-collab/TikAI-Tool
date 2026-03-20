package com.tiktokaitool;
import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;

public class ScreenCaptureActivity extends Activity {
    private static final int REQ = 200;
    private MediaProjectionManager mpm;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ);
    }
    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ && res == RESULT_OK && data != null) {
            OverlayService.sProjection = mpm.getMediaProjection(res, data);
            Intent i = new Intent(this, OverlayService.class); i.setAction("CAPTURE");
            startForegroundService(i);
        } else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        finish();
    }
}

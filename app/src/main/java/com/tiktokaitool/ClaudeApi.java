package com.tiktokaitool;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ClaudeApi {
    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String VERSION   = "2023-06-01";

    public interface Callback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void analyze(String apiKey, String model, String prompt, String imagePath, Callback cb) {
        new Thread(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap bmp = BitmapFactory.decodeFile(imagePath, opts);
                if (bmp == null) { cb.onError("Could not load image"); return; }
                int max = 1024;
                if (bmp.getWidth() > max || bmp.getHeight() > max) {
                    float s = Math.min((float) max / bmp.getWidth(), (float) max / bmp.getHeight());
                    bmp = Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth()*s), (int)(bmp.getHeight()*s), true);
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                JSONObject body = new JSONObject()
                    .put("model", model)
                    .put("max_tokens", 1024)
                    .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", new JSONArray()
                            .put(new JSONObject().put("type","image")
                                .put("source", new JSONObject()
                                    .put("type","base64")
                                    .put("media_type","image/jpeg")
                                    .put("data", b64)))
                            .put(new JSONObject().put("type","text").put("text", prompt)))));

                HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type","application/json");
                c.setRequestProperty("x-api-key", apiKey);
                c.setRequestProperty("anthropic-version", VERSION);
                c.setDoOutput(true);
                c.setConnectTimeout(30000);
                c.setReadTimeout(60000);
                byte[] bd = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setRequestProperty("Content-Length", String.valueOf(bd.length));
                c.getOutputStream().write(bd);

                int code = c.getResponseCode();
                InputStream is = code == 200 ? c.getInputStream() : c.getErrorStream();
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line; while ((line = br.readLine()) != null) sb.append(line);

                JSONObject resp = new JSONObject(sb.toString());
                if (code != 200) {
                    cb.onError(resp.optJSONObject("error") != null ?
                        resp.getJSONObject("error").optString("message","API error") : "HTTP "+code);
                    return;
                }
                cb.onSuccess(resp.getJSONArray("content").getJSONObject(0).getString("text"));
            } catch (Exception e) {
                cb.onError(e.getMessage() != null ? e.getMessage() : "Network error");
            }
        }).start();
    }

    public interface VideoCallback {
        void onSuccess(String videoUrl, String title, String cover);
        void onError(String error);
    }

    public static void getTikTokVideo(String tiktokUrl, VideoCallback cb) {
        new Thread(() -> {
            try {
                String api = "https://www.tikwm.com/api/?url=" +
                    java.net.URLEncoder.encode(tiktokUrl, "UTF-8") + "&hd=1";
                HttpURLConnection c = (HttpURLConnection) new URL(api).openConnection();
                c.setRequestProperty("User-Agent","Mozilla/5.0");
                c.setConnectTimeout(20000); c.setReadTimeout(30000);
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                String line; while ((line = br.readLine()) != null) sb.append(line);
                JSONObject r = new JSONObject(sb.toString());
                if (r.optInt("code",-1) != 0) { cb.onError(r.optString("msg","Failed")); return; }
                JSONObject d = r.getJSONObject("data");
                String play = d.optString("hdplay", d.optString("play",""));
                if (play.isEmpty()) { cb.onError("No video URL found"); return; }
                cb.onSuccess(play, d.optString("title","TikTok Video"), d.optString("cover",""));
            } catch (Exception e) { cb.onError(e.getMessage() != null ? e.getMessage() : "Error"); }
        }).start();
    }
}

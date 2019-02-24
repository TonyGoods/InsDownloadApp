package com.myapp.insdownload;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private EditText web;
    private TextView txtProcess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);
        txtProcess = findViewById(R.id.txtProcess);
        Button btnPaste = findViewById(R.id.btnPaste);
        btnPaste.setOnClickListener((v) -> {
            getContentFromCopy();
        });
        Button btnMult = findViewById(R.id.btnDownload);
        btnMult.setOnClickListener(v -> {
            if (!web.getText().toString().isEmpty()) {
                download();
            } else {
                Toast.makeText(MainActivity.this, "the weblink is empty!", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentFromCopy();
    }

    protected void getContentFromCopy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null) {
            return;
        }
        ClipData data = cm.getPrimaryClip();
        if (data == null) {
            return;
        }
        ClipData.Item item = data.getItemAt(0);
        String content = item.getText().toString();
        web.setText(content);
    }

    @SuppressLint("StaticFieldLeak")
    protected void download() {
        String webUrl = web.getText().toString();
        AsyncTask task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.CALL_PHONE}, 1);
                    }
                    URL url = new URL(webUrl);
                    HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
                    if (httpsURLConnection.getResponseCode() == 200) {
                        InputStream reader = httpsURLConnection.getInputStream();
                        byte[] test = new byte[1024];
                        String name = Environment.getExternalStorageDirectory().getPath() + "/AMyInstagram/temp.txt";
                        File file = new File(name);
                        if (!file.exists()) {
                            if (!file.createNewFile()) {
                                Toast.makeText(MainActivity.this, "failed to create file", Toast.LENGTH_LONG).show();
                            }
                        }
                        OutputStream os = new FileOutputStream(name);
                        int len;
                        while ((len = reader.read(test)) != -1) {
                            os.write(test, 0, len);
                        }
                        reader.close();
                        os.close();
                        InputStream fi = new FileInputStream(name);
                        BufferedReader r = new BufferedReader(new InputStreamReader(fi));
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (line.contains("<script type=\"text/javascript\">window._sharedData")) {
                                line = line.replace("<script type=\"text/javascript\">window._sharedData = ", "");
                                line = line.replace(";</script>", "");
                                JSONObject jsonObject = new JSONObject(line);
                                JSONObject json_entry_data = jsonObject.getJSONObject("entry_data");
                                JSONObject json_post_page = json_entry_data.getJSONArray("PostPage").getJSONObject(0);
                                JSONObject json_graphql = json_post_page.getJSONObject("graphql");
                                JSONObject json_shortcode_media = json_graphql.getJSONObject("shortcode_media");
                                switch (json_shortcode_media.getString("__typename")) {
                                    case "GraphSidecar":
                                        downloadMulti(json_shortcode_media);
                                        break;
                                    case "GraphVideo":
                                        downloadVideo(json_shortcode_media.getString("video_url"));
                                        break;
                                    case "GraphImage":
                                        JSONArray json_display_resources = json_shortcode_media.getJSONArray("display_resources");
                                        downloadImage(json_display_resources.getJSONObject(json_display_resources.length() - 1).getString("src"));
                                        break;
                                }
                            }
                        }
                        r.close();
                        fi.close();
                    } else {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(httpsURLConnection.getErrorStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            Log.w("InsWrong", line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        task.execute();
    }

    protected void downloadMulti(JSONObject json) throws JSONException {
        JSONObject json_edge_sidecar_to_children = json.getJSONObject("edge_sidecar_to_children");
        JSONArray json_edges = json_edge_sidecar_to_children.getJSONArray("edges");
        for (int index = 0; index < json_edges.length(); index++) {
            JSONObject json_edge = json_edges.getJSONObject(index);
            JSONObject json_node = json_edge.getJSONObject("node");
            if (json_node.getString("__typename").equals("GraphImage")) {
                JSONArray json_display_resources = json_node.getJSONArray("display_resources");
                JSONObject json_display_resource = json_display_resources.getJSONObject(json_display_resources.length() - 1);
                downloadImage(json_display_resource.getString("src"));
            } else if (json_node.getString("__typename").equals("GraphVideo")) {
                downloadVideo(json_node.getString("video_url"));
            }
        }
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "All done!", Toast.LENGTH_LONG).show();
            txtProcess.setText("");
        });
    }

    @SuppressLint("StaticFieldLeak")
    protected void downloadImage(String imageUrl) {
        try {
            Bitmap bitmap;
            URL picUrl = new URL(imageUrl);
            HttpsURLConnection connection = (HttpsURLConnection) picUrl.openConnection();
            if (connection.getResponseCode() == 200) {
                InputStream inputStream = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                File file = new File(Environment.getExternalStorageDirectory().getPath() + "/AMyInstagram");
                if (!file.exists()) {
                    if (!file.mkdir()) {
                        Toast.makeText(MainActivity.this, "failed to create dictionary!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                String name = Environment.getExternalStorageDirectory().getPath() + "/AMyInstagram/" + System.currentTimeMillis() + ".jpg";
                FileOutputStream writer = new FileOutputStream(name);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, writer);
                writer.close();
                MediaScannerConnection.scanFile(MainActivity.this, new String[]{name}, null, null);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "download successful!", Toast.LENGTH_LONG).show());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @SuppressLint({"SetTextI18n", "StaticFieldLeak"})
    private void downloadVideo(String videoUrl) {
        try {
            URL mp4Url = new URL(videoUrl);
            HttpsURLConnection mp4URLConnection = (HttpsURLConnection) mp4Url.openConnection();
            InputStream is = mp4URLConnection.getInputStream();
            Long length = mp4URLConnection.getContentLengthLong();
            byte[] bs = new byte[1024];
            int len;
            String name = Environment.getExternalStorageDirectory().getPath() + "/AMyInstagram/" + System.currentTimeMillis() + ".mp4";
            OutputStream os = new FileOutputStream(name);
            long total = 0;
            while ((len = is.read(bs)) != -1) {
                os.write(bs, 0, len);
                total += len;
                String processing = String.valueOf(total * 100.0 / length) + "%";
                runOnUiThread(() -> txtProcess.setText("视频下载进度：" + processing));
            }
            os.close();
            is.close();
            MediaScannerConnection.scanFile(MainActivity.this, new String[]{name}, null, null);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "download successful!", Toast.LENGTH_LONG).show();
                txtProcess.setText("");
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
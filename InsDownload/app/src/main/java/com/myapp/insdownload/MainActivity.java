package com.myapp.insdownload;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private EditText web;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);
        Button btnPaste = findViewById(R.id.btnPaste);
        btnPaste.setOnClickListener((v) -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData data = cm.getPrimaryClip();
            ClipData.Item item = data.getItemAt(0);
            String content = item.getText().toString();
            web.setText(content);
        });
        Button btnDownload = findViewById(R.id.btnDownload);
        btnDownload.setOnClickListener((v) -> {
            if (!web.getText().toString().isEmpty()) {
                download();
            }else{
                Toast.makeText(MainActivity.this,"the weblink is empty!",Toast.LENGTH_LONG).show();
            }
        });

    }

    protected void download() {
        AsyncTask task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try {
                    URL url = new URL(web.getText().toString());
                    HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
                    if (httpsURLConnection.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
                        InputStreamReader in = new InputStreamReader(httpsURLConnection.getInputStream());
                        String line;
                        String html = "";
                        while ((line = reader.readLine()) != null) {
                            html += line;
                        }
                        Document doc = Jsoup.parse(html);
                        Elements scripts = doc.getElementsByTag("script");
                        for (Element element : scripts) {
                            if (element.toString().startsWith("<script type=\"text/javascript\">window._sharedData = ")) {
                                String json = element.toString().replace("<script type=\"text/javascript\">window._sharedData = ", "");
                                JSONObject jsonObject = new JSONObject(json);
                                String picUrlStr = jsonObject.getJSONObject("entry_data").getJSONArray("PostPage").getJSONObject(0).getJSONObject("graphql").getJSONObject("shortcode_media").getString("display_url");
                                Bitmap bitmap;
                                URL picUrl = new URL(picUrlStr);
                                HttpsURLConnection connection = (HttpsURLConnection) picUrl.openConnection();
                                if (connection.getResponseCode() == 200) {
                                    InputStream inputStream = connection.getInputStream();
                                    bitmap = BitmapFactory.decodeStream(inputStream);
                                    inputStream.close();
                                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                            != PackageManager.PERMISSION_GRANTED) {
                                        ActivityCompat.requestPermissions(MainActivity.this,
                                                new String[]{Manifest.permission.CALL_PHONE},
                                                1);
                                    }

                                    File file = new File(Environment.getExternalStorageDirectory().getPath() + "/Instagram");
                                    if (!file.exists()) {
                                        file.mkdir();
                                    }
                                    String name = Environment.getExternalStorageDirectory().getPath() + "/Instagram/" + System.currentTimeMillis() + ".jpg";
                                    File image = new File(name);
                                    FileOutputStream writer = new FileOutputStream(name);
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, writer);
                                    writer.close();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this, "download successful!", Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }

                        }
                    } else {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(httpsURLConnection.getErrorStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                        }
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
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
}

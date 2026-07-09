package com.m3u8dl;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton; // <-- Import ImageButton ditambahkan
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private EditText videoUrlEditText;
    private TextView activeCounter;
    private DownloadAdapter adapter;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private LinearLayout loadingLayout;
    private LinearLayout mainLayout;
    private TextView loadingStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        loadingLayout = findViewById(R.id.loading_layout);
        mainLayout = findViewById(R.id.main_layout);
        loadingStatusText = findViewById(R.id.loading_status_text);
        videoUrlEditText = findViewById(R.id.video_url_edit_text);
        activeCounter = findViewById(R.id.active_counter);
        
        // 🔥 PERBAIKAN DI SINI: Mengubah Button menjadi ImageButton agar cocok dengan file desain XML
        ImageButton downloadButton = findViewById(R.id.download_button);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);
        adapter = new DownloadAdapter();
        recyclerView.setAdapter(adapter);

        checkAllPermissions();
        handleIntent(getIntent());

        downloadButton.setOnClickListener(v -> {
            String url = videoUrlEditText.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a URL!", Toast.LENGTH_SHORT).show();
                return;
            }
            startDownloadTask(url);
        });

        initLibraries();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.hasExtra(Intent.EXTRA_TEXT)) {
            String sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedUrl != null && !sharedUrl.isEmpty()) {
                videoUrlEditText.setText(sharedUrl);
                startDownloadTask(sharedUrl);
            }
        }
    }

    private void initLibraries() {
        new Thread(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                uiHandler.post(() -> loadingStatusText.setText("Preparing FFmpeg..."));
                FFmpeg.getInstance().init(getApplicationContext());
                uiHandler.post(() -> {
                    loadingLayout.setVisibility(View.GONE);
                    mainLayout.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                uiHandler.post(() -> loadingStatusText.setText("Failed to load libraries!"));
            }
        }).start();
    }

    private void startDownloadTask(String url) {
        final String baseFetchText = "Fetching info";
        DownloadItem item = new DownloadItem(baseFetchText, url);
        adapter.addDownload(item);
        updateCounter();

        Runnable dotAnimator = new Runnable() {
            int dotCount = 0;
            @Override
            public void run() {
                if (item.isFinished || item.isStopped || !item.fileName.startsWith(baseFetchText)) return;
                dotCount = (dotCount + 1) % 4;
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) dots.append(".");
                item.fileName = baseFetchText + dots.toString();
                notifyProgressUpdate(item);
                uiHandler.postDelayed(this, 400);
            }
        };
        uiHandler.postDelayed(dotAnimator, 400);

        new Thread(() -> {
            try {
                String lowerUrl = url.toLowerCase();

                // 1. Eksekusi Langsung: Jika input adalah link hasil sniffing
                if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".flv") || lowerUrl.contains(".rtmp") || lowerUrl.contains(".mpd")) {
                    String genTitle = "LiveStream_" + System.currentTimeMillis();
                    uiHandler.post(() -> item.fileName = genTitle + ".mp4");
                    downloadWithFFmpegKit(genTitle, url, item);
                    return;
                }

                // 2. Ekstraksi: Gunakan yt-dlp HANYA untuk mendapatkan link murni
                uiHandler.post(() -> item.speedStr = "Extracting link...");

                YoutubeDLRequest extractReq = new YoutubeDLRequest(url);
                extractReq.addOption("--print", "%(title)s\n%(url)s");
                extractReq.addOption("--no-warnings");

                com.yausername.youtubedl_android.YoutubeDLResponse response =
                        YoutubeDL.getInstance().execute(extractReq, item.processId);

                String out = response.getOut();
                if (out != null && !out.trim().isEmpty()) {
                    String[] lines = out.trim().split("\n");
                    String title = "Extracted_Media";
                    String streamUrl = lines[0]; // fallback jika hanya 1 baris

                    if (lines.length >= 2) {
                        title = lines[0].replaceAll("[\\\\/:*?\"<>|]", "_");
                        streamUrl = lines[lines.length - 1];
                    }

                    final String finalTitle = title;
                    uiHandler.post(() -> item.fileName = finalTitle + ".mp4");

                    // 3. Routing Berdasarkan Format Hasil Ekstrak
                    String lowerStreamUrl = streamUrl.toLowerCase();
                    if (lowerStreamUrl.contains(".m3u8") || lowerStreamUrl.contains(".flv") ||
                        lowerStreamUrl.contains(".rtmp") || lowerStreamUrl.contains(".mpd") ||
                        lowerStreamUrl.contains("manifest")) {

                        uiHandler.post(() -> item.speedStr = "Connecting to FFmpegKit...");
                        downloadWithFFmpegKit(finalTitle, streamUrl, item);
                    } else {
                        // Fallback ke yt-dlp native murni HANYA jika berupa VOD biasa (bukan streaming)
                        downloadWithYtDlp(url, item);
                    }
                } else {
                    downloadWithYtDlp(url, item);
                }
            } catch (Exception e) {
                try {
                    downloadWithYtDlp(url, item);
                } catch (Exception ex) {
                    uiHandler.post(() -> {
                        item.isStopped = true;
                        Toast.makeText(MainActivity.this, "Failed to extract link!", Toast.LENGTH_SHORT).show();
                        adapter.removeDownload(item);
                        updateCounter();
                    });
                }
            }
        }).start();

        uiHandler.post(() -> videoUrlEditText.setText(""));
    }

    private void downloadWithFFmpegKit(String title, String streamUrl, DownloadItem item) {
        File outputDir = RecorderUtils.getOutputDirectory();

        String tsPath = new File(outputDir, title + ".ts").getAbsolutePath();
        String mp4Path = new File(outputDir, title + ".mp4").getAbsolutePath();

        String downloadCmd = "-y -i \"" + streamUrl + "\" -c copy \"" + tsPath + "\"";

        FFmpegSession downloadSession = FFmpegKit.executeAsync(downloadCmd,
            completeCallback -> {
                File tsFile = new File(tsPath);

                if (tsFile.exists() && tsFile.length() > 0) {
                    uiHandler.post(() -> {
                        item.speedStr = "Remuxing to MP4...";
                        item.sizeStr = "Processing...";
                        notifyProgressUpdate(item);
                    });

                    String remuxCmd = "-y -i \"" + tsPath + "\" -c copy -bsf:a aac_adtstoasc \"" + mp4Path + "\"";

                    FFmpegSession remuxSession = FFmpegKit.executeAsync(remuxCmd, remuxCallback -> {
                        File mp4File = new File(mp4Path);
                        if (mp4File.exists() && mp4File.length() > 0) {
                            tsFile.delete();
                        }

                        item.isFinished = true;
                        uiHandler.post(() -> {
                            adapter.removeDownload(item);
                            updateCounter();
                        });
                    });

                    item.ffmpegSessionId = remuxSession.getSessionId();

                } else {
                    item.isFinished = true;
                    uiHandler.post(() -> {
                        adapter.removeDownload(item);
                        updateCounter();
                    });
                }
            },
            logCallback -> {
                // Log disembunyikan / tidak dicetak ke UI
            },
            statisticsCallback -> {
                if (item.isStopped) return;

                long sizeInBytes = (long) statisticsCallback.getSize();
                long timeVideoMs = (long) statisticsCallback.getTime();

                long seconds = (timeVideoMs / 1000) % 60;
                long minutes = (timeVideoMs / (1000 * 60)) % 60;
                long hours = (timeVideoMs / (1000 * 60 * 60)) % 24;
                item.durationStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);

                double sizeMb = sizeInBytes / (1024.0 * 1024.0);
                item.sizeStr = String.format("%.2f MB", sizeMb);

                long currentTimeMs = System.currentTimeMillis();
                if (item.lastTimeMs == 0) {
                    item.lastTimeMs = currentTimeMs;
                    item.lastSizeInBytes = sizeInBytes;
                } else {
                    long timeDiff = currentTimeMs - item.lastTimeMs;
                    if (timeDiff >= 1000) {
                        long sizeDiff = sizeInBytes - item.lastSizeInBytes;
                        double bytesPerSec = (sizeDiff * 1000.0) / timeDiff;

                        if (bytesPerSec >= 1024 * 1024) {
                            item.speedStr = String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0));
                        } else {
                            item.speedStr = String.format("%.0f KB/s", bytesPerSec / 1024.0);
                        }

                        item.lastTimeMs = currentTimeMs;
                        item.lastSizeInBytes = sizeInBytes;
                    }
                }
                notifyProgressUpdate(item);
            }
        );

        item.ffmpegSessionId = downloadSession.getSessionId();
    }

    private void downloadWithYtDlp(String url, DownloadItem item) throws Exception {
        YoutubeDLRequest request = RecorderUtils.buildRequest(url);

        YoutubeDL.getInstance().execute(request, item.processId, (progress, etaInSeconds, line) -> {
            if (item.isStopped) throw new RuntimeException("Successfully stopped");
            if (etaInSeconds > 0) item.durationStr = "ETA: " + etaInSeconds + "s";

            RecorderUtils.parseRealtimeLog(line, item);
            notifyProgressUpdate(item);
            return kotlin.Unit.INSTANCE;
        });

        uiHandler.post(() -> {
            item.isFinished = true;
            adapter.removeDownload(item);
            updateCounter();
        });
    }

    private void notifyProgressUpdate(DownloadItem item) {
        uiHandler.post(() -> {
            int index = adapter.getItems().indexOf(item);
            if (index != -1) adapter.notifyItemChanged(index, "UPDATE_PROGRESS");
        });
    }

    public void updateCounter() {
        int count = adapter.getItemCount();
        if (activeCounter != null) {
            activeCounter.setText(count + "/1000");
        }
    }

    private void checkAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, 100);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, 100);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
    }
}

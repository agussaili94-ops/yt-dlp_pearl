package com.m3u8dl;

import android.os.Environment;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecorderUtils {

    public static File getOutputDirectory() {
        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File lsDlDir = new File(moviesDir, "yt-dlp");
        if (!lsDlDir.exists()) { lsDlDir.mkdirs(); }
        return lsDlDir;
    }

    public static YoutubeDLRequest buildRequest(String url) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);

        // Akses ffmpeg bawaan yt-dlp diputus. 
        // Streaming akan di-intercept di MainActivity untuk FFmpegKit.
        request.addOption("--concurrent-fragments", "4");
        request.addOption("--no-check-certificate");
        request.addOption("--geo-bypass");
        request.addOption("--fixup", "force");
        request.addOption("--remux-video", "mp4");

        File outputDir = getOutputDirectory();
        String outPath = new File(outputDir, "%(uploader)s - %(title).40s [%(resolution)s].%(ext)s").getAbsolutePath();
        request.addOption("-o", outPath);

        return request;
    }

    public static void parseRealtimeLog(String logLine, DownloadItem item) {
        if (logLine == null || logLine.trim().isEmpty()) return;

        try {
            String lowerLog = logLine.toLowerCase();

            // 1. Tangkap Nama File
            if (logLine.contains("Destination:")) {
                String path = logLine.substring(logLine.indexOf("Destination:") + 12).trim();
                item.fileName = new File(path).getName();
                return;
            } else if (logLine.contains("has already been downloaded")) {
                Matcher m = Pattern.compile("\\[.*?\\]\\s+(.*)\\s+has already been").matcher(logLine);
                if (m.find()) { item.fileName = new File(m.group(1)).getName(); }
                return;
            }

            boolean matched = false;

            // 2. Tangkap Log YT-DLP (Hanya untuk fallback VOD)
            if (lowerLog.contains("[download]")) {
                Matcher mSize = Pattern.compile("([\\d.]+\\s*[KMG]iB)").matcher(logLine);
                if (mSize.find()) {
                    item.sizeStr = mSize.group(1).replace("GiB", " GB").replace("MiB", " MB").replace("KiB", " KB");
                    matched = true;
                }

                Matcher mSpeed = Pattern.compile("at\\s+([\\d.]+\\s*[KMG]iB/s)").matcher(logLine);
                if (mSpeed.find()) {
                    item.speedStr = mSpeed.group(1).replace("GiB/s", " GB/s").replace("MiB/s", " MB/s").replace("KiB/s", " KB/s");
                    matched = true;
                }

                if (!matched && logLine.length() > 12) {
                    item.speedStr = logLine.replace("[download]", "").trim();
                    matched = true;
                }
            }
            
            // Log FFmpeg via -stats dinonaktifkan di regex ini karena 
            // callback FFmpegKit di MainActivity mengambil alih pemrosesan streaming.
            
            // 4. Status Remuxing
            else if (logLine.contains("[Merger]") || logLine.contains("[Fixup]")) {
                item.speedStr = "Remuxing MP4...";
                matched = true;
            }

            if (!matched && item.sizeStr.contains("0kB") && logLine.length() < 40) {
                item.speedStr = logLine.trim();
            }

        } catch (Exception ignored) {}
    }

    public static void killZombieFFmpeg(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        try {
            String baseName = fileName;
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf('.'));
            }
            Runtime.getRuntime().exec(new String[]{"pkill", "-f", baseName});
        } catch (Exception e) { e.printStackTrace(); }
    }
}

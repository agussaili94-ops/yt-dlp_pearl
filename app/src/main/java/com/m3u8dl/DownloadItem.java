package com.m3u8dl;

import java.util.UUID;

public class DownloadItem {
    public String fileName;
    public String url;
    public String processId;
    public Long ffmpegSessionId = null;

    public String speedStr = "Preparing...";
    public String sizeStr = "0kB";
    public String durationStr = "Video";

    public boolean isFinished = false;
    public boolean isStopped = false;

    // 🟢 Memori untuk Speedometer Jaringan
    public long lastTimeMs = 0;
    public long lastSizeInBytes = 0;

    public DownloadItem(String fileName, String url) {
        this.fileName = fileName;
        this.url = url;
        this.processId = UUID.randomUUID().toString();
    }
}

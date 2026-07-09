package com.m3u8dl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class LiveMonitorService extends Service {

    private static final String CHANNEL_ID = "LsDlMonitorChannel";
    private static final int NOTIF_ID = 1;
    private NotificationManager manager;

    @Override
    public void onCreate() {
        super.onCreate();
        manager = getSystemService(NotificationManager.class);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int activeCount = 0;
        if (intent != null) {
            activeCount = intent.getIntExtra("TASK_COUNT", 0);
        }
        
        startForeground(NOTIF_ID, buildNotification(activeCount));
        return START_STICKY;
    }

    // 🟢 Merakit teks notifikasi sesuai jumlah task
    private Notification buildNotification(int activeCount) {
        String textContent = activeCount > 0 ? 
            activeCount + " active download process(es)." : 
            "App is ready to download.";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("M3U8 Downloader")
                .setContentText(textContent)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true) // Agar tidak bunyi/getar terus saat angka berubah
                .build();
    }

    // 🟢 Fungsi jarak jauh yang akan dipanggil oleh MainActivity
    public static void updateNotification(Context context, int activeCount) {
        Intent intent = new Intent(context, LiveMonitorService.class);
        intent.putExtra("TASK_COUNT", activeCount);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopForeground(true);
        stopSelf();
        android.os.Process.killProcess(android.os.Process.myPid());
        super.onTaskRemoved(rootIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Background Service", NotificationManager.IMPORTANCE_LOW);
            serviceChannel.setSound(null, null);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }
}

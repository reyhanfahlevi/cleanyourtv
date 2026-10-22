package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Persistent background/foreground service that keeps TvHubServer (REST API on 8080 & mDNS)
 * alive independently of the MainActivity lifecycle.
 */
public class TvHubService extends Service {
    private static final String TAG = "TvHubService";
    private static final String CHANNEL_ID = "tvhub_service_channel";
    private static final int NOTIFICATION_ID = 8080;

    private static volatile TvHubService instance;
    private static volatile TvHubServer hubServer;

    public static TvHubService getInstance() {
        return instance;
    }

    public static TvHubServer getServer() {
        return hubServer;
    }

    public static void start(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, TvHubService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start TvHubService", e);
        }
    }

    public static void updateActivity(MainActivity activity) {
        if (hubServer != null) {
            hubServer.updateActivity(activity);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        startForegroundNotification();
        ensureServerStarted();
    }

    private void startForegroundNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "tvhub Remote Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Background REST API & remote management server");
                channel.setShowBadge(false);
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.createNotificationChannel(channel);
                }

                Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("tvhub")
                        .setContentText("Remote management active on port 8080")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setOngoing(true);

                startForeground(NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to initialize foreground notification", e);
        }
    }

    private synchronized void ensureServerStarted() {
        if (hubServer == null) {
            try {
                hubServer = new TvHubServer(getApplicationContext());
                hubServer.start();
                Log.i(TAG, "TvHubServer started inside TvHubService");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start TvHubServer in service", e);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureServerStarted();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (hubServer != null) {
            try {
                hubServer.stop();
            } catch (Exception ignored) {}
            hubServer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

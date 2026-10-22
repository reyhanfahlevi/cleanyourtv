package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Ensures TvHubService is automatically launched after TV boot or package updates.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "BootReceiver triggered: " + (intent != null ? intent.getAction() : "null"));
        TvHubService.start(context);
    }
}

package com.aware.plugin.application_list;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * Created by Jonatan Hamberg on 13.2.2017.
 */
public class ApplicationReceiver extends WakefulBroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: Can we skip this phase?
        Log.d(Plugin.TAG, "Received application intent, starting application service");
        Intent service = new Intent(context, ApplicationService.class);
        startWakefulService(context, service);
    }
}

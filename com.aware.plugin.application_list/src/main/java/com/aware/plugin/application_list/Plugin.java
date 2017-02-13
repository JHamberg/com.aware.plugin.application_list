package com.aware.plugin.application_list;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Plugin;

public class Plugin extends Aware_Plugin {
    private static ContextProducer contextProducer;

    @Override
    public void onCreate() {
        super.onCreate();
        REQUIRED_PERMISSIONS.add(Manifest.permission.WAKE_LOCK);


        TAG = "AWARE::"+getResources().getString(R.string.app_name);

        /**
         * Plugins share their current status, i.e., context using this method.
         * This method is called automatically when triggering
         * {@link Aware#ACTION_AWARE_CURRENT_CONTEXT}
         **/
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                if(Aware.DEBUG) Log.d(Plugin.TAG, "Context changed!");
                // Intent updateUiIntent = new Intent(ACTION_APPLIST_UPDATED);
                // sendBroadcast(updateUiIntent);
            }
        };

        contextProducer = CONTEXT_PRODUCER;

        //Add permissions you need (Android M+).
        //By default, AWARE asks access to the #Manifest.permission.WRITE_EXTERNAL_STORAGE

        //REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        //To sync data to the server, you'll need to set this variables from your ContentProvider
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Provider.Application_Data.CONTENT_URI };

        //Activate plugin -- do this ALWAYS as the last thing (this will restart your own plugin and apply the settings)
        Aware.startPlugin(this, "com.aware.plugin.application_list");
    }

    public static ContextProducer getContextProducer() {
        return contextProducer;
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {
            //Check if the user has toggled the debug messages
            Aware.startInstallations(this);
            Aware.setSetting(this, Aware_Preferences.STATUS_INSTALLATIONS, true);

            String firstRun = Aware.getSetting(this, Settings.FIRST_RUN_APPLICATION_LIST);
            if(firstRun.length() == 0 || Boolean.parseBoolean(firstRun)){
                Aware.setSetting(this, Settings.FIRST_RUN_APPLICATION_LIST, false);

                // Collect application list on first run
                Intent initialValues = new Intent(this, ApplicationService.class);
                startService(initialValues);
            }

            // No need to schedule anything here, we use the intent-service
        } else {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //Stop AWARE's instance running inside the plugin package
        Aware.stopAWARE();
    }
}

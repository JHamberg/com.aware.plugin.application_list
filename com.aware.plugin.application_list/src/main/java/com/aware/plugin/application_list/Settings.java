package com.aware.plugin.application_list;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;

public class Settings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    //Plugin settings in XML @xml/preferences
    public static final String FIRST_RUN_APPLICATION_LIST = "first_run_application_list";
    public static final String STATUS_APPLICATION_LIST = "status_plugin_application_list";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Aware.getSetting(getApplicationContext(), STATUS_APPLICATION_LIST).length() == 0) {
            Aware.setSetting(getApplicationContext(), STATUS_APPLICATION_LIST, true);
        }
        if(Aware.getSetting(this, FIRST_RUN_APPLICATION_LIST).length() == 0){
            Aware.setSetting(this, FIRST_RUN_APPLICATION_LIST, true);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Not used at the moment
    }
}

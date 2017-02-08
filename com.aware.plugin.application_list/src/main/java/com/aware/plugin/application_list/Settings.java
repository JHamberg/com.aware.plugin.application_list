package com.aware.plugin.application_list;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;

public class Settings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    //Plugin settings in XML @xml/preferences
    public static final Long DEFAULT_INTERVAL_PLUGIN_APPLICATION_LIST = 1L;
    public static final String FREQUENCY_APPLICATION_LIST = "frequency_plugin_application_list";
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
        if(Aware.getSetting(this, FREQUENCY_APPLICATION_LIST).length() == 0 ) {
            Aware.setSetting( this, FREQUENCY_APPLICATION_LIST, DEFAULT_INTERVAL_PLUGIN_APPLICATION_LIST);
        }
        if (Aware.getSetting(getApplicationContext(), STATUS_APPLICATION_LIST).length() == 0) {
            Aware.setSetting(getApplicationContext(), STATUS_APPLICATION_LIST, true);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference setting = findPreference(key);
        if(setting.getKey().equals(FREQUENCY_APPLICATION_LIST) ) {
            Aware.setSetting(this, key, sharedPreferences.getLong(key, DEFAULT_INTERVAL_PLUGIN_APPLICATION_LIST));
        }
    }
}

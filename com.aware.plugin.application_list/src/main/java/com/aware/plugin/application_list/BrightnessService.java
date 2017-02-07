package com.aware.plugin.application_list;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;

/**
 * Created by Jonatan Hamberg on 6.2.2017.
 */
public class BrightnessService extends IntentService{
    public static String brightness;
    public static String autoBrightness;

    public BrightnessService() {
        super(Plugin.TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(Plugin.TAG, "Received intent to update brightness");

        ContentValues data = new ContentValues();
        brightness = getScreenBrightness();
        autoBrightness = isAutoBrightness();
        data.put(Provider.Brightness_Data.TIMESTAMP, System.currentTimeMillis());
        data.put(Provider.Brightness_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        data.put(Provider.Brightness_Data.BRIGHTNESS, brightness);
        data.put(Provider.Brightness_Data.AUTO_BRIGHTNESS, autoBrightness);
        getContentResolver().insert(Provider.Brightness_Data.CONTENT_URI, data);
        if(Aware.DEBUG){
            Log.d(Plugin.TAG, data.toString());
        }

        Aware_Plugin.ContextProducer producer = Plugin.getContextProducer();
        if(producer != null){
            producer.onContext();
        }
    }

    private String isAutoBrightness() {
        try {
            int mode = android.provider.Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
            return mode == 1 ? "true" : "false";
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return "unknown";
    }

    private String getScreenBrightness(){
        try {
            Integer brightness = android.provider.Settings.System.getInt(getApplicationContext().getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
            return brightness < 0 ? "unknown" : brightness.toString();
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return "unknown";
    }
}

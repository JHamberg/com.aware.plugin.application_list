package com.aware.plugin.application_list;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.PowerManager;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Jonatan Hamberg on 6.2.2017.
 */
public class ApplicationService extends IntentService{
    private static final String UNKNOWN_STRING = "unknown";
    private static final int UNKNOWN_INT = -1;
    private static final int MAX_SERVICES = 255;

    private class AppPackage {
        String packageName = UNKNOWN_STRING;
        String versionName = UNKNOWN_STRING;
        String label = UNKNOWN_STRING;
        String installationPackage = UNKNOWN_STRING;
        int versionCode = UNKNOWN_INT;
        boolean isSystem = false;
        boolean isRunning = false;
        Set<AppProcess> processes = new HashSet<>();
        Set<String> services = new HashSet<>();
        Set<String> signatures = new HashSet<>();
    }

    private class AppProcess {
        String name = UNKNOWN_STRING;
        int importance = UNKNOWN_INT;
        int pid = UNKNOWN_INT;
    }

    public ApplicationService() {
        super(Plugin.TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Plugin.TAG);
        wl.acquire();

        Log.d(Plugin.TAG, "Received intent to update application list");
        addApplications(System.currentTimeMillis());

        Aware_Plugin.ContextProducer producer = Plugin.getContextProducer();
        if(producer != null){
            producer.onContext();
        }
        wl.release();
    }

    public void addApplications(long timestamp) {
        Map<String, AppPackage> pkgInfos = new HashMap<>();
        pkgInfos = addInstalledPackages(pkgInfos);
        pkgInfos = addRunningProcessInfo(pkgInfos);
        pkgInfos = addRunningServiceInfo(pkgInfos);

        for(AppPackage app : pkgInfos.values()){
            try {
                JSONObject application = new JSONObject();
                application.put("packageName", app.packageName);
                application.put("label", app.label);
                application.put("versionName", app.versionName);
                application.put("versionCode", app.versionCode);
                application.put("installationPackage", app.installationPackage);
                application.put("isSystemApp", app.isSystem);
                application.put("isRunning", app.isRunning);

                JSONArray processes = new JSONArray();
                for(AppProcess appProcess : app.processes){
                    JSONObject process = new JSONObject();
                    process.put("processName", appProcess.name);
                    process.put("processImportance", appProcess.importance);
                    process.put("processPid", appProcess.pid);
                    processes.put(process);
                }
                application.put("processes", processes);

                JSONArray services = new JSONArray();
                for(String service : app.services){
                    services.put(service);
                }
                application.put("services", services);

                JSONArray signatures = new JSONArray();
                for(String signature : app.signatures){
                    signatures.put(signature);
                }
                application.put("signatures", signatures);

                // Create a new row for this application
                ContentValues packageData = new ContentValues();
                packageData.put(Provider.Application_Data.TIMESTAMP, timestamp);
                packageData.put(Provider.Application_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                packageData.put(Provider.Application_Data.APPLICATION, application.toString());
                getContentResolver().insert(Provider.Application_Data.CONTENT_URI, packageData);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }



    private Map<String, AppPackage> addInstalledPackages(Map<String, AppPackage> pkgInfos){
        PackageManager pm = getApplicationContext().getPackageManager();
        int flags = PackageManager.GET_SIGNATURES | PackageManager.GET_PERMISSIONS;
        List<PackageInfo> packageList = pm.getInstalledPackages(flags);

        // This is a bit inefficient because we fetch package info
        // twice, the overhead should be negligible in any case.
        for(PackageInfo packInfo : packageList){
            String packageName = packInfo.packageName;
            pkgInfos.put(packageName, constructInfo(packageName));
        }
        return pkgInfos;
    }

    private Map<String, AppPackage> addRunningServiceInfo(Map<String, AppPackage> pkgInfos){
        ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(ACTIVITY_SERVICE);
        List<RunningServiceInfo> runningServices = activityManager.getRunningServices(MAX_SERVICES);
        for (RunningServiceInfo service : runningServices) {
            if (service != null && service.service != null) {
                String serviceName = service.process;
                String packageName = service.service.getPackageName();

                AppPackage info = pkgInfos.containsKey(packageName) ?
                        pkgInfos.get(packageName) :
                        constructInfo(packageName);

                info.isRunning = true;
                info.services.add(serviceName);
                pkgInfos.put(packageName, info);
            }
        }
        return pkgInfos;
    }

    private Map<String, AppPackage> addRunningProcessInfo(Map<String, AppPackage> pkgInfos){
        ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();
        for (RunningAppProcessInfo running : runningProcesses) {
            if (running != null && running.processName != null) {
                String processName = running.processName;

                // Packages this process belongs to
                String[] packages = running.pkgList;
                for (String packageName : packages) {
                    AppPackage info = pkgInfos.containsKey(packageName) ?
                            pkgInfos.get(packageName) :
                            constructInfo(packageName);

                    // Processes also have importance, so we wrap it
                    AppProcess appProcess = new AppProcess();
                    appProcess.name = processName;
                    appProcess.importance = running.importance;
                    appProcess.pid = running.pid;

                    info.isRunning = true;
                    info.processes.add(appProcess);
                    pkgInfos.put(packageName, info);
                }
            }
        }
        return pkgInfos;
    }

    public AppPackage constructInfo(String packageName){
        PackageManager pm = getApplicationContext().getPackageManager();
        int flags = PackageManager.GET_SIGNATURES | PackageManager.GET_PERMISSIONS;
        AppPackage info = new AppPackage();
        try {
            PackageInfo pInfo = pm.getPackageInfo(packageName, flags);
            ApplicationInfo appInfo = pInfo.applicationInfo;

            info.packageName = packageName;
            info.label = pm.getApplicationLabel(appInfo).toString();
            info.versionCode = pInfo.versionCode;
            info.versionName = pInfo.versionName;
            info.isSystem = isSystemApp(appInfo);
            info.signatures = getPackageSignatures(pInfo);
            info.installationPackage = pm.getInstallerPackageName(packageName);

            return info;
        } catch (Exception e) {
            Log.d(Plugin.TAG, "Something went wrong with constructing package info");
            e.printStackTrace();
        }
        return info;
    }

    private boolean isSystemApp(ApplicationInfo appInfo){
        return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) > 0 ||
                (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) > 0;
    }

    private Set<String> getPackageSignatures(android.content.pm.PackageInfo packInfo){
        Set<String> signatures = new HashSet<>();
        String[] permissions = packInfo.requestedPermissions;
        if(permissions != null && permissions.length != 0){
            byte[] bytes = getPermissionBytes(permissions);
            signatures.add(toHex(bytes));
        }
        for(Signature signature : packInfo.signatures) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(signature.toByteArray());
                signatures.add(toHex(md.digest()));
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                if(cf == null) continue;
                X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(signature.toByteArray()));
                if(cert == null) continue;
                PublicKey pkPublic = cert.getPublicKey();
                if(pkPublic == null) continue;
                byte[] data;
                switch(pkPublic.getAlgorithm()) {
                    case "RSA":
                        md = MessageDigest.getInstance("SHA-256");
                        RSAPublicKey rsa = (RSAPublicKey) pkPublic;
                        data = rsa.getModulus().toByteArray();
                        if (data[0] == 0) {
                            byte[] copy = new byte[data.length - 1];
                            System.arraycopy(data, 1, copy, 0, data.length - 1);
                            md.update(copy);
                        } else {
                            md.update(data);
                        }
                        signatures.add(toHex(md.digest()));
                        break;
                    case "DSA":
                        DSAPublicKey dsa = (DSAPublicKey) pkPublic;
                        md = MessageDigest.getInstance("SHA-256");
                        data = dsa.getY().toByteArray();
                        if (data[0] == 0) {
                            byte[] copy = new byte[data.length - 1];
                            System.arraycopy(data, 1, copy, 0, data.length - 1);
                            md.update(copy);
                        } else {
                            md.update(data);
                        }
                        signatures.add(toHex(md.digest()));
                        break;
                    default:
                        Log.e(Plugin.TAG, "Unknown algorithm");
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return signatures;
    }

    private byte[] getPermissionBytes(String[] permissions){
        byte[] bytes = new byte[allPermissions.size() / 8 + 1];
        for(String permission : permissions){
            int idx = allPermissions.indexOf(permission);
            if(idx > 0){
                int i = idx / 8;
                idx = (int) Math.pow(2, idx - i * 8);
                bytes[i] = (byte) (bytes[i] | idx);
            }
        }
        return bytes;
    }

    private String toHex(byte[] data){
        StringBuilder buf = new StringBuilder();
        for (byte b : data) {
            int half = (b >>> 4) & 0x0F;
            int twoHalves = 0;
            do {
                buf.append((0 <= half) && (half <= 9) ? (char) ('0' + half)
                        : (char) ('a' + (half - 10)));
                half = b & 0x0F;
            } while (twoHalves++ < 1);
        }
        return buf.toString();
    }

    private final List<String> allPermissions = new ArrayList<String>(Arrays.asList(
            "android.permission.ACCESS_CHECKIN_PROPERTIES",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
            "android.permission.ACCESS_MOCK_LOCATION",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_SURFACE_FLINGER",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.ACCOUNT_MANAGER",
            "android.permission.AUTHENTICATE_ACCOUNTS",
            "android.permission.BATTERY_STATS",
            "android.permission.BIND_APPWIDGET",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.BIND_INPUT_METHOD",
            "android.permission.BIND_WALLPAPER",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BRICK",
            "android.permission.BROADCAST_PACKAGE_REMOVED",
            "android.permission.BROADCAST_SMS",
            "android.permission.BROADCAST_STICKY",
            "android.permission.BROADCAST_WAP_PUSH",
            "android.permission.CALL_PHONE",
            "android.permission.CALL_PRIVILEGED",
            "android.permission.CAMERA",
            "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
            "android.permission.CHANGE_CONFIGURATION",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CLEAR_APP_CACHE",
            "android.permission.CLEAR_APP_USER_DATA",
            "android.permission.CONTROL_LOCATION_UPDATES",
            "android.permission.DELETE_CACHE_FILES",
            "android.permission.DELETE_PACKAGES",
            "android.permission.DEVICE_POWER",
            "android.permission.DIAGNOSTIC",
            "android.permission.DISABLE_KEYGUARD",
            "android.permission.DUMP",
            "android.permission.EXPAND_STATUS_BAR",
            "android.permission.FACTORY_TEST",
            "android.permission.FLASHLIGHT",
            "android.permission.FORCE_BACK",
            "android.permission.GET_ACCOUNTS",
            "android.permission.GET_PACKAGE_SIZE",
            "android.permission.GET_TASKS",
            "android.permission.GLOBAL_SEARCH",
            "android.permission.HARDWARE_TEST",
            "android.permission.INJECT_EVENTS",
            "android.permission.INSTALL_LOCATION_PROVIDER",
            "android.permission.INSTALL_PACKAGES",
            "android.permission.INTERNAL_SYSTEM_WINDOW",
            "android.permission.INTERNET",
            "android.permission.KILL_BACKGROUND_PROCESSES",
            "android.permission.MANAGE_ACCOUNTS",
            "android.permission.MANAGE_APP_TOKENS",
            "android.permission.MASTER_CLEAR",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.MODIFY_PHONE_STATE",
            "android.permission.MOUNT_FORMAT_FILESYSTEMS",
            "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
            "android.permission.PERSISTENT_ACTIVITY",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.READ_CALENDAR",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_FRAME_BUFFER",
            "com.android.browser.permission.READ_HISTORY_BOOKMARKS",
            "android.permission.READ_INPUT_STATE",
            "android.permission.READ_LOGS",
            "android.permission.READ_OWNER_DATA",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_SMS",
            "android.permission.READ_SYNC_SETTINGS",
            "android.permission.READ_SYNC_STATS",
            "android.permission.REBOOT",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECORD_AUDIO",
            "android.permission.REORDER_TASKS",
            "android.permission.RESTART_PACKAGES",
            "android.permission.SEND_SMS",
            "android.permission.SET_ACTIVITY_WATCHER",
            "android.permission.SET_ALWAYS_FINISH",
            "android.permission.SET_ANIMATION_SCALE",
            "android.permission.SET_DEBUG_APP",
            "android.permission.SET_ORIENTATION",
            "android.permission.SET_PREFERRED_APPLICATIONS",
            "android.permission.SET_PROCESS_LIMIT",
            "android.permission.SET_TIME",
            "android.permission.SET_TIME_ZONE", "android.permission.SET_WALLPAPER",
            "android.permission.SET_WALLPAPER_HINTS",
            "android.permission.SIGNAL_PERSISTENT_PROCESSES",
            "android.permission.STATUS_BAR",
            "android.permission.SUBSCRIBED_FEEDS_READ",
            "android.permission.SUBSCRIBED_FEEDS_WRITE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.UPDATE_DEVICE_STATS",
            "android.permission.USE_CREDENTIALS",
            "android.permission.VIBRATE",
            "android.permission.WAKE_LOCK",
            "android.permission.WRITE_APN_SETTINGS",
            "android.permission.WRITE_CALENDAR",
            "android.permission.WRITE_CONTACTS",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.WRITE_GSERVICES",
            "com.android.browser.permission.WRITE_HISTORY_BOOKMARKS",
            "android.permission.WRITE_OWNER_DATA",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.WRITE_SETTINGS",
            "android.permission.WRITE_SMS",
            "android.permission.WRITE_SYNC_SETTINGS"
    ));
}

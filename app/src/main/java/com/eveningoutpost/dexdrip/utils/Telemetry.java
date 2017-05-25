package com.eveningoutpost.dexdrip.utils;

import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.eveningoutpost.dexdrip.NFCReaderX;
import com.eveningoutpost.dexdrip.stats.StatsResult;
import com.eveningoutpost.dexdrip.xdrip;

import static com.eveningoutpost.dexdrip.Models.JoH.getVersionDetails;

/**
 * Created by jamorham on 31/01/2017.
 */

public class Telemetry {

    private static final String TAG = Telemetry.class.getSimpleName();

    /*

    No personal information is sent.

    Only the level of success in receiving sensor data and
    the make/model/version/settings/type of phone and collector used.

    This is to try to find any patterns relating to successful combinations, for example
    G5 collection working better with Samsung devices or not.

     */

    public static void sendCaptureReport() {
        try {
            if (JoH.ratelimit("capture-report", 50000)) {
                Log.d(TAG, "SEND EVENT START");

                if (Home.getPreferencesBooleanDefaultFalse("enable_crashlytics") && Home.getPreferencesBooleanDefaultFalse("enable_telemetry")) {

                    final Sensor sensor = Sensor.currentSensor();

                    if (sensor != null) {
                        if (JoH.msSince(sensor.started_at) > 86400000) {

                            final StatsResult statsResult = new StatsResult(PreferenceManager.getDefaultSharedPreferences(xdrip.getAppContext()), true);
                            final int capture_percentage = statsResult.getCapturePercentage();
                            final int capture_set = (capture_percentage / 10) * 10;

                            if (capture_set > 60) {
                                final boolean use_transmiter_pl_bluetooth = Home.getPreferencesBooleanDefaultFalse("use_transmiter_pl_bluetooth");
                                final boolean use_rfduino_bluetooth = Home.getPreferencesBooleanDefaultFalse("use_rfduino_bluetooth");
                                final String subtype = (use_transmiter_pl_bluetooth ? "TR" : "") + (use_rfduino_bluetooth ? "RF" : "") + (Home.get_forced_wear() ? "W" : "") + (NFCReaderX.used_nfc_successfully ? "N" : "");
                                final String capture_id = DexCollectionType.getDexCollectionType().toString() + subtype + " Captured " + capture_set;

                                Log.d(TAG, "SEND CAPTURE EVENT PROCESS: " + capture_id);
                                String watch_model = "";

                                if (Home.get_forced_wear()) {
                                    // anonymize watch model
                                    final String wear_node = Home.getPreferencesStringDefaultBlank("node_wearG5");
                                    if (wear_node.length() > 0) {
                                        final String[] wear_array = wear_node.split(" ");
                                        for (String ii : wear_array) {
                                            if (!ii.contains("|"))
                                                watch_model = watch_model + ii;
                                        }
                                    }
                                }
                                if (watch_model.length() > 0) {
                                    Answers.getInstance().logCustom(new CustomEvent(capture_id)
                                            .putCustomAttribute("Model", Build.MODEL + " " + Build.VERSION.RELEASE)
                                            .putCustomAttribute("Manufacturer", Build.MANUFACTURER)
                                            .putCustomAttribute("Version", Build.VERSION.RELEASE)
                                            .putCustomAttribute("xDrip", getVersionDetails())
                                            .putCustomAttribute("Watch", watch_model)
                                            .putCustomAttribute("Percentage", capture_percentage));
                                } else {
                                    Answers.getInstance().logCustom(new CustomEvent(capture_id)
                                            .putCustomAttribute("Model", Build.MODEL + " " + Build.VERSION.RELEASE)
                                            .putCustomAttribute("Manufacturer", Build.MANUFACTURER)
                                            .putCustomAttribute("Version", Build.VERSION.RELEASE)
                                            .putCustomAttribute("xDrip", getVersionDetails())
                                            .putCustomAttribute("Percentage", capture_percentage));
                                }
                            }
                        } else {
                            Log.d(TAG, "Sensor not running for more than 24 hours yet");
                        }
                    } else {
                        Log.d(TAG, "No sensor active");
                    }
                    Log.d(TAG, "SEND EVENT DONE");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Got exception sending Capture Report");
        }

    }

}

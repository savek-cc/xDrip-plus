package com.eveningoutpost.dexdrip;

/**
 * Created by jamorham on 11/01/16.
 */

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.eveningoutpost.dexdrip.Models.TransmitterData;
import com.eveningoutpost.dexdrip.Models.Treatments;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.utils.CipherUtils;
import com.eveningoutpost.dexdrip.utils.Preferences;
import com.eveningoutpost.dexdrip.utils.WebAppHelper;
import com.google.android.gms.gcm.GcmPubSub;

import java.util.Date;


public class GcmListenerSvc extends com.google.android.gms.gcm.GcmListenerService {

    private static final String TAG = "jamorham GCMlis";
    private static SharedPreferences prefs;
    private static byte[] staticKey;
    public static double lastMessageReceived = 0;

    @Override
    public void onMessageReceived(String from, Bundle data) {

        if (data == null) return;
        final PowerManager.WakeLock wl = JoH.getWakeLock("xdrip-onMsgRec",120000);

        if (from == null) from="null";
        String message = data.getString("message");

        Log.d(TAG, "From: " + from);
        if (message != null) { Log.d(TAG, "Message: " + message); } else { message = "null"; }

        Bundle notification = data.getBundle("notification");
        if (notification != null) {
            Log.d(TAG, "Processing notification bundle");
            try {
                sendNotification(notification.getString("body"), notification.getString("title"));
            } catch (NullPointerException e) {
                Log.d(TAG, "Null pointer exception within sendnotification");
            }
        }

        if (from.startsWith(getString(R.string.gcmtpc))) {

            String xfrom = data.getString("xfrom");
            String payload = data.getString("datum");
            String action = data.getString("action");

            if ((xfrom!=null) && (xfrom.equals(GcmActivity.token))) {
                GcmActivity.queueAction(action + payload);
                JoH.releaseWakeLock(wl);
                return;
            }

            String[] tpca = from.split("/");
            if ((tpca[2] != null) && (tpca[2].length() > 30) && (!tpca[2].equals(GcmActivity.myIdentity()))) {
                Log.e(TAG, "Received invalid channel: " + from + " instead of: " + GcmActivity.myIdentity());
                if ((GcmActivity.myIdentity() != null) && (GcmActivity.myIdentity().length() > 30)) {
                    try {
                        GcmPubSub.getInstance(this).unsubscribe(GcmActivity.token, from);
                    } catch (Exception e) {
                        Log.e(TAG, "Exception unsubscribing: " + e.toString());
                    }
                }
                JoH.releaseWakeLock(wl);
                return;
            }

            if (payload == null) payload="";

            if (payload.length() > 16) {
                if (GoogleDriveInterface.keyInitialized()) {
                    String decrypted_payload = CipherUtils.decryptString(payload);
                    if (decrypted_payload.length() > 0) {
                        payload = decrypted_payload;
                    } else {
                        Log.e(TAG, "Couldn't decrypt payload!");
                        payload = "";
                        Home.toaststaticnext("Having problems decrypting incoming data - check keys");
                    }
                } else {
                    Log.e(TAG, "Couldn't decrypt as key not initialized");
                    payload = "";
                }
            }

            Log.i(TAG, "Got action: " + action + " with payload: " + payload);
            lastMessageReceived = JoH.ts();

            if (action==null) action="null";
            // new treatment
            if (action.equals("nt") && (payload != null)) {
                Log.i(TAG, "Attempting GCM push to Treatment");
                GcmActivity.pushTreatmentFromPayloadString(payload);
            } else if (action.equals("dat")) {
                Log.i(TAG, "Attempting GCM delete all treatments");
                Treatments.delete_all();
            } else if (action.equals("dt")) {
                Log.i(TAG, "Attempting GCM delete specific treatment");
                Treatments.delete_by_uuid(filter(payload));
            } else if (action.equals("clc"))
            {
                Log.i(TAG,"Attempting to clear last calibration");
                Calibration.clearLastCalibration();
            } else if (action.equals("cal")) {
                String[] message_array = filter(payload).split("\\s+");
                if ((message_array.length == 3) && (message_array[0].length() > 0)
                        && (message_array[1].length() > 0) && (message_array[2].length() > 0)) {
                    // [0]=timestamp [1]=bg_String [2]=bgAge
                    Intent calintent = new Intent();
                    calintent.setClassName(getString(R.string.local_target_package), "com.eveningoutpost.dexdrip.AddCalibration");
                    calintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    long timediff = (long) ((new Date().getTime() - Double.parseDouble(message_array[0])) / 1000);
                    Log.i(TAG, "Remote calibration latency calculated as: " + Long.toString(timediff) + " seconds");
                    if (timediff > 0) {
                        message_array[2] = Long.toString(Long.parseLong(message_array[2]) + timediff);
                    }
                    Log.i(TAG, "Processing remote CAL " + message_array[1] + " age: " + message_array[2]);
                    calintent.putExtra("bg_string", message_array[1]);
                    calintent.putExtra("bg_age", message_array[2]);
                    if (timediff < 3600) {
                        getApplicationContext().startActivity(calintent);
                    }
                } else {
                    Log.e(TAG, "Invalid CAL payload");
                }
            } else if (action.equals("ping")) {
                // don't respond to wakeup pings
            } else if (action.equals("p")) {
                GcmActivity.send_ping_reply();
            } else if (action.equals("q")) {
                Home.toaststatic("Received ping reply");
            } else if (action.equals("plu")) {
                // process map update
                if (Home.get_follower()) {
                    MapsActivity.newMapLocation(payload, (long) JoH.ts());
                }
            } else if (action.equals("sbu")) {
                if (Home.get_follower()) {
                    Log.i(TAG, "Received sensor battery level update");
                    Sensor.updateBatteryLevel(Integer.parseInt(payload), true);
                    TransmitterData.updateTransmitterBatteryFromSync(Integer.parseInt(payload));
                }
            } else if (action.equals("bbu")) {
                if (Home.get_follower()) {
                    Log.i(TAG, "Received bridge battery level update");
                    Home.setPreferencesInt("bridge_battery", Integer.parseInt(payload));
                }
            } else if (action.equals("sbr")) {
                if ((Home.get_master())  && JoH.ratelimit("gcm-sbr",300)) {
                    Log.i(TAG, "Received sensor battery request");
                    try {
                        TransmitterData td = TransmitterData.last();
                        if ((td != null) && (td.sensor_battery_level != 0)) {
                            GcmActivity.sendSensorBattery(td.sensor_battery_level);
                        } else {
                            GcmActivity.sendSensorBattery(Sensor.currentSensor().latest_battery_level);
                        }
                    } catch (NullPointerException e ) {
                        Log.e(TAG,"Cannot send sensor battery as sensor is null");
                    }
                }
            } else if (action.equals("bgs")) {
                Log.i(TAG, "Received BG packet(s)");
                if (Home.get_follower()) {
                    String bgs[] = payload.split("\\^");
                    for (String bgr : bgs) {
                        BgReading.bgReadingInsertFromJson(bgr);
                    }
                } else {
                    Log.e(TAG,"Received remote BG packet but we are not set as a follower");
                }
               // Home.staticRefreshBGCharts();
            } else if (action.equals("bfb")) {
                initprefs();
                String bfb[] = payload.split("\\^");
                if (prefs.getString("dex_collection_method", "").equals("Follower")) {
                    Log.i(TAG, "Processing backfill location packet as we are a follower");
                    staticKey = CipherUtils.hexToBytes(bfb[1]);
                    new WebAppHelper(new GcmListenerSvc.ServiceCallback()).executeOnExecutor(xdrip.executor,getString(R.string.wserviceurl) + "/joh-getsw/" + bfb[0]);
                } else {
                    Log.i(TAG, "Ignoring backfill location packet as we are not follower");
                }
            } else if (action.equals("bfr")) {
                initprefs();
                if (prefs.getBoolean("plus_follow_master", false)) {
                    Log.i(TAG, "Processing backfill location request as we are master");
                    GcmActivity.syncBGTable2();
                }
            } else {
                Log.e(TAG, "Received message action we don't know about: " + action);
            }
        } else {
            // direct downstream message.
            Log.i(TAG, "Received downstream message: " + message);
        }

        JoH.releaseWakeLock(wl);
    }

    public class ServiceCallback implements Preferences.OnServiceTaskCompleted {
        @Override
        public void onTaskCompleted(byte[] result) {
            final PowerManager.WakeLock wl = JoH.getWakeLock("xdrip-gcm-callback",60000);
            try {
                if (result.length > 0) {
                    if ((staticKey == null) || (staticKey.length != 16)) {
                        Log.e(TAG, "Error processing security key");
                    } else {
                        byte[] plainbytes = JoH.decompressBytesToBytes(CipherUtils.decryptBytes(result, staticKey));
                        staticKey = null;
                        UserError.Log.d(TAG, "Plain bytes size: " + plainbytes.length);
                        if (plainbytes.length > 0) {
                            GcmActivity.processBFPbundle(new String(plainbytes, 0, plainbytes.length, "UTF-8"));
                        } else {
                            Log.e(TAG, "Error processing data - empty");
                        }
                    }
                } else {
                    Log.e(TAG, "Error processing - no data - try again?");
                }
            } catch (Exception e) {
                Log.e(TAG, "Got error in BFP callback: " + e.toString());
            } finally {
                JoH.releaseWakeLock(wl);
            }
        }
    }

    private void initprefs() {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        }
    }

    public static int lastMessageMinutesAgo()
    {
        return (int)((JoH.ts() - GcmListenerSvc.lastMessageReceived) / 60000);
    }

    private void sendNotification(String body, String title) {
        Intent intent = new Intent(this, Home.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }

    private String filter(String source) {
        if (source == null) return null;
        return source.replaceAll("[^a-zA-Z0-9 _.-]", "");
    }

}


package com.eveningoutpost.dexdrip;

import android.app.Application;
import android.content.Context;
import android.preference.PreferenceManager;

import com.activeandroid.ActiveAndroid;

import java.util.Locale;

//import io.fabric.sdk.android.Fabric;

/**
 * Created by Emma Black on 3/21/15.
 */

public class xdrip extends Application {

    private static final String TAG = "xdrip.java";
    private static Context context;
    private static boolean fabricInited = false;

    @Override
    public void onCreate() {
        xdrip.context = getApplicationContext();
        super.onCreate();
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        ActiveAndroid.initialize(this);
     }

    public static Context getAppContext() {
        return xdrip.context;
    }

    public static boolean checkAppContext(Context context) {
        if (getAppContext() == null) {
            xdrip.context = context;
            return false;
        } else {
            return true;
        }
    }
}

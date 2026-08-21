package com.cookie.securenotes.manager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.cookie.securenotes.data.local.prefs.SecurePrefsManager;
import com.cookie.securenotes.ui.login.LoginActivity;

public class LockManager {
    private static final String PREF_LOCK_TIME = "lock_pause_time";
    private static final int MAX_MINUTES = 30;
    
    private static LockManager instance;
    private final SharedPreferences sharedPreferences;
    private final SecurePrefsManager securePrefsManager;
    private int pauseTimeMinutes;

    private LockManager(Context context) {
        sharedPreferences = context.getSharedPreferences("lock_settings", Context.MODE_PRIVATE);
        securePrefsManager = new SecurePrefsManager(context);
        loadSettings();
    }

    public static synchronized LockManager getInstance(Context context) {
        if (instance == null) {
            instance = new LockManager(context.getApplicationContext());
        }
        return instance;
    }

    private void loadSettings() {
        pauseTimeMinutes = sharedPreferences.getInt(PREF_LOCK_TIME, 5); // Default 5 min
    }

    public void savePauseTime(int minutes) {
        if (minutes > MAX_MINUTES) {
            minutes = MAX_MINUTES;
        }
        pauseTimeMinutes = minutes;
        sharedPreferences.edit().putInt(PREF_LOCK_TIME, minutes).apply();
    }

    public int getPauseTimeMinutes() {
        return pauseTimeMinutes;
    }

    public void savePin(String pin) {
        securePrefsManager.savePin(pin);
    }

    public void lockApp(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
package com.cookie.securenotes.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {

    private static final String PREF_NAME = "app_settings";

    // quante note anteprima ci devono essere
    private static final String KEY_RECENT_NOTES_COUNT = "recent_notes_count";
    private static final int DEFAULT_RECENT_NOTES_COUNT = 5;

    // quanto dura la sessione prima dei lock
    private static final String KEY_TIMEOUT_MINUTES = "timeout_minutes";
    private static final int DEFAULT_TIMEOUT_MINUTES = 3;

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getRecentNotesCount() {
        return prefs.getInt(KEY_RECENT_NOTES_COUNT, DEFAULT_RECENT_NOTES_COUNT);
    }

    public void setRecentNotesCount(int count) {
        prefs.edit().putInt(KEY_RECENT_NOTES_COUNT, count).apply();
    }


    public int getTimeoutMinutes() {
        return prefs.getInt(KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES);
    }

    public void setTimeoutMinutes(int minutes) {
        prefs.edit().putInt(KEY_TIMEOUT_MINUTES, minutes).apply();
    }
}
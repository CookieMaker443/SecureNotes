package com.cookie.securenotes.session;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.TimeUnit;

public class LockManager {

    private static LockManager instance;

    public static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3);
    public static final long MAX_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long CHECK_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private long lastInteractionTime = System.currentTimeMillis();
    private long backgroundedAt = -1; // -1 = non attualmente in background

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean checking = false;
    private LockListener lockListener;

    private LockManager() {}

    public static synchronized LockManager getInstance() {
        if (instance == null) {
            instance = new LockManager();
        }
        return instance;
    }

    // interfaccia annindata che serve per indicare il legame tra questa classe e questo metodo
    // questo metmodo viene overridato
    public interface LockListener {
        void onLockTriggered();
    }

    public void setLockListener(LockListener listener) {
        this.lockListener = listener;
    }

    public void setTimeoutMinutes(int minutes) {
        timeoutMillis = Math.min(TimeUnit.MINUTES.toMillis(minutes), MAX_TIMEOUT_MILLIS);
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /** Da chiamare subito dopo SecureSession.unlock(). */
    public void start() {
        lastInteractionTime = System.currentTimeMillis();
        backgroundedAt = -1;
        if (!checking) {
            checking = true;
            handler.postDelayed(periodicCheck, CHECK_INTERVAL_MILLIS);
        }
    }

    /** Da chiamare quando la sessione si chiude (timeout o logout manuale). */
    public void stop() {
        checking = false;
        handler.removeCallbacks(periodicCheck);
    }

    // ---- Meccanismo 2: uso continuo, controllo periodico ogni ~30s ----

    public void notifyInteraction() {
        lastInteractionTime = System.currentTimeMillis();
    }

    private final Runnable periodicCheck = new Runnable() {
        @Override
        public void run() {
            if (!checking) return;

            long elapsed = System.currentTimeMillis() - lastInteractionTime;
            if (elapsed >= timeoutMillis) {
                triggerLock();
                return; // non ripianifica: start() lo farà al prossimo unlock
            }
            handler.postDelayed(this, CHECK_INTERVAL_MILLIS);
        }
    };

    // ---- Meccanismo 1: tempo passato in background ----

    /** Da chiamare da onStop() di ogni Activity. */
    public void notifyAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis();
    }

    /** Da chiamare da onResume() di ogni Activity. */
    public void notifyAppForegrounded() {
        if (backgroundedAt == -1) return;

        long elapsed = System.currentTimeMillis() - backgroundedAt;
        backgroundedAt = -1;

        if (checking && elapsed >= timeoutMillis) {
            triggerLock();
        } else {
            notifyInteraction(); // rientro in tempo = conta come interazione valida
        }
    }

    private void triggerLock() {
        checking = false;
        handler.removeCallbacks(periodicCheck);
        if (lockListener != null) {
            lockListener.onLockTriggered();
        }
    }
}
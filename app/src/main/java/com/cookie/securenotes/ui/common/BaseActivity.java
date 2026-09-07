package com.cookie.securenotes.ui.common;

import android.content.Intent;
import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;

import com.cookie.securenotes.session.LockManager;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.ui.login.LoginActivity;

public abstract class BaseActivity extends AppCompatActivity {
// necesssita che le varie activity ereditino BaseActivity invece di AppCompatActivity
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        LockManager.getInstance().notifyInteraction();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LockManager.getInstance().notifyAppForegrounded();

        // Se il lock è scattato mentre l'app era in background,
        // non lasciare l'utente su una schermata con dati sbloccati.
        if (!SecureSession.getInstance().isUnlocked()) {
            goToLogin();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LockManager.getInstance().notifyAppBackgrounded();
    }

    protected void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
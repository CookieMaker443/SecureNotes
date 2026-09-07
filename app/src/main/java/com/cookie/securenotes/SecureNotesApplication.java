package com.cookie.securenotes;

import android.app.Application;
import android.content.Intent;

import com.cookie.securenotes.session.LockManager;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.ui.login.LoginActivity;

public class SecureNotesApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        LockManager.getInstance().setLockListener(() -> {
            SecureSession.getInstance().lock();

            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
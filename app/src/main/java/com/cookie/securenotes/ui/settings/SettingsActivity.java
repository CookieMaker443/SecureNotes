package com.cookie.securenotes.ui.settings;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.prefs.AppSettings;
import com.cookie.securenotes.data.local.prefs.SecurePrefsException;
import com.cookie.securenotes.data.local.prefs.SecurePrefsManager;
import com.cookie.securenotes.session.LockManager;
import com.cookie.securenotes.ui.common.BaseActivity;

import java.util.concurrent.Executor;

public class SettingsActivity extends BaseActivity {

    private static final int MIN_TIMEOUT_MINUTES = 3;
    private static final int MAX_TIMEOUT_MINUTES = 30;

    private LockManager lockManager;
    private AppSettings appSettings;
    private SecurePrefsManager prefsManager;

    private EditText editTimeoutMinutes;
    private EditText editRecentNotesCount;
    private LinearLayout newPinSection;
    private EditText editNewPin;
    private EditText editConfirmPin;
    private boolean biometricVerified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        lockManager = LockManager.getInstance();
        appSettings = new AppSettings(this);
        try {
            prefsManager = new SecurePrefsManager(this);
        } catch (SecurePrefsException e) {
            Toast.makeText(this, getString(R.string.toast_settings_init_error), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        editTimeoutMinutes = findViewById(R.id.editTimeoutMinutes);
        editRecentNotesCount = findViewById(R.id.editRecentNotesCount);
        newPinSection = findViewById(R.id.newPinSection);
        editNewPin = findViewById(R.id.editNewPin);
        editConfirmPin = findViewById(R.id.editConfirmPin);
        Button btnVerifyBiometric = findViewById(R.id.btnVerifyBiometric);
        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);
        Button btnExportBackup = findViewById(R.id.btnExportBackup);

        // Precompila con i valori attuali
        editTimeoutMinutes.setText(String.valueOf(appSettings.getTimeoutMinutes()));
        editRecentNotesCount.setText(String.valueOf(appSettings.getRecentNotesCount()));

        btnVerifyBiometric.setOnClickListener(v -> showBiometricPrompt());
        btnSaveSettings.setOnClickListener(v -> saveSettings());

        // Backup: da implementare in seguito, per ora disabilitato
        btnExportBackup.setEnabled(false);
        btnExportBackup.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.toast_backup_not_ready), Toast.LENGTH_SHORT).show());
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.auth_error_prefix, errString), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        biometricVerified = true;
                        newPinSection.setVisibility(android.view.View.VISIBLE);
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.toast_biometric_verified), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(getApplicationContext(), getString(R.string.auth_failed), Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_change_pin_subtitle))
                .setNegativeButtonText(getString(R.string.action_cancel))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void saveSettings() {
        // ---- Timeout inattività ----
        String timeoutStr = editTimeoutMinutes.getText().toString();
        if (!timeoutStr.isEmpty()) {
            int minutes;
            try {
                minutes = Integer.parseInt(timeoutStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.error_invalid_number), Toast.LENGTH_SHORT).show();
                return;
            }
            minutes = Math.max(MIN_TIMEOUT_MINUTES, Math.min(minutes, MAX_TIMEOUT_MINUTES));
            appSettings.setTimeoutMinutes(minutes);
            lockManager.setTimeoutMinutes(minutes); // aggiorna subito la sessione già in corso
            editTimeoutMinutes.setText(String.valueOf(minutes)); // riflette l'eventuale clamp
        }

        // ---- Numero di note recenti in dashboard ----
        String recentStr = editRecentNotesCount.getText().toString();
        if (!recentStr.isEmpty()) {
            try {
                int count = Integer.parseInt(recentStr);
                if (count < 1) count = 1;
                appSettings.setRecentNotesCount(count);
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.error_invalid_number), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // ---- Cambio PIN (solo se la biometria è stata verificata) ----
        if (biometricVerified) {
            String newPin = editNewPin.getText().toString();
            String confirmPin = editConfirmPin.getText().toString();

            if (newPin.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_pin_required), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPin.equals(confirmPin)) {
                Toast.makeText(this, getString(R.string.error_pin_mismatch), Toast.LENGTH_SHORT).show();
                return;
            }

            prefsManager.savePin(newPin);
        }

        Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
package com.cookie.securenotes.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.prefs.SecurePrefsManager;
import com.cookie.securenotes.ui.dashboard.DashboardActivity;

import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    private SecurePrefsManager prefsManager;
    private EditText pinInput;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefsManager = new SecurePrefsManager(this);

        pinInput = findViewById(R.id.pinInput);
        loginButton = findViewById(R.id.loginButton);
        Button biometricButton = findViewById(R.id.biometricButton);

        loginButton.setOnClickListener(v -> handlePinLogin());
        biometricButton.setOnClickListener(v -> showBiometricPrompt());

        if (!prefsManager.hasPin()) {
            Toast.makeText(this, getString(R.string.toast_set_new_pin), Toast.LENGTH_SHORT).show();
            loginButton.setText(getString(R.string.btn_set_pin));
        } else {
            if (prefsManager.isBiometricEnabled()) {
                showBiometricPrompt();
            }
        }
    }

    private void handlePinLogin() {
        String pin = pinInput.getText().toString();
        if (pin.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_enter_pin), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!prefsManager.hasPin()) {
            prefsManager.savePin(pin);
            prefsManager.setBiometricEnabled(true);
            Toast.makeText(this, getString(R.string.toast_pin_saved), Toast.LENGTH_SHORT).show();
            navigateToDashboard();
        } else {
            if (prefsManager.isPinCorrect(pin)) {
                navigateToDashboard();
            } else {
                Toast.makeText(this, getString(R.string.toast_incorrect_pin), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(LoginActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(), getString(R.string.auth_error_prefix, errString), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                navigateToDashboard();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), getString(R.string.auth_failed), Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setNegativeButtonText(getString(R.string.biometric_prompt_negative_text))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }
}
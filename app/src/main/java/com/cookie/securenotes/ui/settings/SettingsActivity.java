package com.cookie.securenotes.ui.settings;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cookie.securenotes.R;
import com.cookie.securenotes.manager.LockManager;

public class SettingsActivity extends AppCompatActivity {

    private EditText editNewPin;
    private EditText editPauseTime;
    private LockManager lockManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settigs);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        lockManager = LockManager.getInstance(this);
        editNewPin = findViewById(R.id.editNewPin);
        editPauseTime = findViewById(R.id.editPauseTime);
        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);

        editPauseTime.setText(String.valueOf(lockManager.getPauseTimeMinutes()));

        btnSaveSettings.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        String newPin = editNewPin.getText().toString();
        String pauseStr = editPauseTime.getText().toString();

        if (!newPin.isEmpty()) {
            lockManager.savePin(newPin);
        }

        if (!pauseStr.isEmpty()) {
            int minutes = Integer.parseInt(pauseStr);
            lockManager.savePauseTime(minutes);
        }

        Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
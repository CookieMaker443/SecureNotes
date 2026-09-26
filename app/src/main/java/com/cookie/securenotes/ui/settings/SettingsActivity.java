package com.cookie.securenotes.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.prefs.AppSettings;
import com.cookie.securenotes.data.local.prefs.SecurePrefsException;
import com.cookie.securenotes.data.local.prefs.SecurePrefsManager;
import com.cookie.securenotes.session.LockManager;
import com.cookie.securenotes.ui.common.BaseActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

public class SettingsActivity extends BaseActivity {

    private static final int MIN_TIMEOUT_MINUTES = 3;
    private static final int MAX_TIMEOUT_MINUTES = 30;
    private static final int MIN_BACKUP_PASSWORD_LENGTH = 8;

    private LockManager lockManager;
    private AppSettings appSettings;
    private SecurePrefsManager prefsManager;
    private BackupViewModel backupViewModel; // sostituisce l'ExporterManager tenuto direttamente dall'Activity

    private EditText editTimeoutMinutes;
    private EditText editRecentNotesCount;
    private LinearLayout newPinSection;
    private EditText editNewPin;
    private EditText editConfirmPin;
    private boolean biometricVerified = false;

    // campi backup — promossi a variabili d'istanza: servono anche nell'observer del ViewModel, per abilitare/disabilitare
    private EditText editBackupPassword;
    private EditText editBackupPasswordConfirm;
    private Button btnExportBackup;
    private Button btnImportBackup;

    // tenuta tra il click su "Esporta" (dove si legge la password) e il ritorno del picker SAF con la destinazione scelta
    private char[] passwordDaEsportare;

    // launcher SAF per scegliere DOVE salvare il backup esportato
    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                if (uri == null) {
                    azzeraPasswordEsportata(); // l'utente ha annullato il picker: la password letta non serve più
                    return;
                }
                backupViewModel.avviaExport(uri, passwordDaEsportare);
                passwordDaEsportare = null; // il riferimento passa al ViewModel, che se ne occupa (azzeramento incluso)
            });

    // launcher SAF per scegliere QUALE file .secnotes importare
    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return; // annullato
                mostraDialogPasswordImport(uri);
            });

    // NUOVO: intercetta il tasto indietro (fisico o di sistema) per avvisare se un'operazione è in corso
    private final OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            confermaUscita();
        }
    };

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

        // il ViewModel sopravvive a una rotazione schermo (stessa Activity, ricreata) — non a un'uscita vera dalla schermata
        backupViewModel = new ViewModelProvider(this).get(BackupViewModel.class);

        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        editTimeoutMinutes = findViewById(R.id.editTimeoutMinutes);
        editRecentNotesCount = findViewById(R.id.editRecentNotesCount);
        newPinSection = findViewById(R.id.newPinSection);
        editNewPin = findViewById(R.id.editNewPin);
        editConfirmPin = findViewById(R.id.editConfirmPin);
        Button btnVerifyBiometric = findViewById(R.id.btnVerifyBiometric);
        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);
        btnExportBackup = findViewById(R.id.btnExportBackup);
        btnImportBackup = findViewById(R.id.btnImportBackup);
        editBackupPassword = findViewById(R.id.editBackupPassword);
        editBackupPasswordConfirm = findViewById(R.id.editBackupPasswordConfirm);

        // Precompila con i valori attuali
        editTimeoutMinutes.setText(String.valueOf(appSettings.getTimeoutMinutes()));
        editRecentNotesCount.setText(String.valueOf(appSettings.getRecentNotesCount()));

        btnVerifyBiometric.setOnClickListener(v -> showBiometricPrompt());
        btnSaveSettings.setOnClickListener(v -> saveSettings());
        btnExportBackup.setOnClickListener(v -> validaEAvviaExport());
        btnImportBackup.setOnClickListener(v -> openBackupLauncher.launch(new String[]{"*/*"})); // .secnotes non è un MIME registrato

        osservaStatoBackup();
    }

    /** Un solo observer per tutta la UI del backup: abilita/disabilita i pulsanti e mostra l'esito. */
    private void osservaStatoBackup() {
        backupViewModel.getUiState().observe(this, stato -> {
            boolean inCorso = stato.stato == BackupViewModel.Stato.IN_CORSO;

            // mentre un export/import gira, non si può avviarne un altro né toccare le password
            btnExportBackup.setEnabled(!inCorso);
            btnImportBackup.setEnabled(!inCorso);
            editBackupPassword.setEnabled(!inCorso);
            editBackupPasswordConfirm.setEnabled(!inCorso);

            if (stato.stato == BackupViewModel.Stato.COMPLETATO || stato.stato == BackupViewModel.Stato.ERRORE) {
                Toast.makeText(this, stato.messaggio, Toast.LENGTH_LONG).show();
                // torna INATTIVO subito dopo aver mostrato il messaggio, altrimenti una rotazione
                // schermo successiva potrebbe far ricomparire lo stesso Toast
                backupViewModel.resetStato();
            }
        });
    }

    // ================== EXPORT ==================

    /** Controlla i due campi password prima di far scegliere all'utente dove salvare il file. */
    private void validaEAvviaExport() {
        String password = editBackupPassword.getText().toString();
        String conferma = editBackupPasswordConfirm.getText().toString();

        if (password.length() < MIN_BACKUP_PASSWORD_LENGTH) {
            Toast.makeText(this, getString(R.string.error_backup_password_too_short), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(conferma)) {
            Toast.makeText(this, getString(R.string.error_backup_password_mismatch), Toast.LENGTH_SHORT).show();
            return;
        }

        // char[] invece di String: si può azzerare esplicitamente dopo l'uso (lo fa BackupViewModel a fine operazione)
        passwordDaEsportare = password.toCharArray();

        // nome suggerito: SecNotes_export_ddMMyy.secnotes
        String data = new SimpleDateFormat("ddMMyy", Locale.getDefault()).format(new Date());
        createBackupLauncher.launch("SecNotes_export_" + data + ".secnotes");
    }

    private void azzeraPasswordEsportata() {
        if (passwordDaEsportare != null) {
            java.util.Arrays.fill(passwordDaEsportare, '\0');
            passwordDaEsportare = null;
        }
    }

    // ================== IMPORT ==================

    /** Mostra un piccolo dialog con un solo campo password, dopo che l'utente ha scelto il file da importare. */
    private void mostraDialogPasswordImport(Uri sorgenteUri) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_password, null);
        EditText editImportPassword = dialogView.findViewById(R.id.editImportPassword);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_import_password_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.action_import_backup), (dialog, which) -> {
                    char[] password = editImportPassword.getText().toString().toCharArray();
                    backupViewModel.avviaImport(sorgenteUri, password);
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    // ================== conferma prima di uscire (NUOVO) ==================

    /**
     * Chiamato sia dal tasto indietro di sistema sia dalla freccia nella toolbar:
     * se un export/import è in corso, avvisa prima di lasciare davvero la schermata.
     */
    private void confermaUscita() {
        if (backupViewModel.isOperazioneInCorso()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.confirm_leave_backup_title))
                    .setMessage(getString(R.string.confirm_leave_backup_message))
                    .setPositiveButton(getString(R.string.action_leave), (dialog, which) -> {
                        backupViewModel.annullaOperazione(); // cancellazione cooperativa, non istantanea
                        finish();
                    })
                    .setNegativeButton(getString(R.string.action_stay), null)
                    .show();
        } else {
            finish();
        }
    }

    // ================== resto della Activity, invariato ==================

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
                        newPinSection.setVisibility(View.VISIBLE);
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
        // NUOVO: passa dalla stessa conferma del tasto indietro, invece di un finish() diretto
        confermaUscita();
        return true;
    }
}

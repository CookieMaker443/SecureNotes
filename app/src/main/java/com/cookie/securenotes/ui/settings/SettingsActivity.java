package com.cookie.securenotes.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.prefs.AppSettings;
import com.cookie.securenotes.data.local.prefs.SecurePrefsException;
import com.cookie.securenotes.data.local.prefs.SecurePrefsManager;
import com.cookie.securenotes.manager.backup.ExporterManager;
import com.cookie.securenotes.security.BackupCryptoException;
import com.cookie.securenotes.session.LockManager;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.ui.common.BaseActivity;
import com.cookie.securenotes.util.AppExecutors;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
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
    private ExporterManager exporterManager; // NUOVO

    private EditText editTimeoutMinutes;
    private EditText editRecentNotesCount;
    private LinearLayout newPinSection;
    private EditText editNewPin;
    private EditText editConfirmPin;
    private boolean biometricVerified = false;

    // campi per la password di backup (solo export)
    private EditText editBackupPassword;
    private EditText editBackupPasswordConfirm;

    // tenuta tra il click su "Esporta" (dove si legge la password) e il ritorno
    // del picker SAF con la destinazione scelta dall'utente
    private char[] passwordDaEsportare;

    // launcher SAF per scegliere DOVE salvare il backup esportato
    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                if (uri == null) {
                    // l'utente ha annullato il picker: si azzera la password già letta, non serve più
                    azzeraPasswordEsportata();
                    return;
                }
                avviaExport(uri);
            });

    // launcher SAF per scegliere QUALE file .secnotes importare
    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return; // annullato
                mostraDialogPasswordImport(uri);
            });

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

        // un solo ExporterManager per tutta la vita di questa Activity, chiuso in onDestroy
        exporterManager = new ExporterManager(SecureSession.getInstance(), getApplicationContext());

        editTimeoutMinutes = findViewById(R.id.editTimeoutMinutes);
        editRecentNotesCount = findViewById(R.id.editRecentNotesCount);
        newPinSection = findViewById(R.id.newPinSection);
        editNewPin = findViewById(R.id.editNewPin);
        editConfirmPin = findViewById(R.id.editConfirmPin);
        Button btnVerifyBiometric = findViewById(R.id.btnVerifyBiometric);
        Button btnSaveSettings = findViewById(R.id.btnSaveSettings);
        Button btnExportBackup = findViewById(R.id.btnExportBackup);
        Button btnImportBackup = findViewById(R.id.btnImportBackup);
        editBackupPassword = findViewById(R.id.editBackupPassword);
        editBackupPasswordConfirm = findViewById(R.id.editBackupPasswordConfirm);

        // Precompila con i valori attuali
        editTimeoutMinutes.setText(String.valueOf(appSettings.getTimeoutMinutes()));
        editRecentNotesCount.setText(String.valueOf(appSettings.getRecentNotesCount()));

        btnVerifyBiometric.setOnClickListener(v -> showBiometricPrompt());
        btnSaveSettings.setOnClickListener(v -> saveSettings());

        // Backup: ora funzionante, non più placeholder
        btnExportBackup.setOnClickListener(v -> validaEAvviaExport());
        btnImportBackup.setOnClickListener(v -> openBackupLauncher.launch(new String[]{"*/*"})); // .secnotes non è un MIME registrato
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

        // char[] invece di lasciarla come String: si può azzerare esplicitamente dopo l'uso
        passwordDaEsportare = password.toCharArray();

        // nome suggerito: SecNotes_export_ddMMyy.secnotes
        String data = new SimpleDateFormat("ddMMyy", Locale.getDefault()).format(new Date());
        createBackupLauncher.launch("SecNotes_export_" + data + ".secnotes");
    }

    /** Chiamato quando l'utente ha scelto dove salvare (Uri di destinazione già confermato dal picker). */
    private void avviaExport(Uri destinazioneUri) {
        char[] password = passwordDaEsportare; // copia locale, per sicurezza in caso di riuso della Activity

        Toast.makeText(this, getString(R.string.msg_export_in_progress), Toast.LENGTH_SHORT).show();

        exporterManager.esegui(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(destinazioneUri)) {
                if (out == null) {
                    throw new java.io.IOException("Impossibile aprire il file di destinazione");
                }
                exporterManager.exportMedia(password, out, null); // null: nessuna barra di progresso in questa versione

                AppExecutors.getInstance().mainThread(() ->
                        Toast.makeText(this, getString(R.string.msg_export_success), Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                AppExecutors.getInstance().mainThread(() ->
                        Toast.makeText(this, getString(R.string.error_export_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            } finally {
                azzeraPasswordEsportata();
            }
        });
    }

    private void azzeraPasswordEsportata() {
        if (passwordDaEsportare != null) {
            Arrays.fill(passwordDaEsportare, '\0'); // sovrascrive la password in RAM, non solo "dimentica" il riferimento
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
                    avviaImport(sorgenteUri, password);
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void avviaImport(Uri sorgenteUri, char[] password) {
        Toast.makeText(this, getString(R.string.msg_import_in_progress), Toast.LENGTH_SHORT).show();

        exporterManager.esegui(() -> {
            try (InputStream in = getContentResolver().openInputStream(sorgenteUri)) {
                if (in == null) {
                    throw new java.io.IOException("Impossibile aprire il file di backup scelto");
                }
                exporterManager.importMedia(password, in, null);

                AppExecutors.getInstance().mainThread(() ->
                        Toast.makeText(this, getString(R.string.msg_import_success), Toast.LENGTH_LONG).show());

            } catch (BackupCryptoException e) {
                // caso specifico: password sbagliata o file corrotto, messaggio dedicato invece di quello generico
                AppExecutors.getInstance().mainThread(() ->
                        Toast.makeText(this, getString(R.string.error_backup_wrong_password), Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                AppExecutors.getInstance().mainThread(() ->
                        Toast.makeText(this, getString(R.string.error_import_failed, e.getMessage()), Toast.LENGTH_LONG).show());

            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    // ================== resto della Activity ==================

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
        finish();
        return true;
    }

    // evita di lasciare appeso il thread dedicato di ExporterManager se la schermata si chiude
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exporterManager != null) {
            exporterManager.cancel();   // interrompe un'eventuale operazione ancora in corso
            exporterManager.shutdown(); // chiude il suo executor dedicato
        }
    }
}

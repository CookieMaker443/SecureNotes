# SettingsActivity

## Ruolo e metodi

Gestisce preferenze della sessione e cambio PIN. onCreate carica AppSettings e SecurePrefsManager, collega pulsanti e disabilita il backup non implementato. showBiometricPrompt richiede conferma biometrica prima di rendere visibile il cambio PIN. saveSettings valida e limita timeout a 3-30 minuti, aggiorna AppSettings e LockManager, salva il numero di note recenti e, dopo verifica biometrica, salva il nuovo PIN. onSupportNavigateUp chiude.

## Collegamenti

È aperta dalla dashboard; usa AppSettings, SecurePrefsManager e LockManager.

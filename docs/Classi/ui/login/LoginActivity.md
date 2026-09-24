# LoginActivity

## Ruolo e metodi

È la schermata d'accesso e non eredita BaseActivity. onCreate inizializza SecurePrefsManager, PIN e biometria. handlePinLogin verifica il PIN o lo salva alla prima configurazione. showBiometricPrompt usa BiometricPrompt e, al successo, sblocca la sessione. unlockSessionAndProceed crea CryptoManager, inizializza SecureSession, la sblocca, avvia e configura LockManager. navigateToDashboard apre DashboardActivity.

## Collegamenti

È la porta della sessione: passa le preferenze sicure a SecureSession e poi il controllo alla dashboard.

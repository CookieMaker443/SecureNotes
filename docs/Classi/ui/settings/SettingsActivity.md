# SettingsActivity

## Ruolo e metodi

Gestisce preferenze della sessione, cambio PIN e l'interfaccia del backup.
`onCreate` carica `AppSettings` e `SecurePrefsManager`, ottiene il
`BackupViewModel` associato alla schermata e collega i controlli. L'Activity non
possiede né esegue direttamente `ExporterManager`.

- `validaEAvviaExport` verifica che la password di backup sia lunga almeno otto
  caratteri e che i due campi coincidano, quindi apre il selettore di destinazione SAF.
- Il launcher di export consegna l'`Uri` scelto e la password al
  `BackupViewModel`; se il picker viene annullato, azzera subito la password.
- `mostraDialogPasswordImport` sceglie un file `.secnotes` e raccoglie la password;
  il `BackupViewModel` avvia il ripristino, azzera il `char[]` al termine e distingue
  password errata/file non valido dagli altri errori.
- `osservaStatoBackup` osserva `LiveData<BackupUiState>`: durante l'operazione
  disabilita i controlli del backup e mostra l'esito finale in un `Toast`.
- `confermaUscita`, usato sia dal tasto indietro sia dalla freccia della toolbar,
  chiede conferma durante un backup in corso e ne richiede l'annullamento cooperativo.
- `showBiometricPrompt` richiede conferma biometrica prima di rendere visibile il cambio
  PIN. `saveSettings` limita il timeout a 3–30 minuti, salva il numero di note recenti
  e, dopo verifica biometrica, aggiorna il PIN.
- La chiusura dell'executor e l'annullamento di sicurezza sono responsabilità di
  `BackupViewModel.onCleared()`.

## Collegamenti

È aperta dalla dashboard; usa `AppSettings`, `SecurePrefsManager`, `LockManager` e
[BackupViewModel](BackupViewModel.md). Per la scelta dei file usa lo Storage Access
Framework tramite `ActivityResultContracts`.

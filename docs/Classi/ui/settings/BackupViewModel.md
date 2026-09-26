# `BackupViewModel`

Package: `com.cookie.securenotes.ui.settings`  
File: `BackupViewModel.java`

## Ruolo

Coordina un solo export o import alla volta per `SettingsActivity`. Mantiene la
logica di esecuzione fuori dall'Activity: possiede un `ExporterManager`, apre gli
stream dal `ContentResolver` usando l'`Application` context e pubblica lo stato
alla UI tramite `LiveData`.

## Stato esposto

`getUiState()` restituisce `LiveData<BackupUiState>`. Lo stato può essere
`INATTIVO`, `IN_CORSO`, `COMPLETATO` o `ERRORE`; per gli ultimi due contiene anche
un messaggio già formattato per l'utente. `resetStato()` evita che un esito già
mostrato venga ripetuto dopo la ricreazione dell'Activity.

## Operazioni

- `avviaExport(Uri, char[])` e `avviaImport(Uri, char[])` eseguono il lavoro
  sull'executor dedicato di `ExporterManager`.
- I risultati `BackupResult` vengono trasformati in messaggi con il numero di
  elementi riusciti e di quelli saltati.
- `OperazioneAnnullataException` produce il messaggio di annullamento; una
  `BackupCryptoException` in import segnala password errata o backup non valido.
- La password viene sempre sovrascritta con zeri nel `finally`.

## Ciclo di vita

Il ViewModel sopravvive a una rotazione, ma non all'uscita da `SettingsActivity`.
`onCleared()` richiede l'annullamento cooperativo e chiude l'executor. Per questo
la schermata chiede conferma prima di uscire durante un'operazione; una futura
migrazione a WorkManager è descritta in `docs/todo/todo-backup-workmanager.md`.

## Dipendenze

Usa `SecureSession`, `ExporterManager`, `AppExecutors.mainThread()` e le stringhe
di risorse. È usato solo da [SettingsActivity](SettingsActivity.md).

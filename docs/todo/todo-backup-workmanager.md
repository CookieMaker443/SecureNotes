# TODO — Backup: migrazione a WorkManager

> Riferimento: `13-livello-backup-export-import.md` per lo stato attuale
> (`ExporterManager` + `BackupViewModel`, funzionante ma legato al ciclo di vita di
> `SettingsActivity`). Questo file descrive solo il *prossimo* passo, non lo stato
> attuale.

## Perché serve

Oggi l'export/import di un backup **non sopravvive** se l'utente naviga via da
`SettingsActivity` (back, freccia in toolbar) mentre l'operazione è in corso: il
`BackupViewModel` viene distrutto insieme all'Activity, e con lui l'operazione si
interrompe (mitigato solo con un avviso di conferma prima di uscire — vedi doc 13).
Per un backup di pochi elementi non è un problema reale; per un archivio grande
(molti video) l'utente potrebbe volerlo lanciare e continuare a usare l'app nel
frattempo. `WorkManager` era già nello stack tecnico previsto dal documento di
progetto originale, mai ancora usato.

## Cosa cambia, in sintesi

- Un `BackupWorker extends Worker` (o `CoroutineWorker` se si introduce Kotlin per
  questa parte — da decidere, il resto del progetto è Java puro) prende il posto
  dell'esecuzione diretta su `ExporterManager.esegui(...)`. La logica dentro
  `ExporterManager` (due passaggi, skip-and-continue, `BackupResult`) resta
  praticamente identica — cambia solo *chi la invoca e da dove*.
- Password ed eventuale `Uri` di destinazione/sorgente vanno passate al `Worker`
  tramite `Data` (`setInputData`) — **attenzione**: `Data` finisce su disco (nel DB
  interno di WorkManager), quindi la password **non può viaggiare in chiaro** in
  quell'oggetto. Da valutare: cifrarla con `CryptoManager` prima di passarla (visto
  che l'app ha già la sua chiave Keystore) e decifrarla dentro il `Worker`, oppure
  un meccanismo di handoff diverso (es. un riferimento a un secret tenuto altrove,
  con TTL breve). Punto da chiarire bene prima di scrivere codice — è l'unica vera
  complicazione non banale di questa migrazione.
- Progresso: `Worker.setProgressAsync(Data)`, osservato da
  `WorkManager.getInstance(context).getWorkInfoByIdLiveData(id)` — sostituisce il
  `ProgressListener` passato oggi come `null`.
- Su Android 12+ (API 31+), un lavoro lungo in background dovrebbe girare come
  **foreground service** con notifica (`setForegroundAsync`), altrimenti il sistema
  può interromperlo. Serve una notifica dedicata ("Backup in corso… 42/120") con
  eventualmente un'azione "Annulla" che chiama `WorkManager.cancelWorkById(id)`.
- `BackupViewModel` resterebbe, ma più leggero: invece di possedere
  `ExporterManager` e gestirne l'executor, si limiterebbe a **enqueue-are** il
  `Worker` e a osservarne il `WorkInfo` — la sopravvivenza alla navigazione non
  dipende più da lui.
- La UI di `SettingsActivity` potrebbe restare quasi invariata: cambia solo cosa
  succede se si esce a metà — non serve più il dialog "annulla l'operazione se
  esci", perché l'operazione **continuerebbe** in background. Il dialog attuale
  andrebbe quindi rimosso o riproposto solo come "vuoi annullare il backup in
  corso?" con scelta esplicita, non più legato all'uscita dalla schermata.

## Cosa NON cambia

- `ExporterManager`, `BackupCrypto`, `BackupManifest`, `CryptoManager.encryptStream`,
  `NoteRepository.importNote`, `FileRepository.importFile` — tutta la logica di
  business/cifratura resta identica. `WorkManager` sostituisce solo
  l'`ExecutorService` dedicato come meccanismo di esecuzione in background.
- Il formato `.secnotes`, il manifest, `skipped_report.txt` — nessun impatto.

## Effort stimato / complessità

Non enorme, ma non banale: un `Worker` nuovo, il problema della password da passare
in modo sicuro (il punto più delicato), la notifica in foreground per Android 12+,
e l'adattamento di `BackupViewModel`/`SettingsActivity` alla nuova fonte di verità
del progresso. Da programmare come blocco a sé, non "en passant" mentre si lavora
su altro.

## Prerequisito prima di iniziare

Aggiungere la dipendenza `androidx.work:work-runtime` (via alias in
`libs.versions.toml`, coerente con le altre dipendenze del progetto) — non ancora
presente nel `build.gradle.kts` nonostante fosse nello stack previsto dal documento
di progetto originale.

# 13 — Livello Backup (Export/Import cifrato)

> Aggiornato allo stato **as-built**: a differenza della prima versione di questo
> documento (design, pre-codice), qui si descrive cosa è stato effettivamente scritto.
> Per il seguito previsto (migrazione a `WorkManager`), vedi il file separato
> `todo-backup-workmanager.md` in `docs/todo/`.

## Scopo

`btnExportBackup` era solo un placeholder (`setEnabled(false)`, vedi doc 09). Ora
`SettingsActivity` ha export e import funzionanti, con un `BackupViewModel` dedicato
che gestisce lo stato dell'operazione.

## Decisioni chiuse

- **Autorizzazione ≠ cifratura**: PIN/biometria dell'app restano cose separate dalla
  password di backup (mai richieste per il backup in sé in questa versione — solo la
  password di backup autorizza/protegge l'operazione).
- **Formato file**: `.secnotes` = zip normale (`java.util.zip`, già nell'SDK) cifrato
  per intero con AES/GCM, chiave derivata dalla password via PBKDF2WithHmacSHA256
  (150.000 iterazioni). Nessuna libreria esterna.
- **Validazione password**: solo in export (lunghezza minima 8 + doppia conferma).
  In import non è possibile validare a priori: si tenta la decifratura, un tag GCM
  che non torna diventa `BackupCryptoException` → messaggio "password errata o file
  non valido".
- **Manifest dentro lo zip** (`manifest.json`) con `schemaVersion`: fa da controllo di
  validità in import.
- **Date preservate** in import (da manifest, non "adesso").
- **UUID sempre rigenerati** in import — mai collisioni col device di destinazione.
- **Ricifratura locale obbligatoria** in import, con il `CryptoManager` del device
  corrente (la chiave Keystore non è mai la stessa tra due device).
- **SAF** per scegliere destinazione (export) e sorgente (import) — nessun permesso
  di storage.
- **Executor dedicato** in `ExporterManager`, separato da `AppExecutors.diskIO()`.
- **Streaming a blocchi** sia in lettura (export) che in scrittura (import).
- **`BackupViewModel` dedicato**, scoped a `SettingsActivity` — vedi sezione apposita.
- **Skip-and-continue per elemento**, con report scritto dentro il backup stesso —
  vedi sezione apposita.

## Formato del file `.secnotes`

```
[salt PBKDF2 — 16 byte, in chiaro] [IV GCM — 12 byte, in chiaro] [ciphertext + tag GCM]
```

Il `ciphertext`, decifrato, è uno zip normale con:

```
manifest.json
skipped_report.txt      <- NUOVO: sempre presente, anche se vuoto ("Nessun elemento saltato.")
notes/n_0, n_1, ...      (contenuto delle note, in chiaro una volta decifrato lo zip)
files/f_0, f_1, ...      (contenuto dei file, in chiaro una volta decifrato lo zip)
```

## Classi coinvolte (stato finale)

| Classe | Package | Ruolo |
| --- | --- | --- |
| `BackupCrypto` | `security` | cifra/decifra il blob `.secnotes` con password (PBKDF2 + AES/GCM streaming) |
| `BackupCryptoException` | `security` | eccezione dedicata: password errata o file corrotto |
| `BackupManifest` (+ `NoteEntry`, `FileEntryMeta`) | `manager.backup` | modello del manifest, serializzazione JSON (`org.json`, già nell'SDK) |
| `ExporterManager` | `manager.backup` | orchestratore: `exportMedia`/`importMedia`, executor dedicato, skip-and-continue |
| `BackupViewModel` | `ui.settings` | **NUOVO** — stato dell'operazione via `LiveData`, possiede l'unico `ExporterManager` di questa sessione UI |
| `CryptoManager.encryptStream` | `security` | aggiunta simmetrica a `decryptStream` (già esistente), per ricifrare a blocchi in import |
| `NoteRepository.importNote` / `FileRepository.importFile` | `manager.repository` | scrivono con UUID nuovo, data preservata dal manifest, passando sempre da `CryptoManager` — mai `ExporterManager` tocca cifratura/DAO direttamente |
| `SecureSession.unlock()` | `session` | pulizia dei residui `secnotes_tmp_*`, stessa rete di sicurezza già in uso per `pdf_view_*`/`video_view_*` |

### `ExporterManager` — firme finali

```java
BackupResult exportMedia(char[] password, OutputStream destinazione, ProgressListener listener)
        throws CryptoException, IOException, BackupCryptoException, JSONException, OperazioneAnnullataException;

BackupResult importMedia(char[] password, InputStream sorgente, ProgressListener listener)
        throws BackupCryptoException, CryptoException, IOException, JSONException, OperazioneAnnullataException;

void cancel();       // cancellazione cooperativa, controllata a inizio di ogni elemento del loop
void esegui(Runnable operazione);
void shutdown();     // chiude l'executor dedicato — chiamato da BackupViewModel.onCleared()
```

`BackupResult` (nested, pubblica): `int totale`, `int riusciti`, `List<String> saltati`
— non più `void`: sia export che import ora restituiscono un esito strutturato,
usato da `BackupViewModel` per comporre il messaggio finale ("12 esportati, 1
saltato").

`OperazioneAnnullataException` (nested, pubblica, checked): sostituisce il vecchio
`if (cancelled) return;` silenzioso. Lanciata ai checkpoint di cancellazione, si
propaga fino a `BackupViewModel`, che la distingue esplicitamente da un errore vero
(messaggio "operazione annullata" invece di un errore tecnico).

## Skip-and-continue: un elemento problematico non blocca tutto il backup

**Il problema che risolve**: durante un export di molti elementi, l'utente potrebbe
eliminare una nota/foto da un'altra schermata proprio mentre l'export ci sta
arrivando (due thread diversi: `ExporterManager` ha il suo executor dedicato,
Note/Archivio usano `AppExecutors.diskIO()`). Prima di questa modifica, un singolo
elemento sparito avrebbe fatto fallire l'intero export.

**Come funziona ora**, in `scriviZipInChiaro` (export) e `importaContenuti` (import):
ogni nota/file viene processato dentro un `try/catch` individuale. Se fallisce, la
descrizione dell'errore finisce in una lista `saltati` e il loop **continua** con
l'elemento successivo — non propaga l'eccezione, non abortisce l'intera operazione.

**Distinzione importante applicata in export**: la lettura/decifratura della nota
(`noteRepository.loadNote(...)`) avviene **prima** di aprire la entry nello zip — se
fallisce, l'entry non viene mai aperta, lo zip resta pulito. Per i file, invece,
`loadFileToStream` legge *e* scrive nella stessa chiamata (decifra a blocchi
scrivendo direttamente nella entry): se fallisce a metà, l'entry può restare aperta
con contenuto parziale — ma **non viene aggiunta al manifest**, quindi in import
viene semplicemente ignorata (l'import processa solo le entry elencate nel
manifest). Nessun rischio di importare un file corrotto per errore.

**Report `skipped_report.txt`**: scritto come ultima entry dentro lo zip **in
export**, testo semplice leggibile da una persona (non serve riparsarlo via app),
elenca ogni elemento saltato con id/titolo/nome e il messaggio d'errore. Presente
sempre, anche quando è vuoto ("Nessun elemento saltato."). **In import** non viene
prodotto un file analogo (l'import consuma il backup, non ne genera uno): l'esito
con eventuale conteggio di saltati arriva solo nel messaggio finale mostrato
dall'utente tramite `BackupViewModel`.

## `BackupViewModel`

**Perché serve**: prima, le lambda passate a `ExporterManager.esegui(...)` catturavano
direttamente `SettingsActivity` (per `getContentResolver()`, i `Toast`, ecc.) — un
riferimento diretto Activity↔thread di sfondo, diverso da come lavora il resto
dell'app (Note/Archivio/Dashboard passano sempre da un `ViewModel`). Ora
`SettingsActivity` è allineata allo stesso pattern.

**Cosa NON risolve**: il `ViewModel` sopravvive a una **rotazione schermo** (stessa
Activity, ricreata), ma **non** a un'uscita vera dalla schermata (back, freccia
toolbar) — in quel caso Android distrugge il `ViewModelStore` insieme all'Activity,
e con lui il `ViewModel`. Per questo l'operazione **non prosegue in background se si
naviga altrove**: è un limite noto, accettato per questa versione, con la
mitigazione UI descritta sotto. Il percorso per farla sopravvivere davvero è
`WorkManager` — vedi `todo-backup-workmanager.md`.

**Stato esposto**: `LiveData<BackupUiState>` con `enum Stato { INATTIVO, IN_CORSO,
COMPLETATO, ERRORE }` + un messaggio già pronto per un `Toast`. `SettingsActivity`
osserva e:
- disabilita `btnExportBackup`/`btnImportBackup`/i due campi password quando
  `IN_CORSO`;
- mostra il messaggio e richiama `resetStato()` su `COMPLETATO`/`ERRORE` (per non
  ripresentare lo stesso `Toast` se l'observer viene ri-registrato dopo una
  rotazione schermo).

**`onCleared()`**: chiama `exporterManager.cancel()` + `shutdown()` — l'unico punto
che chiude l'executor dedicato, non serve più farlo manualmente in
`SettingsActivity.onDestroy()`.

## Conferma prima di uscire durante un'operazione

`SettingsActivity` registra un `OnBackPressedCallback` (sempre attivo) e passa anche
`onSupportNavigateUp()` (freccia in toolbar) dallo stesso punto:
`confermaUscita()`. Se `backupViewModel.isOperazioneInCorso()` è `true`, mostra un
`AlertDialog` ("l'operazione verrà annullata, uscire comunque?") prima di chiamare
`finish()`; altrimenti esce subito. Scegliendo "esci", si chiama
`annullaOperazione()` (cancellazione cooperativa — l'elemento in corso di
elaborazione in quel momento fa comunque in tempo a finire, il loop si ferma al
checkpoint successivo) prima di `finish()`.

## Limitazioni note di questa versione (per riferimento, dettagliate nel TODO)

- Nessuna barra di progresso reale in UI (`ProgressListener` passato come `null`
  da `BackupViewModel` — l'infrastruttura c'è già in `ExporterManager`, manca solo
  il collegamento a una `LiveData<Integer>` osservata dall'Activity).
- L'operazione non sopravvive a una navigazione fuori da `SettingsActivity` (vedi
  sopra) — richiede `WorkManager` per essere risolto per davvero.
- Nessuna notifica di sistema durante un'operazione lunga (necessaria comunque per
  un eventuale futuro `Worker` in foreground su Android 12+).

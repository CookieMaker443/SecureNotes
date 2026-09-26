# `ExporterManager`

Package: `com.cookie.securenotes.manager.backup`
File: `ExporterManager.java`
Classi annidate: `ExporterManager.ProgressListener`, `ExporterManager.BackupResult`,
`ExporterManager.OperazioneAnnullataException`

## Ruolo

Orchestratore dell'intero export/import del backup cifrato. Non tocca mai
`CryptoManager` o i DAO direttamente: passa sempre da `NoteRepository`/
`FileRepository`, che restano gli unici proprietari della tripla
cifratura+DB+filesystem (stessa regola già in vigore per il resto dell'app, doc 04).

## Costruttore e stato interno

```java
public ExporterManager(SecureSession session, Context context)
```

Riceve `SecureSession` (da cui costruisce i due repository, come farebbe qualunque
altro manager) e un `Context` (usato solo per `getCacheDir()` — sempre convertito in
`getApplicationContext()` internamente, mai tenuto come Activity).

Possiede un `ExecutorService` **dedicato** (`Executors.newSingleThreadExecutor()`),
separato da `AppExecutors.diskIO()`: un export/import lungo non deve mai bloccare le
operazioni di Note/Archivio, che condividono invece quell'executor singolo.

Un flag `volatile boolean cancelled` — controllato a inizio di ogni iterazione dei
loop di copia, per uscire prima se l'utente annulla (stesso pattern cooperativo già
usato da `VideoPlayerActivity`).

## Metodi pubblici

| Metodo | Cosa fa |
| --- | --- |
| `BackupResult exportMedia(char[] password, OutputStream destinazione, ProgressListener listener)` | Esporta tutte le note e i file in un backup cifrato |
| `BackupResult importMedia(char[] password, InputStream sorgente, ProgressListener listener)` | Importa da un backup cifrato |
| `cancel()` | Imposta `cancelled = true` — cancellazione cooperativa, non istantanea |
| `esegui(Runnable operazione)` | Mette in coda un'operazione sull'executor dedicato, resettando `cancelled` |
| `shutdown()` | Chiude l'executor — chiamato da `BackupViewModel.onCleared()` |

## `BackupResult` — esito strutturato

```java
public static class BackupResult {
    int totale;
    int riusciti;
    List<String> saltati; // descrizioni leggibili di eventuali elementi non esportati/importati
}
```

Non è più `void`: sia export che import restituiscono questo oggetto, usato da
`BackupViewModel` per comporre il messaggio finale ("12 esportati, 1 saltato").

## `OperazioneAnnullataException` — segnala l'annullamento, non un errore

Checked exception nested, lanciata ai checkpoint di cancellazione al posto di un
vecchio `if (cancelled) return;` silenzioso. Si propaga fino a `BackupViewModel`, che
la distingue esplicitamente da un errore vero (messaggio "operazione annullata",
non un errore tecnico).

## Flusso `exportMedia` — due passaggi

1. `scriviZipInChiaro(...)`: crea uno zip **non cifrato** su un file temporaneo in
   cache (prefisso `secnotes_tmp_`). Per ogni nota: `NoteRepository.loadNote` (già
   decifra) → scritta come entry di testo. Per ogni file: `FileRepository.
   loadFileToStream` (decifra a blocchi da 64KB, riusato identico da come è stato
   scritto per il video) → scritto direttamente nell'entry. Scrive `manifest.json` e
   `skipped_report.txt` come ultime entry.
2. `BackupCrypto.encryptStream(...)`: cifra il file temporaneo intero verso la
   `destinazione` scelta dall'utente (via SAF).
3. Il file temporaneo in chiaro viene sempre cancellato in un blocco `finally`,
   successo o fallimento che sia.

### Skip-and-continue (per elemento, non per l'intera operazione)

Ogni nota/file è avvolto in un `try/catch` individuale: se fallisce (es. eliminato da
un'altra schermata mentre l'export è in corso, dato che gira su un thread diverso da
`AppExecutors.diskIO()`), la descrizione dell'errore finisce in `saltati` e il loop
**continua**, non abortisce tutto. Per le note, la lettura avviene **prima** di
aprire la entry — se fallisce, l'entry non si apre mai. Per i file, `loadFileToStream`
legge e scrive nella stessa chiamata: se fallisce a metà, l'entry può restare aperta
con contenuto parziale, ma **non viene aggiunta al manifest** — l'import ignora
comunque qualunque entry non elencata lì, quindi nessun rischio di importare un file
corrotto per errore.

## Flusso `importMedia` — simmetrico

1. `BackupCrypto.decryptStream(...)`: decifra tutto il blob verso un file temporaneo
   in chiaro. Se la password è sbagliata, fallisce qui (`BackupCryptoException`) e si
   esce subito.
2. `leggiManifest(...)`: prima passata sullo zip temporaneo, cerca solo
   `manifest.json`, salta il resto senza leggerlo (`ZipInputStream.getNextEntry()`
   scarta automaticamente il contenuto non consumato della entry precedente).
   Controlla `schemaVersion`.
3. `importaContenuti(...)`: seconda passata, guidata dal manifest — per ogni entry
   che corrisponde a una nota o un file elencato, chiama `NoteRepository.importNote`
   o `FileRepository.importFile` (stesso skip-and-continue di sopra). Le entry non
   elencate nel manifest (incluse `manifest.json` e `skipped_report.txt` stesse)
   vengono ignorate.
4. Il file temporaneo viene sempre cancellato in `finally`.

## Chi la usa

Solo `BackupViewModel` — mai `SettingsActivity` direttamente (vedi doc su
`BackupViewModel` per il perché).

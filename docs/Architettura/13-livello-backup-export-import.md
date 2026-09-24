# 13 — Livello Backup (Export/Import cifrato)

> Documento aggiuntivo ai 12 file già esistenti in `securenotes-docs/`. Copre il
> design — deciso in chat, **non ancora scritto in codice** — per l'esportazione
> e l'importazione di un backup cifrato con password. `btnExportBackup` esiste
> già nel layout (`activity_settings.xml`, vedi doc 09) ma è `setEnabled(false)`,
> solo placeholder. Questo documento descrive cosa serve per renderlo
> funzionante, più un nuovo tasto `btnImportBackup` mai esistito finora.

## Decisioni chiuse in questa sessione di design

- **Autorizzazione ≠ cifratura**: PIN/biometria dell'app autorizzano l'operazione
  (come già avviene per il cambio PIN in `SettingsActivity`); una **password di
  backup separata**, chiesta solo in fase di export, è quella che cifra
  effettivamente lo zip. Le due cose non si mescolano mai.
- **Formato file**: `.secnotes` = zip normale (senza password, `java.util.zip`,
  già nell'SDK) cifrato per intero con AES/GCM, chiave derivata dalla password
  di backup via **PBKDF2WithHmacSHA256**. Nessuna libreria esterna nuova.
- **Validazione password in export sì, in import no**: in export si controlla
  lunghezza minima + corrispondenza dei due campi, *prima* di procedere. In
  import non si può "validare" una password a priori: si tenta la decifratura
  e si intercetta `AEADBadTagException` (stesso meccanismo già usato da
  `CryptoManager` per dati manomessi) per dire "password errata o file non
  valido".
- **Manifest dentro lo zip** con `schemaVersion`: fa da "file di check" — se
  assente o versione non riconosciuta, l'import si rifiuta subito.
- **Date preservate in import**: le note/i file importati mantengono
  `dataCreazione`/`dataModifica` originali dal manifest, non la data
  dell'import.
- **UUID sempre rigenerati in import**, per evitare collisioni con dati già
  presenti sul device di destinazione.
- **Ricifratura locale obbligatoria in import**: il contenuto viaggia nello zip
  in chiaro (protetto solo dalla cifratura del blob `.secnotes`); una volta
  dentro l'app, va sempre ricifrato con il `CryptoManager` **del device
  corrente** — la chiave Keystore non è mai la stessa tra due device, quindi
  non avrebbe senso portarsi dietro byte già cifrati con una chiave che qui
  non esiste.
- **SAF per la destinazione/sorgente file**: `ACTION_CREATE_DOCUMENT` per
  l'export, `ActivityResultContracts.OpenDocument` per l'import — stesso
  pattern già in uso per l'import dei file media (doc 08), nessun permesso di
  storage richiesto.
- **Executor dedicato**: `ExporterManager` non usa `AppExecutors.diskIO()`
  (thread singolo condiviso con Note/Archivio) — un backup lungo lo terrebbe
  occupato per minuti, bloccando il resto dell'app (stesso problema già visto
  con il video, doc 12, Fase 6). Ha un proprio `ExecutorService` interno.
- **Streaming a blocchi**, non file interi in RAM — stesso principio già
  applicato al video (`loadFileToStream`), esteso qui sia in lettura (export)
  che in scrittura (import, nuovo `encryptStream` simmetrico).

## Formato del file `.secnotes`

```
[salt PBKDF2 — 16 byte, in chiaro] [IV GCM — 12 byte, in chiaro] [ciphertext + tag GCM]
```

Il salt e l'IV non sono segreti — servono solo a derivare la chiave e ad
inizializzare il cifrario; il segreto è la password, mai scritta su disco.
Il `ciphertext`, una volta decifrato, è un file zip normale (leggibile con
`ZipInputStream`/`ZipFile` standard) che contiene:

```
manifest.json
entries/
  <uuid-o-id-qualunque>   (un file per ogni nota/foto/video/pdf, contenuto in chiaro)
  ...
```

### `manifest.json` — struttura

```json
{
  "schemaVersion": 1,
  "app": "SecureNotes",
  "exportDate": "2026-09-24T10:30:00Z",
  "notes": [
    { "entryFile": "n_0001", "titolo": "...", "dataCreazione": 0, "dataModifica": 0 }
  ],
  "files": [
    { "entryFile": "f_0001", "tipo": "foto", "nomeOriginale": "...", "dimensioneByte": 0, "dataCreazione": 0 }
  ]
}
```

`entryFile` è solo il nome della entry dentro il file zip interno — non ha
nessun legame con l'UUID fisico originale sul device sorgente (che comunque
verrà rigenerato in import).

---

## Classi nuove

### `BackupCrypto`

Package proposto: `com.cookie.securenotes.security` (accanto a `CryptoManager`,
ma **senza dipendenza da Keystore** — lavora solo con una chiave derivata da
password, che vive esclusivamente in RAM per la durata dell'operazione).

| Metodo | Cosa fa |
| --- | --- |
| `deriveKey(char[] password, byte[] salt) -> SecretKey` | PBKDF2WithHmacSHA256, chiave AES a 32 byte |
| `encryptStream(char[] password, InputStream zipInChiaro, OutputStream dest) throws BackupCryptoException` | genera salt+IV casuali, li scrive in testa a `dest`, poi cifra a blocchi (stesso schema IV-in-testa di `CryptoManager`) |
| `decryptStream(char[] password, InputStream sorgenteCifrata, OutputStream zipDecifrato) throws BackupCryptoException` | legge salt+IV dalla testa, deriva la chiave, decifra a blocchi. Propaga `AEADBadTagException` come causa di `BackupCryptoException` — il chiamante lo interpreta come "password errata o file corrotto" |

`char[]` per la password (non `String`) — si può azzerare esplicitamente dopo
l'uso, `String` in Java è immutabile e resta in memoria finché il GC non
decide.

### `BackupManifest` (+ `NoteEntry`, `FileEntryMeta`)

Semplice modello dati, serializzato/deserializzato in JSON (via
`org.json`, già disponibile su Android, niente libreria nuova tipo Gson).

```
BackupManifest
  int schemaVersion
  String app
  String exportDate
  List<NoteEntry> notes
  List<FileEntryMeta> files

BackupManifest.NoteEntry
  String entryFile, titolo
  long dataCreazione, dataModifica

BackupManifest.FileEntryMeta
  String entryFile, tipo, nomeOriginale
  long dimensioneByte, dataCreazione
```

### `ExporterManager`

Package proposto: `com.cookie.securenotes.manager.backup`. Riceve
`SecureSession` nel costruttore (stesso schema di `NoteRepository`/
`FileRepository`) e ottiene da lì i due repository — **non** tocca mai
direttamente `CryptoManager` o i DAO: per rispettare la stessa regola già in
vigore nel livello repository (doc 04), ogni cifratura/scrittura DB passa
sempre da `NoteRepository`/`FileRepository`.

| Metodo | Cosa fa |
| --- | --- |
| `exportMedia(char[] password, OutputStream destinazione, ProgressListener listener) throws ExportException` | vedi flusso sotto |
| `importMedia(char[] password, InputStream sorgente, ProgressListener listener) throws ImportException` | vedi flusso sotto |
| `cancel()` | imposta un flag `volatile boolean cancelled`, controllato nel loop di copia — stesso pattern cooperativo già usato in `VideoPlayerActivity` |

Interfaccia annidata (usata da un solo chiamante, quindi annidata come
`LockListener`, non estratta come `OnFileClickListener`):

```java
public interface ProgressListener {
    void onProgress(int completati, int totale);
}
```

`ExporterManager` possiede un proprio `ExecutorService` (uno solo, dedicato —
non `AppExecutors.diskIO()`), creato nel costruttore e chiuso quando la sessione
si blocca.

#### Flusso `exportMedia` (due passaggi, come per il PDF/video: file temporaneo in cache poi risultato finale)

1. Crea uno zip **non cifrato** su un file temporaneo in `getCacheDir()`
   (prefisso `secnotes_tmp_`, stessa logica di pulizia di `pdf_view_*`/
   `video_view_*`).
2. Per ogni nota: `NoteRepository.loadNote(id)` (già decifra), scrive il testo
   come entry nello zip, aggiunge una riga al manifest.
3. Per ogni file: `FileRepository.loadFileToStream(id, entryOutputStream)`
   (già esiste, decifra a blocchi da 64KB — **niente di nuovo qui**, si riusa
   quello già scritto per il video), aggiunge una riga al manifest.
4. Scrive `manifest.json` come ultima entry.
5. Chiude lo zip temporaneo, poi `BackupCrypto.encryptStream(password, zipTemp, destinazione)`.
6. Cancella il file temporaneo (anche in caso di eccezione, `finally`).

#### Flusso `importMedia` (simmetrico)

1. `BackupCrypto.decryptStream(password, sorgente, zipTempInChiaro)` — se la
   password è sbagliata, fallisce qui e si esce subito.
2. Legge `manifest.json` dallo zip temporaneo, controlla `schemaVersion`.
3. Per ogni nota nel manifest: legge la entry (testo in chiaro), chiama
   `NoteRepository.importNote(titolo, testo, dataCreazione, dataModifica)`
   (metodo nuovo, vedi sotto) — la ricifratura avviene lì dentro, non qui.
4. Per ogni file nel manifest: apre uno stream sulla entry corrispondente,
   chiama `FileRepository.importFile(tipo, nomeOriginale, stream, dataCreazione)`
   (metodo nuovo, vedi sotto).
5. Cancella il file temporaneo.

---

## Modifiche a classi esistenti

### `CryptoManager` — nuova aggiunta

```java
public OutputStream encryptStream(OutputStream destinazioneCifrata) throws CryptoException
```

Simmetrico a `decryptStream` (già esiste, doc 11): genera un IV nuovo, lo
scrive in testa a `destinazioneCifrata`, ritorna un `CipherOutputStream` su cui
scrivere in chiaro a blocchi. Serve per l'import: non si vuole mai un `byte[]`
intero in RAM nemmeno in fase di *ri*cifratura di un video importato — stesso
principio di `decryptStream`, specchiato.

### `NoteRepository` — nuovo metodo

```java
public void importNote(String titolo, String testoChiaro, long dataCreazione, long dataModifica) throws CryptoException, IOException
```

Come `saveNote`, ma: genera comunque un UUID nuovo (mai riusa quello del
device sorgente), **non** usa `System.currentTimeMillis()` per le date — le
riceve già pronte e le passa così com'è alla riga Room.

### `FileRepository` — nuovo metodo

```java
public void importFile(String tipo, String nomeOriginale, InputStream contenutoChiaro, long dataCreazione) throws CryptoException, IOException
```

Come `saveFile`, ma prende uno stream già in chiaro (invece di un `byte[]` da
un `ContentResolver`) e scrive cifrando **a blocchi** via
`cryptoManager.encryptStream(...)` — non `encrypt(byte[])` — proprio per non
riproporre il problema di RAM già risolto per il video in lettura (doc 12,
Fase 6/7), stavolta in scrittura. Data preservata come per `importNote`.

### `SecureSession.unlock()` — estensione della pulizia residui

Stessa idea già presente per `pdf_view_*`/`video_view_*` (doc 11), estesa al
prefisso `secnotes_tmp_`, per il caso in cui il processo venga ucciso a metà
di un export/import.

---

## UI — modifiche a `SettingsActivity` / `activity_settings.xml`

Sotto la sezione "Backup" già esistente, aggiungere due campi password prima
di `btnExportBackup` (che va sbloccato, non più `setEnabled(false)`), più un
nuovo tasto `btnImportBackup`:

```xml
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:hint="@string/hint_backup_password">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/editBackupPassword"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textPassword" />

</com.google.android.material.textfield.TextInputLayout>

<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:hint="@string/hint_backup_password_confirm">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/editBackupPasswordConfirm"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textPassword" />

</com.google.android.material.textfield.TextInputLayout>

<!-- btnExportBackup: già esistente, va solo sbloccato -->

<Button
    android:id="@+id/btnImportBackup"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:text="@string/action_import_backup" />
```

**Perché la password di import NON è un campo in questo layout**: è legata a
un file specifico scelto al momento (via SAF), non a uno stato permanente
della schermata Impostazioni. Il tasto `btnImportBackup` apre il file-picker;
solo *dopo* la scelta del file compare un `AlertDialog` con un solo campo
password (view custom minimale, non serve un intero layout XML dedicato) —
si chiude da solo a operazione conclusa, niente campo che resta popolato a
metà se l'utente annulla.

Stringhe nuove da aggiungere: `hint_backup_password`,
`hint_backup_password_confirm`, `action_import_backup`,
`dialog_import_password_title`, `hint_import_password`,
`error_backup_password_too_short`, `error_backup_password_mismatch`,
`error_backup_wrong_password`, `msg_export_success`, `msg_import_success`,
`error_export_failed`, `error_import_failed`.

### Flusso export (lato UI)

1. Utente compila i due campi password in `SettingsActivity`.
2. Tap su "Esporta backup" → validazione locale (lunghezza minima 8,
   corrispondenza dei due campi) → se ok, lancia
   `ACTION_CREATE_DOCUMENT` con nome suggerito
   `SecNotes_export_ddMMyy.secnotes`.
3. Al ritorno dell'`Uri` di destinazione, `ExporterManager.exportMedia(...)`
   parte sull'executor dedicato.
4. A operazione conclusa (successo o errore), notifica l'utente (Toast o
   dialog) — i campi password vengono azzerati subito dopo l'uso.

### Flusso import (lato UI)

1. Tap su "Importa backup" → `ActivityResultContracts.OpenDocument` (MIME
   generico, `.secnotes` non è un tipo registrato).
2. Al ritorno dell'`Uri` sorgente, mostra `AlertDialog` con campo password.
3. Conferma → `ExporterManager.importMedia(...)` sull'executor dedicato.
4. Successo/errore → notifica utente. Se la password era sbagliata, il
   messaggio distingue esplicitamente questo caso (intercettando
   `BackupCryptoException`) da un errore generico di file corrotto/manifest
   non riconosciuto.

---

## Punto aperto, da decidere prima o durante l'implementazione

- **`SettingsActivity` non ha oggi un proprio ViewModel** (a differenza di
  Note/Archivio/Dashboard). Per un'operazione lunga con progresso e possibile
  cancellazione, converrebbe introdurre un `BackupViewModel` (stesso pattern
  MVVM già usato ovunque nell'app: `LiveData<Integer>` per il progresso,
  `LiveData<String>` per l'esito), invece di far parlare `SettingsActivity`
  direttamente con `ExporterManager`. Consigliato per coerenza con il resto
  del progetto, ma è un dettaglio che si può anche semplificare se si preferisce
  tenere `SettingsActivity` più diretta.

## Punti aperti pre-esistenti risolti da questo documento

- ✅ "Requisiti della password di backup" (doc 06) → password dedicata, minimo
  8 caratteri, conferma doppia in UI.
- ✅ "Export backup criptato" (doc 09/10) → design completo, pronto per
  l'implementazione.

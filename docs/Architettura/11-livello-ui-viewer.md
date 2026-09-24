# 11 — Livello UI: Viewer (visualizzazione file)

> Documento aggiuntivo rispetto ai 10 file già esistenti in `securenotes-docs/`.
> Copre tutto ciò che è stato progettato e scritto in questa sessione per risolvere
> il TODO `onFileClick` in Archivio (tap su una nota/foto/video/PDF non apriva nulla).

## Scopo

Prima di questa sessione, `onFileClick` in tutti e tre i Fragment dell'Archivio
(`FotoFragment`, `VideoFragment`, `PdfFragment`, tramite la logica comune in
`BaseFileListFragment`) era un `TODO` vuoto. Questo documento descrive il nuovo
package `ui.viewer` che apre un visualizzatore dedicato per ciascun tipo di file,
e tutte le modifiche a classi esistenti necessarie per farlo funzionare.

## Struttura del nuovo package

```
com.cookie.securenotes
  ui/
    viewer/
      SecureViewerActivity.java   (astratta, base comune)
      ImageViewerActivity.java
      PdfViewerActivity.java
      VideoPlayerActivity.java
```

Layout XML aggiunti: `activity_image_viewer.xml`, `activity_pdf_viewer.xml`,
`activity_video_player.xml`.

## Principio comune a tutti e tre i viewer

Ogni file è salvato **cifrato** su disco (AES/GCM via `CryptoManager`). Nessun
viewer di sistema (ImageView, PdfRenderer, ExoPlayer) sa leggere dati cifrati
direttamente: vanno prima riportati in chiaro, in memoria o su un file
temporaneo, a seconda del tipo e della sua dimensione tipica.

| Tipo  | Dove va il contenuto decifrato          | Perché                                                                 |
|-------|------------------------------------------|-------------------------------------------------------------------------|
| Foto  | Array di byte in RAM                     | Le foto sono piccole; nessun bisogno di file temporaneo.                |
| PDF   | File temporaneo in chiaro (`getCacheDir()`) | `PdfRenderer` richiede un `ParcelFileDescriptor` con accesso casuale reale, non accetta stream custom. |
| Video | File temporaneo in chiaro, scritto **a blocchi** (streaming di decifratura) | Vedi `12-cronistoria-bug-video.md` per il perché di questa scelta specifica — non è stata la prima soluzione tentata. |

---

## Classi nuove

### `SecureViewerActivity` (astratta)

Base comune ai tre viewer, estende `BaseActivity` (quindi eredita
automaticamente tracciamento del lock/timeout e il redirect a `LoginActivity`
se la sessione risulta bloccata al resume — nessun codice aggiuntivo necessario
per la sicurezza di base).

```java
public static final String EXTRA_FILE_ID = "extra_file_id";
protected long getFileId() { ... }       // legge l'id dal Intent
public boolean onSupportNavigateUp() { finish(); return true; }
```

### `ImageViewerActivity`

Il più semplice dei tre, nessuna dipendenza nuova.

- `onCreate()`: su `AppExecutors.diskIO()`, chiama `FileRepository.loadFile(fileId)`
  (decifra l'intero file in un array — accettabile per una foto), poi
  `BitmapFactory.decodeByteArray(...)`, poi torna sul main thread per
  `imageView.setImageBitmap(bitmap)`.
- Errori (bitmap null, eccezione) → Toast + `finish()`.
- **Non implementato**: pinch-to-zoom. Deciso di rimandarlo a dopo che il resto
  funzionasse — resta un punto aperto (vedi sezione finale).

### `PdfViewerActivity`

Usa `android.graphics.pdf.PdfRenderer`, l'unica API di sistema per il
rendering di pagine PDF.

- Campi: `pdfRenderer`, `fileDescriptor`, `tempFile`, `currentPage`, `pageIndex`.
- `onCreate()`: su `diskIO()` → `loadFile()` → scrive un file temporaneo con
  prefisso **`pdf_view_`** in `getCacheDir()` → `ParcelFileDescriptor.open(...)`
  → `new PdfRenderer(fileDescriptor)` → torna sul main thread → `showPage(0)`.
- `showPage(index)`: chiude la pagina corrente (se presente), apre quella
  richiesta, la renderizza su un `Bitmap` (`RENDER_MODE_FOR_DISPLAY`), la
  mostra. Navigazione con due bottoni (precedente/successiva), non gesture.
- `onDestroy()`: chiude pagina, renderer, file descriptor, poi cancella
  `tempFile`.
- **Nota di sicurezza esplicita**: per tutta la durata della visualizzazione
  esiste un file PDF in chiaro nella cache privata dell'app. Accettabile (cartella
  privata, non root-accessibile), ma è una concessione pratica di cui il
  progetto deve restare consapevole.

### `VideoPlayerActivity`

Quello che ha richiesto più iterazioni. La versione attuale (funzionante per
il caricamento, con un problema di visualizzazione ancora in corso di debug —
vedi `12-cronistoria-bug-video.md`):

- Dipendenze nuove: **Media3/ExoPlayer** (`media3-exoplayer`, `media3-ui`,
  `media3-datasource`, v1.4.1), aggiunte con alias in `libs.versions.toml` +
  `build.gradle.kts`.
- Campi: `player` (`ExoPlayer`), `tempFile`, `cancelled` (`volatile boolean`,
  per cancellazione cooperativa se l'utente esce prima che il caricamento finisca).
- `onCreate()`: su `diskIO()` → `FileRepository.getById(fileId)` (metadati) →
  crea file temporaneo con prefisso **`video_view_`** → chiama
  **`FileRepository.loadFileToStream(fileId, out)`** (decifra a blocchi di 64KB,
  mai l'intero video in un unico array — vedi sezione sotto) → torna sul main
  thread → `setupPlayer()`.
- `setupPlayer()`: crea `ExoPlayer`, aggiunge un `Player.Listener` che logga
  `onPlayerError` con tag `SecureNotesVideo` (utile per il debug), collega
  `PlayerView`, `player.setMediaItem(MediaItem.fromUri(tempFile...))`,
  `prepare()`, `setPlayWhenReady(true)`.
- `onStop()`: mette in pausa. `onDestroy()`: imposta `cancelled = true` per
  primo, rilascia il player, cancella `tempFile`.
- Richiede l'annotazione `@UnstableApi` sulla classe (Media3 marca alcune API
  come non ancora stabili tra versioni).

## Routing dal click: modifica a `BaseFileListFragment`

Il `TODO` in `onFileClick` è stato sostituito con:

```java
@Override
public void onFileClick(FileEntry fileEntry) {
    Class<?> target;
    switch (fileEntry.tipo) {
        case "foto": target = ImageViewerActivity.class; break;
        case "video": target = VideoPlayerActivity.class; break;
        case "pdf":   target = PdfViewerActivity.class; break;
        default: return;
    }
    Intent intent = new Intent(requireContext(), target);
    intent.putExtra(SecureViewerActivity.EXTRA_FILE_ID, fileEntry.id);
    startActivity(intent);
}
```

---

## Modifiche/aggiunte a classi esistenti

### `FileRepository` (aggiunte)

- **`getById(long fileEntryId)`**: espone pubblicamente ciò che prima era solo
  interno a `loadFile()` (`fileDao.getById(...)`). Serve ai viewer per
  recuperare i metadati (`nomeFisico`, `tipo`) senza dover decifrare il
  contenuto.
- **`loadFileToStream(long fileEntryId, OutputStream destinazione)`**: decifra
  a blocchi di 64KB per volta (via `CryptoManager.decryptStream`), scrivendo
  ogni blocco direttamente sulla destinazione. Usato da `VideoPlayerActivity`
  per non dover mai tenere l'intero video in RAM in un unico array — a
  differenza di `loadFile()`, che resta invariato e va bene per foto/PDF
  (dimensioni tipicamente piccole).

```java
public void loadFileToStream(long fileEntryId, OutputStream destinazione) throws CryptoException, IOException {
    FileEntry entry = fileDao.getById(fileEntryId);
    if (entry == null) throw new IOException("File non trovato nell'indice: id=" + fileEntryId);
    File sorgente = new File(getDirByTipo(entry.tipo), entry.nomeFisico);

    try (InputStream cifrato = new FileInputStream(sorgente);
         InputStream decifrato = cryptoManager.decryptStream(cifrato)) {
        byte[] buffer = new byte[64 * 1024];
        int letti;
        while ((letti = decifrato.read(buffer)) != -1) {
            destinazione.write(buffer, 0, letti);
        }
    }
}
```

### `CryptoManager` (aggiunta: famiglia 3, streaming)

```java
public InputStream decryptStream(InputStream encryptedIn) throws CryptoException {
    // legge i primi 12 byte come IV, inizializza Cipher in DECRYPT_MODE
    // con GCMParameterSpec, ritorna un CipherInputStream sull'input rimanente
}
```

Supporta **solo lettura sequenziale in avanti** — limite intrinseco di
AES/GCM (il tag di autenticazione finale valida l'intero flusso in ordine,
non è possibile un vero seek casuale dentro il ciphertext). Nato durante il
tentativo di streaming diretto poi abbandonato (vedi doc 12), è stato
riutilizzato con successo per `loadFileToStream()`, dove la lettura è
comunque sempre sequenziale in avanti per natura.

### `SecureSession` (aggiunta in `unlock()`)

Pulizia di eventuali residui di file temporanei in chiaro, nel caso il
processo sia stato ucciso a forza (crash, kill di sistema) mentre un viewer
era aperto, prima che il suo `onDestroy()` avesse modo di ripulire:

```java
File[] staleCache = appContext.getCacheDir().listFiles((dir, name) ->
        name.startsWith("pdf_view_") || name.startsWith("video_view_"));
if (staleCache != null) for (File f : staleCache) f.delete();
```

### `AndroidManifest.xml`

Aggiunte le tre nuove Activity (necessario, dimenticarle causa
`ActivityNotFoundException`/crash silenzioso al tap):

```xml
<activity android:name=".ui.viewer.ImageViewerActivity" android:exported="false" />
<activity android:name=".ui.viewer.VideoPlayerActivity" android:exported="false" />
<activity android:name=".ui.viewer.PdfViewerActivity" android:exported="false" />
```

### `strings.xml`

Aggiunte: `error_loading_file`, `desc_full_image`, `desc_pdf_page`,
`action_prev_page`, `action_next_page`.

### `libs.versions.toml` / `build.gradle.kts`

Aggiunte le tre dipendenze Media3 (v1.4.1) con alias:
`media3-exoplayer`, `media3-ui`, `media3-datasource`.

---

## Due bug preesistenti risolti durante questa sessione (non nuovi, ma scoperti/corretti mentre si lavorava al viewer)

### `LoginActivity` — biometria non inizializzava mai la sessione

`onAuthenticationSucceeded` chiamava direttamente `navigateToDashboard()`,
saltando `unlockSessionAndProceed()` (che crea `CryptoManager`,
`SecureSession.init()+unlock()`, `LockManager.start()`). Causava crash al
primo avvio con impronta (sessione mai inizializzata) e "nulla succede" dopo
lo sblocco da timeout (sessione bloccata mai riportata a `unlocked=true`).
**Fix**: chiamare `unlockSessionAndProceed()` anche nel ramo biometrico.

### `FileAdapter` — `ClassCastException` sempre presente nel percorso PDF

`FileAdapter` definiva un'interfaccia annidata `OnFileClickListener` separata
(ma identica nella forma) da quella condivisa `ui.archivio.OnFileClickListener`.
`BaseFileListFragment` costruiva `FileAdapter` con un cast forzato tra le due,
che falliva sempre a runtime. **Fix**: rimossa l'interfaccia duplicata,
`FileAdapter` ora usa direttamente quella condivisa.

---

## Classi create e poi rimosse durante l'esplorazione (dettagli completi in `12-cronistoria-bug-video.md`)

Per trasparenza, questi file sono esistiti temporaneamente nel progetto e
sono stati rimossi dopo essersi rivelati non necessari o non funzionanti:

- **`EncryptedFileDataSource.java`** — `DataSource` custom per Media3, per
  streaming diretto cifrato→player senza mai un file in chiaro su disco.
  Abbandonato: incompatibile in pratica con video la cui struttura interna
  (`moov` in fondo al file) richiede accesso casuale che AES/GCM sequenziale
  non permette in tempi ragionevoli.
- **`VideoRemuxer.java`** — avrebbe dovuto riscrivere i video con l'indice
  interno (`moov`) spostato in testa, usando `MediaExtractor`/`MediaMuxer`.
  Abbandonato: le API Android (`MediaMuxer`) non supportano questa
  ottimizzazione in nessun modo (conferma nel log: *"The mp4 file will not
  be streamable"*, sempre presente indipendentemente da come viene usato).
- **`StoragePaths.getTempDir()`** — cartella temporanea dedicata introdotta
  per `VideoRemuxer`, rimossa insieme ad esso.

## Stato attuale a fine sessione

| Tipo  | Stato |
|-------|-------|
| Foto  | ✅ Funzionante |
| PDF   | ✅ Funzionante |
| Video | ⚠️ Caricamento senza più problemi di memoria, ma **schermo nero al play** ancora da diagnosticare — vedi `12-cronistoria-bug-video.md` |

## Punti aperti generati da questo lavoro (da aggiungere a `06-punti-aperti.md`)

- Pinch-to-zoom su `ImageViewerActivity` (rimandato deliberatamente).
- Valutare se restringere i MIME type accettati in `VideoFragment.getMimeTypes()`
  (oggi ancora `video/*` generico) per evitare formati a rischio, ora che il
  remux non c'è più questo è meno critico ma resta buona norma.
- Se in futuro servisse davvero lo streaming diretto (mai chiaro su disco),
  servirebbe rivalutare con una libreria di muxing esterna (es. FFmpeg via
  binding), fuori scopo per il progetto attuale.

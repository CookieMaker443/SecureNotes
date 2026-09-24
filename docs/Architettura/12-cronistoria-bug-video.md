# 12 — Cronistoria: bug del visualizzatore video

> Documento di tracciamento per non perdere il filo tra le sessioni. Racconta,
> in ordine cronologico, tutto ciò che è stato tentato per far funzionare la
> riproduzione video cifrata, cosa ha funzionato, cosa no e perché, e dove ci
> siamo fermati.

## Sintomo di partenza

Tap su un video in Archivio non apriva nulla (`TODO` in `onFileClick`).
Foto e PDF sono stati risolti nella stessa sessione senza grosse difficoltà;
il video ha richiesto molte più iterazioni per motivi strutturali legati al
formato MP4 e ai limiti di AES/GCM, non per errori banali di codice.

---

## Fase 1 — Routing di base

Aggiunto il package `ui.viewer` con `SecureViewerActivity` (base astratta) e
le tre Activity concrete. Primo giro di test: crash immediato su tap,
**sia** per foto che per video/pdf. Causa: le tre nuove Activity non erano
dichiarate in `AndroidManifest.xml`. Risolto aggiungendole. Foto e PDF hanno
iniziato a funzionare da subito dopo questo fix.

## Fase 2 — Prima scelta per il video: streaming diretto cifrato→player

Decisione iniziale (concordata esplicitamente): **mai un file in chiaro su
disco**, nemmeno temporaneamente, per il video. Approccio scelto: un
`DataSource` custom per Media3/ExoPlayer che decifra i byte al volo mentre il
player li richiede.

**Cosa è stato scritto:**
- `CryptoManager.decryptStream(InputStream)`: apre un `CipherInputStream`
  leggendo prima i 12 byte dell'IV, poi decifrando in sequenza. Formato su
  disco confermato: `IV(12 byte) || ciphertext+tag GCM`.
- `EncryptedFileDataSource extends BaseDataSource`: legge il file cifrato,
  lo passa per `decryptStream()`, espone i byte in chiaro a Media3.
- `VideoPlayerActivity` con `ProgressiveMediaSource.Factory(factory)`.
- Dipendenze Media3 aggiunte (`media3-exoplayer`, `media3-ui`,
  `media3-datasource`).

**Problemi di compilazione incontrati e risolti** (tutti minori, tipici di
Media3):
- Nome del file Java doveva corrispondere esattamente al nome della classe
  pubblica (`EncryptedFileDataSource.java`).
- Annotazione `@UnstableApi` richiesta su `EncryptedFileDataSource` e
  `VideoPlayerActivity` (API di Media3 non ancora stabilizzate).
- Annotazioni `@NonNull` richieste sui parametri degli override
  (`open(DataSpec)`, `read(byte[], ...)`) per compatibilità con le
  convenzioni `@NonNullApi` di Media3.

**Bug applicativo trovato dopo la compilazione**: crash al tap su Video/PDF
dovuto a un `ClassCastException` in `FileAdapter` (interfaccia
`OnFileClickListener` duplicata — vedi dettagli nel doc 11, sezione bugfix).
Non era in realtà un problema specifico del video: colpiva anche PDF perché
condividevano lo stesso ramo di codice (`useGridLayout() == false`).

## Fase 3 — Il video si apre ma resta nero, 0:00/0:00

Con tutti i fix sopra applicati, il video si apriva (Activity raggiungeva
`RESUMED` nei log) ma restava fermo a schermo nero, `0:00 / 0:00`, con i
controlli del player visibili ma inerti.

**Diagnosi (confermata empiricamente, non solo teorica):**

Un file MP4 è diviso in "atom": `mdat` (i dati grezzi audio/video) e `moov`
(l'indice — durata, tracce, tabella degli offset di ogni fotogramma). Un
player **deve** leggere `moov` prima di poter iniziare qualunque cosa.

- Video **registrati con la fotocamera di un telefono** scrivono quasi
  sempre `moov` **alla fine** del file (perché la tabella degli offset si
  può costruire solo a registrazione conclusa).
- Player su file normali non hanno problemi: un salto (`seek`) in un file su
  disco costa quasi zero.
- Il nostro `EncryptedFileDataSource`, basato su AES/GCM decifrato
  **sequenzialmente** (`CipherInputStream`), non può "saltare dentro" il
  ciphertext: per arrivare al byte N deve aver già decifrato in ordine tutti
  i byte precedenti.
- Risultato: per leggere `moov` (in fondo), il player doveva far decifrare
  *tutto* il file prima di poter mostrare qualsiasi cosa.

**Test di conferma**: un video di prova di soli 2 secondi ha impiegato
**~30 secondi** a partire. Su un video reale di minuti sarebbe stato
totalmente inutilizzabile.

**Sintomi aggiuntivi coerenti con questa causa**: dopo la fine della
riproduzione, "play" non ripartiva; un seek indietro (anche di soli 5
secondi su un video di 2) riportava a `0:00` ma non faceva ripartire nulla —
coerente con uno stream sequenziale ormai "consumato" per intero, che
Media3 non sapeva come far ripartire senza gestione esplicita che non era
stata scritta.

## Fase 4 — Decisione: abbandonare lo streaming diretto per il video

Valutate due strade per risolvere il problema del `moov` in fondo:

1. Spostare `moov` manualmente negli atom del file (sconsigliato: richiede
   ricalcolare a mano tutti gli offset interni, fragile).
2. Rimuxare il video con `MediaExtractor`/`MediaMuxer` di Android al momento
   del salvataggio, per riscriverlo con `moov` (si sperava) in testa.

Scelta la strada 2, ma **contestualmente** si è anche deciso di passare il
video all'approccio già usato per il PDF ("opzione A": decifra tutto,
scrivi su file temporaneo in chiaro, lascia che il player legga un file
vero) — perché una volta che il player legge da un file reale su disco, la
posizione di `moov` **non è più un problema**: i seek su un file reale sono
sempre economici, indipendentemente da dove sta l'indice.

**Primo tentativo di riscrittura di `VideoPlayerActivity` per l'opzione A**:
bug introdotto per distrazione — `repository.getById(fileId)` veniva
chiamato **sul main thread** invece che su `AppExecutors.diskIO()` (mentre
`ImageViewerActivity`/`PdfViewerActivity` lo facevano già correttamente).
Room vieta query sincrone sul main thread per design.

```
Caused by: java.lang.IllegalStateException: Cannot access database on the main thread
	at ... FileDao_Impl.getById
	at ... FileRepository.getById
	at ... VideoPlayerActivity.onCreate
```

Questo crash uccideva l'intero processo, il che mascherava ulteriormente i
log successivi su dispositivo fisico (Honor/MediaTek, log HAL/GPU molto
rumorosi che hanno reso la diagnosi via `adb logcat` complicata per diversi
round di questa sessione, prima di scoprire il filtro per PID:
`adb logcat --pid=$(adb shell pidof -s com.cookie.securenotes)`).
**Fix**: spostato tutto il caricamento su `diskIO()`. Testato con successo
su un video di 2 secondi.

## Fase 5 — Tentativo di rimux con `MediaExtractor`/`MediaMuxer`

Nonostante l'opzione A risolvesse già il problema pratico, si è comunque
voluto implementare il rimux "moov in testa" al salvataggio, con l'idea di
poter eventualmente tornare in futuro allo streaming diretto (mai chiaro su
disco) una volta risolto il vincolo strutturale.

**Cosa è stato scritto:**
- `VideoRemuxer.java`: usa `MediaExtractor` per leggere tracce/campioni dal
  video sorgente e `MediaMuxer` (`MUXER_OUTPUT_MPEG_4`) per riscriverli in un
  nuovo file.
- `StoragePaths.getTempDir()`: nuova cartella dedicata (`tmp_video` dentro
  `getCacheDir()`) per i temporanei del rimux.
- Integrazione in `FileRepository.saveFile()`: per `tipo="video"`, prima di
  cifrare, i byte grezzi passavano per il rimux.
- Pulizia dei residui aggiunta in `SecureSession.unlock()`.

**Risultato del test**: il rimux **completava senza errori**, ma il log
conteneva questa riga, esplicita e inequivocabile:

```
MPEG4Writer: The mp4 file will not be streamable.
```

Cioè: **`MediaMuxer` di Android non supporta in nessun modo la scrittura di
un file con `moov` in testa**. Scrive sempre `mdat` mentre arrivano i
campioni e `moov` alla fine — esattamente come fa la fotocamera. Non esiste
un flag o un'opzione per cambiare questo comportamento con le API native.

**Conclusione**: il rimux non risolveva (e non poteva risolvere) il
problema per cui era stato introdotto. Nel frattempo aggiungeva un carico di
lavoro e di memoria interamente inutile al momento dell'importazione (dato
che l'opzione A, già in uso per la riproduzione, rende irrilevante la
posizione di `moov`).

## Fase 6 — Problema di memoria con video reali (più lunghi dei test da 2 secondi)

Testando con un video reale di 3-4 minuti (non più i test brevi usati fino a
questo punto), sono comparsi sintomi nuovi: il video restava a `0:00/0:00`
indefinitamente, e il log si riempiva ininterrottamente di righe come:

```
Waiting for a blocking GC Alloc
WaitForGcToComplete blocked Alloc on Background for ...ms
Background concurrent mark compact GC freed ...
```

con l'heap dell'app in crescita continua (104MB → 170MB+ e oltre).

**Causa**: `loadFile()` (usato all'epoca anche per il video) decifra
**l'intero file in un unico array di byte in RAM**. Per un video di minuti,
questo significa tenere contemporaneamente in memoria più copie quasi
complete del contenuto (cifrato letto da disco, decifrato in RAM, poi
riscritto nel file temporaneo) — nell'ordine di centinaia di MB per un
singolo video, sufficiente a mettere il garbage collector in seria
difficoltà e avvicinarsi a un `OutOfMemoryError`.

Nello stesso giro di test, si è anche notato (osservazione corretta
dell'utente) che uscendo dal viewer prima che il caricamento finisse, il
lavoro **continuava comunque** sul thread unico e condiviso di
`AppExecutors.diskIO()`, bloccando nel frattempo in coda qualunque altra
operazione di I/O/DB richiesta altrove nell'app.

## Fase 7 — Rollback del rimux, decifratura a blocchi

Decisioni prese in questa fase, tutte applicate:

1. **Eliminati** `VideoRemuxer.java`, `EncryptedFileDataSource.java`,
   `StoragePaths.getTempDir()` e la pulizia dei residui di rimux in
   `SecureSession.unlock()` (mantenuta invece quella per `pdf_view_*` /
   `video_view_*`, ancora valida).
2. **Aggiunto** `FileRepository.loadFileToStream(fileEntryId, OutputStream)`:
   decifra a blocchi di 64KB per volta, riutilizzando
   `CryptoManager.decryptStream()` (già scritto in Fase 2, mai eliminato
   perché è generico e non legato a `EncryptedFileDataSource` in sé), e
   scrive ogni blocco direttamente sul file temporaneo. Mai un array
   completo in RAM, indipendentemente dalla lunghezza del video.
3. **Aggiornato** `VideoPlayerActivity` per usare `loadFileToStream()`
   invece di `loadFile()` + scrittura in blocco unico, e aggiunto un campo
   `cancelled` (`volatile boolean`) controllato in più punti del
   caricamento, per uscire prima se l'utente ha già abbandonato la
   schermata (mitigazione parziale al problema del thread condiviso di
   Fase 6 — non risolve un'operazione già in corso, ma evita di continuare
   a fare lavoro/allocazioni superflue dopo che l'utente è già uscito).

## Fase 8 — Stato attuale: nuovo schermo nero, causa non ancora confermata

Dopo i fix di Fase 7, un nuovo test con lo stesso video di 3-4 minuti non ha
più mostrato il loop di GC in affanno (segno che il problema di memoria è
verosimilmente risolto), ma il video resta a schermo nero al play.

**Non ancora determinato con certezza** se si tratti di:
- Un problema in come `loadFileToStream()` è stato effettivamente trascritto
  nel progetto (non ancora confermato dall'utente carattere per carattere).
- Un file temporaneo scritto vuoto o troncato per un bug di chiusura/flush
  degli stream.
- Un `PlaybackException` di ExoPlayer non ancora osservato nei log (il
  listener `onPlayerError` con tag `SecureNotesVideo` è già collegato e
  pronto a intercettarlo).
- Qualcos'altro di non ancora ipotizzato.

**Sessione messa in pausa a questo punto** per dedicarsi alla
documentazione, prima di riprendere il debug.

---

## Cosa verificare alla ripresa del debug

1. Confermare il testo esatto di `FileRepository.loadFileToStream()` come
   attualmente presente nel progetto (non ancora incollato/confermato
   dall'utente in questa sessione).
2. Log filtrato per PID durante un tentativo di riproduzione:
   ```
   adb logcat --pid=$(adb shell pidof -s com.cookie.securenotes)
   ```
3. Verificare se `tempFile` risulta vuoto o troppo piccolo rispetto
   all'originale dopo il caricamento (es. loggando `tempFile.length()`
   subito prima di `setupPlayer()`).
4. Ripetere il test anche con un video breve (2-3 secondi) per capire se il
   problema è generico (qualsiasi video) o legato ancora alla lunghezza.
5. Verificare se compare un `PlaybackException` via il listener già
   collegato (tag `SecureNotesVideo`).

## Decisioni definitive (valide per il proseguimento del progetto)

- **Niente streaming cifrato diretto per il video.** L'incompatibilità
  pratica tra AES/GCM sequenziale e il posizionamento tipico di `moov` nei
  file da fotocamera, unita al fatto che le API native Android non offrono
  un modo per forzare `moov` in testa, rende questa strada non percorribile
  senza introdurre dipendenze esterne pesanti (es. FFmpeg).
- **Il video userà sempre decifratura completa su file temporaneo in
  chiaro**, come il PDF, ma a differenza del PDF la decifratura avviene **a
  blocchi** (`loadFileToStream`) per non caricare mai l'intero contenuto in
  RAM in un colpo solo — necessario per file potenzialmente grandi come i
  video, a differenza di PDF/foto che restano piccoli.
- Il rimux "moov in testa" resta un'idea scartata con le API attuali. Se in
  futuro servisse davvero (per tornare allo streaming), andrebbe rivalutata
  con una libreria esterna dedicata — non prioritario ora.

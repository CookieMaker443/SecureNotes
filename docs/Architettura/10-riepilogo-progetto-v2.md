# SecureNotes — Riepilogo progetto v2 (per ripristinare il contesto)

> Incolla questo documento all'inizio di una nuova chat per riprendere esattamente
> da dove ci si è fermati. Questo file **sostituisce/aggiorna**
> `10-riepilogo-progetto.md` — se disponibile, allega anche i documenti
> `11-livello-ui-viewer.md` e `12-cronistoria-bug-video.md`, scritti nella
> sessione in cui è stato costruito il visualizzatore file.

## Cos'è il progetto

App Android (Java, API 26+, `compileSdk`/`targetSdk` 35) per note personali e file
sensibili (foto, video, PDF), con autenticazione PIN/biometrica, cifratura locale
end-to-end, timeout di sessione configurabile, backup criptato esportabile (non ancora
implementato). Nessun cloud: tutto locale sul dispositivo. Architettura MVVM +
Repository.

## Documentazione completa esistente

Nella cartella `securenotes-docs/` (file scaricabili, consegnati in sessioni
precedenti) ci sono ora **12 file `.md`** + 1 canvas Obsidian
(`SecureNotes4.canvas`, ultima versione). Se disponibili, **leggere prima quei
file** (in particolare `00-panoramica.md` come indice) invece di richiedere di
nuovo le spiegazioni:

```
00-panoramica.md                     — indice, architettura generale, struttura cartelle
01-livello-sicurezza.md              — CryptoManager, SecurePrefsManager
02-livello-sessione.md               — SecureSession, LockManager, BaseActivity, SecureNotesApplication
03-livello-dati.md                   — Entità Room, DAO, Database SQLCipher, StoragePaths
04-livello-repository.md             — NoteRepository, FileRepository
05-flussi-sequenze.md                — Sequenze complete (diagrammi Mermaid)
06-punti-aperti.md                   — Decisioni ancora da prendere
07-livello-ui-notes.md               — ViewModel/Adapter/Activity per le Note
08-livello-ui-archivio.md            — ViewModel/Adapter/Fragment per Foto/Video/PDF
09-livello-ui-dashboard-settings.md  — Dashboard e Impostazioni
10-riepilogo-progetto.md             — versione precedente di questo riepilogo (superata da questo file)
11-livello-ui-viewer.md              — NUOVO: package ui.viewer (Image/Pdf/VideoPlayerActivity)
12-cronistoria-bug-video.md          — NUOVO: storia completa del bug video (in corso)
SecureNotes4.canvas                  — mappa visiva aggiornata di tutte le classi e collegamenti
```

**Nota importante sui documenti 01-09**: descrivono ancora fedelmente lo stato
del codice per quelle aree — non sono stati toccati in questa sessione, tranne
per due piccole aggiunte che *non sono ancora state riportate lì per iscritto*
(vedi sezione "Bugfix applicati" più sotto): un metodo in `FileRepository`
(già in 04), uno in `CryptoManager` (già in 01), una riga in `SecureSession`
(già in 02). I dettagli completi di queste aggiunte sono nel nuovo documento 11.

---

## Architettura in una frase

```
View (Activity/Fragment) -> ViewModel -> Repository -> CryptoManager / Room DAO / filesystem
                          -> LockManager (indipendente, timeout)
                          -> Viewer (ui.viewer, NUOVO) per la visualizzazione di foto/video/PDF
```

Due cifrature separate: il **contenuto** dei file (note, foto, video, PDF) è cifrato
esplicitamente da `CryptoManager` (Android Keystore) prima di toccare il disco; i **due
indici** (`notes_index.db`, `files_index.db`) sono cifrati per intero, a livello di
pagina, da **SQLCipher**, con una chiave dedicata a 32 byte (non l'alias Keystore) che
— quando non è in uso — sta salvata cifrata da `CryptoManager` dentro
`EncryptedSharedPreferences`.

Le note sono trattate **esattamente come i file media**: un file fisico per nota
(`Media/Notes/<uuid>`), indice separato in Room — non testo dentro la riga del DB.

## Struttura cartelle su disco (dispositivo Android)

```
cartella_base/ (context.getFilesDir(), privata dell'app)
  .info/                    -> notes_index.db, files_index.db (Room + SQLCipher)
  Media/
    Video/, Immagini/, PDF/ -> nomi fisici = UUID casuali
    Notes/                  -> stessa logica, un file per nota
```

`getCacheDir()` (privata, non persistente) viene ora usata anche per file
**temporanei in chiaro**, con prefissi riconoscibili: `pdf_view_*` (PDF in
visualizzazione) e `video_view_*` (video in visualizzazione). Vengono
cancellati alla chiusura del viewer, e ripuliti come rete di sicurezza anche
in `SecureSession.unlock()` in caso di kill/crash del processo mentre erano
aperti.

## Struttura package nel codice sorgente

```
com.cookie.securenotes
  SecureNotesApplication.java

  security/
    CryptoManager.java, CryptoException.java
    // CryptoManager ora ha anche: decryptStream(InputStream) -> InputStream
    // (CipherInputStream sequenziale, per decifratura a blocchi)

  data/local/
    prefs/     SecurePrefsManager.java, SecurePrefsException.java, AppSettings.java
    db/        Nota.java, FileEntry.java, NoteDao.java, FileDao.java,
               NotesDatabase.java, FilesDatabase.java
    storage/   StoragePaths.java

  session/
    SecureSession.java, LockManager.java

  util/
    AppExecutors.java

  manager/repository/
    NoteRepository.java, FileRepository.java
    // FileRepository ora ha anche: getById(long) pubblico,
    // loadFileToStream(long, OutputStream) — decifra a blocchi 64KB

  ui/
    common/     BaseActivity.java
    login/      LoginActivity.java
    dashboard/  DashboardActivity.java, DashboardViewModel.java
    settings/   SettingsActivity.java
    notes/      NoteViewModel.java, NoteAdapter.java, NotesListActivity.java, NoteEditorActivity.java
    archivio/   FileViewModel.java, OnFileClickListener.java, FilesAdapter.java,
                FileAdapter.java, FileGridAdapter.java, BaseFileListFragment.java,
                FotoFragment.java, VideoFragment.java, PdfFragment.java,
                ArchivioPagerAdapter.java, ArchivioActivity.java
    viewer/     NUOVO — SecureViewerActivity.java (astratta), ImageViewerActivity.java,
                PdfViewerActivity.java, VideoPlayerActivity.java
```

Layout XML (`res/layout/`): quelli già esistenti, più i nuovi
`activity_image_viewer.xml`, `activity_pdf_viewer.xml`, `activity_video_player.xml`.

`strings.xml`: aggiunte `error_loading_file`, `desc_full_image`,
`desc_pdf_page`, `action_prev_page`, `action_next_page`.

`AndroidManifest.xml`: aggiunte le tre nuove Activity (`android:exported="false"`).

## Dipendenze chiave (`libs.versions.toml` / `app/build.gradle.kts`)

- Room 2.6.1 (`room-runtime`, `room-compiler` via `annotationProcessor`)
- **SQLCipher**: `net.zetetic:android-database-sqlcipher:4.5.4` — attenzione al nome
  esatto dell'artifact, `sqlcipher-android` è un pacchetto diverso e ha causato un bug
  di build risolto in precedenza.
- `androidx.security:security-crypto:1.1.0-alpha06` (EncryptedSharedPreferences)
- `androidx.biometric:biometric:1.2.0-alpha05`
- `androidx.viewpager2:viewpager2:1.1.0`
- **NUOVO**: `androidx.media3:media3-exoplayer:1.4.1`,
  `media3-ui:1.4.1`, `media3-datasource:1.4.1` (player video, richiede
  `@OptIn(UnstableApi)` in un paio di classi)
- AGP 9.2.1, `minSdk=26`, `compileSdk`/`targetSdk=35`

Tutte le dipendenze sono dichiarate con alias nel catalogo `libs.versions.toml`
(niente stringhe dirette in `build.gradle.kts`, incluse le nuove Media3).

---

## Decisioni architetturali chiuse (non ridiscuterle da zero)

Tutte quelle già presenti nella versione precedente del riepilogo, **più**:

- **Video: mai streaming cifrato diretto al player.** Deciso e chiuso dopo
  un'esplorazione approfondita (vedi `12-cronistoria-bug-video.md`):
  incompatibilità pratica tra AES/GCM sequenziale e il posizionamento tipico
  dell'atom `moov` nei video da fotocamera (quasi sempre in fondo al file).
  Le API native Android (`MediaMuxer`) non offrono modo di forzare `moov` in
  testa, quindi anche un tentativo di "rimux" preventivo al salvataggio è
  stato scartato.
- **Video: decifratura sempre su file temporaneo in chiaro, ma a blocchi.**
  Stesso pattern del PDF (file temp in `getCacheDir()`, poi il player legge
  un file reale), ma con decifratura **streaming a blocchi di 64KB**
  (`FileRepository.loadFileToStream`) invece che un unico array in RAM —
  necessario perché i video, a differenza di foto/PDF, possono essere
  grandi abbastanza da causare pressione seria sul garbage collector se
  decifrati tutti insieme.
- **PDF: `PdfRenderer` di sistema impone comunque un file temporaneo.**
  Non è una scelta ma un vincolo della piattaforma: `PdfRenderer` richiede
  un `ParcelFileDescriptor` con accesso casuale reale, non accetta stream
  custom.
- **Pinch-to-zoom sulle immagini: rimandato deliberatamente**, per prima
  far funzionare l'apertura base del viewer. Resta un punto aperto.
- (invariate dalla versione precedente) Singleton con `init()`/`getInstance()`
  separati per `SecureSession`/`LockManager`; `FileViewModel` unico parametrizzato
  per tipo; `AppExecutors` con `diskIO()` a thread singolo + `mainThread()`;
  `BaseActivity`/`BaseFileListFragment` come classi base; cambio PIN solo via
  biometria di sistema; miniature griglia solo per foto (video = icona statica,
  PDF = solo lista).

## Bugfix applicati in questa sessione (chiusi, confermati funzionanti)

- **Status bar sovrapposta**: risolto con `fitsSystemWindows="true"` (confermato
  dall'utente a inizio sessione).
- **Dark mode automatica**: confermato essere comportamento atteso, non un bug.
- **Crash biometria al cold start + "non succede nulla" dopo sblocco da timeout**:
  stessa causa in entrambi i casi. `LoginActivity.onAuthenticationSucceeded`
  chiamava direttamente `navigateToDashboard()` invece di
  `unlockSessionAndProceed()`, saltando l'inizializzazione di `SecureSession`
  e l'avvio di `LockManager`. **Fix**: chiamare `unlockSessionAndProceed()`
  anche nel ramo biometrico.
- **Crash su Video/PDF al click sul tab (ma non allo swipe)**: causato da un
  `ClassCastException` sempre presente in `FileAdapter` (interfaccia
  `OnFileClickListener` duplicata rispetto a quella condivisa in
  `ui.archivio`). **Fix**: rimossa la duplicazione, `FileAdapter` ora usa
  l'interfaccia condivisa.

## Bug noti / in corso (stato incerto o aperto al momento del riepilogo)

- ⚠️ **BUG APERTO E PRIORITARIO — Video: schermo nero al play, 0:00/0:00.**
  Il problema di memoria (garbage collector in affanno su video lunghi) è
  stato risolto con la decifratura a blocchi, ma la riproduzione resta
  nera. Causa non ancora confermata. Vedi `12-cronistoria-bug-video.md` per
  la cronistoria completa e la checklist di cosa verificare alla ripresa
  (confermare il testo esatto di `loadFileToStream()`, log filtrato per
  PID, dimensione del file temporaneo generato, test con video breve vs
  lungo).

## Punti aperti (decisioni di prodotto/sicurezza da prendere, non bug)

Vedi [06-punti-aperti.md](06-punti-aperti.md) per la lista completa e
aggiornata (non modificata in questa sessione). Da aggiungere manualmente a
quel file, generati dal lavoro sul viewer:

- Pinch-to-zoom su `ImageViewerActivity` (rimandato).
- Restringere i MIME type accettati in `VideoFragment.getMimeTypes()` (oggi
  ancora `video/*` generico) — buona norma anche se meno critica ora che il
  rimux non c'è più.
- Se in futuro servisse davvero lo streaming diretto per il video (mai
  chiaro su disco), andrebbe rivalutato con una libreria di muxing esterna
  (es. FFmpeg via binding) — fuori scopo per ora.

Punti aperti pre-esistenti, invariati: `setUserAuthenticationRequired(true)`
sulla chiave Keystore; UI selezione multipla note; requisiti password
backup; root/tamper detection; robustezza hash PIN; hardening
`SupportFactory`/SQLCipher; `allowBackup="true"` nel Manifest.

## Cosa è completo, cosa manca ancora

✅ **Completo e funzionante**: sicurezza (Keystore, SQLCipher, PIN+biometria — bug
biometria chiusi), sessione (login → unlock → dashboard, timeout → lock →
login, ora funzionante anche via biometria), livello dati (Room+SQLCipher,
repository), Note (CRUD completo + navigazione), Archivio (import,
lista/griglia con miniature, ricerca, eliminazione, tab senza più crash),
Dashboard, Impostazioni, **Visualizzatore Foto** (nessun zoom ancora),
**Visualizzatore PDF** (navigazione a bottoni, non gesture).

⬜ **Ancora da fare**, in ordine di priorità aggiornato:

1. **Risolvere il bug del video** (schermo nero) — priorità immediata, vedi
   doc 12 per dove riprendere.
2. Pinch-to-zoom su `ImageViewerActivity`.
3. Modalità selezione multipla per eliminazione note.
4. Export backup criptato (.zip con password PBKDF2) — bottone placeholder
   ancora disabilitato.
5. Offuscamento del codice in release (non ancora affrontato).
6. Decisione su `allowBackup` e sui punti aperti di sicurezza elencati sopra.

---

## Come continuare

Nella prossima chat: se il bug del video non è ancora risolto, si riparte
dalla checklist in fondo a `12-cronistoria-bug-video.md`. In quel caso,
allegare anche `FileRepository.java` (con `loadFileToStream` così com'è
attualmente nel progetto) e `VideoPlayerActivity.java` correnti, più un log
`adb logcat --pid=$(adb shell pidof -s com.cookie.securenotes)` catturato
durante un tentativo di riproduzione.

Se il bug è già stato risolto nel frattempo, indicare da quale altro punto
della lista "ancora da fare" si vuole ripartire.

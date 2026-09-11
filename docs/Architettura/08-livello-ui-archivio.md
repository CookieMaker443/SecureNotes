# Livello UI — Archivio (Foto / Video / PDF)

Classi: `FileViewModel`, `OnFileClickListener`, `FilesAdapter`, `FileAdapter`,
`FileGridAdapter`, `BaseFileListFragment`, `FotoFragment`, `VideoFragment`,
`PdfFragment`, `ArchivioPagerAdapter`, `ArchivioActivity`
Package: `com.cookie.securenotes.ui.archivio`

Stessa filosofia del livello Note (vedi [07-livello-ui-notes.md](07-livello-ui-notes.md)),
ma con due complicazioni in più: **un solo ViewModel condiviso da tre schermate diverse**
(tipo passato come parametro) e **anteprime immagine che richiedono decifratura**.

---

## Decisione di design: un `FileViewModel`, tipo passato ai metodi

Invece di un ViewModel per tipo (`FotoViewModel`, `VideoViewModel`, `PdfViewModel`),
un solo `FileViewModel` i cui metodi (`loadFiles`, `search`, `deleteFile`) ricevono il
`tipo` (`"foto"`\|`"video"`\|`"pdf"`) come parametro. Vantaggio concreto: aggiungere un
quarto tipo in futuro richiede solo una nuova sottoclasse di `BaseFileListFragment` di
poche righe — nessuna nuova classe ViewModel.

---

## `FileViewModel`

**LiveData esposte**: `files` (`List<FileEntry>`), `errorMessage`.

**Metodi principali**:

| Metodo | Cosa fa |
| --- | --- |
| `loadFiles(tipo)` / `search(tipo, query)` | popolano `files` filtrando per tipo |
| `addFile(tipo, uri, resolver)` | importa un file scelto dall'utente (vedi sotto) |
| `deleteFile(tipo, id)` | elimina file fisico + riga indice, invalida la cache miniatura |
| `loadThumbnail(entry, callback)` | genera/recupera la miniatura di una foto (vedi sotto) |

### Importazione file (`addFile`)

Usa il **Storage Access Framework**: l'`Uri` arriva da un file-picker di sistema
(`ActivityResultContracts.OpenDocument`, lanciato dal Fragment). Vantaggio: **nessun
permesso di storage richiesto** — l'utente sceglie esplicitamente il file, l'app riceve
accesso solo a quello, non a tutta la galleria.

```
Uri (dal picker) -> ContentResolver.query() -> nome file (DISPLAY_NAME)
                  -> ContentResolver.openInputStream() -> byte[] in chiaro
                  -> FileRepository.saveFile(nome, tipo, byte[])  [cifra + salva + indicizza]
```

### Miniature (`loadThumbnail`) — solo per foto

I video **non** vengono mai decifrati per l'anteprima — costerebbe troppo (decifrare
un intero video per un frame). Mostrano solo un'icona play statica, gestita
direttamente da `FileGridAdapter` senza toccare il ViewModel.

Per le foto, la sequenza è:

```
1. Cerca in thumbnailCache (LruCache<Long, Bitmap>) -> se presente, ritorna subito
2. Altrimenti, su diskIO():
   a. FileRepository.loadFile(id)          -> byte[] decifrati (foto intera)
   b. decodeSampledBitmap(byte[], 200px)   -> Bitmap ridotto, non a piena risoluzione
   c. Mette in cache, poi richiama il callback su mainThread()
```

`decodeSampledBitmap` fa un doppio passaggio con `BitmapFactory`:
`inJustDecodeBounds=true` per leggere solo le dimensioni (istantaneo, non alloca
pixel), calcola `inSampleSize` (potenza di 2 più vicina al target), poi decodifica
davvero solo a quella risoluzione ridotta — evita di portare in RAM una foto a piena
risoluzione (es. 4000×3000) solo per mostrarne 200×200.

**`thumbnailCache`** è un `LruCache` dimensionato a 1/8 dell'heap disponibile
(criterio standard Android per cache di immagini). Evita di ridecifrare la stessa
immagine ogni volta che l'utente scrolla avanti e indietro nella griglia.
`deleteFile` invalida la entry corrispondente, per non tenere in RAM la miniatura di
un file ormai cancellato.

---

## Interfacce estratte in file propri

A differenza di `LockListener` (annidata in `LockManager`, usata da un solo punto),
`OnFileClickListener` e `FilesAdapter` sono in file a sé, perché **due adapter diversi**
(`FileAdapter` e `FileGridAdapter`) le implementano entrambe — un'interfaccia usata da
più classi indipendenti va estratta, non annidata.

- `OnFileClickListener`: `onFileClick` / `onFileLongClick` — stesso contratto di
  `NoteAdapter.OnNoteClickListener`, ma per `FileEntry`.
- `FilesAdapter`: un solo metodo, `submitList(List<FileEntry>)` — permette a
  `BaseFileListFragment` di aggiornare la lista senza sapere se sta parlando con
  l'adapter a lista o a griglia.

## `FileAdapter` (lista) e `FileGridAdapter` (griglia)

Entrambi implementano `FilesAdapter` e usano `OnFileClickListener`. Differenze:

- `FileAdapter`: usato solo da `PdfFragment`. Riga singola con nome file
  (`item_file.xml`).
- `FileGridAdapter`: usato da `FotoFragment`/`VideoFragment`. Cella quadrata
  (`item_file_grid.xml`) con `ImageView` miniatura, overlay nome file, icona play
  condizionale per i video.

**Punto delicato in `FileGridAdapter.onBindViewHolder`**: `RecyclerView` ricicla gli
holder. Se una miniatura sta ancora decifrando in background quando l'holder viene
riassegnato a un'altra riga (scroll veloce), bisogna verificare che l'holder stia
*ancora* mostrando lo stesso file prima di applicare il bitmap arrivato in ritardo —
altrimenti si vede per un istante la miniatura sbagliata nel posto sbagliato:

```java
int currentPos = holder.getBindingAdapterPosition();
if (currentPos != RecyclerView.NO_POSITION && items.get(currentPos).id == boundEntryId) {
    holder.thumbnail.setImageBitmap(bitmap);
}
```

---

## `BaseFileListFragment`

Stessa funzione di `BaseActivity` ma per i Fragment: **un solo posto** che gestisce
lista/ricerca/FAB/picker/eliminazione, invece di ripeterlo in tre Fragment. Le
sottoclassi specificano solo cosa le distingue tramite override:

| Metodo astratto | `FotoFragment` | `VideoFragment` | `PdfFragment` |
| --- | --- | --- | --- |
| `getTipo()` | `"foto"` | `"video"` | `"pdf"` |
| `getMimeTypes()` | `image/*` | `video/*` | `application/pdf` |
| `useGridLayout()` | `true` | `true` | `false` (default ereditato) |

**Osservazione con `getViewLifecycleOwner()`, non `this`**: la `View` di un Fragment
può essere distrutta (es. quando esce dallo schermo dentro `ViewPager2`) mentre il
Fragment stesso resta vivo più a lungo. Osservare con il lifecycle sbagliato causa
crash o leak quando si torna a una scheda già visitata.

`filePickerLauncher` è un `ActivityResultLauncher<String[]>` registrato una volta nel
costruttore del Fragment (via `registerForActivityResult`) — apre il file-picker di
sistema filtrato sui MIME type di `getMimeTypes()`.

## `ArchivioPagerAdapter` e `ArchivioActivity`

`ArchivioPagerAdapter extends FragmentStateAdapter`: crea `FotoFragment`/
`VideoFragment`/`PdfFragment` in base alla posizione (0/1/2). `ArchivioActivity` ospita
`ViewPager2` + `TabLayout`, collegati tramite `TabLayoutMediator` (sincronizza swipe e
tap sulle schede in entrambe le direzioni). Estende `BaseActivity` come tutte le
Activity post-login.

---

## Layout coinvolti

| File | Usato da |
| --- | --- |
| `activity_archivio.xml` | `ArchivioActivity` — `TabLayout` + `ViewPager2` |
| `fragment_file_list.xml` | `BaseFileListFragment` — ricerca + `RecyclerView` + FAB |
| `item_file.xml` | `FileAdapter` — riga con nome file |
| `item_file_grid.xml` | `FileGridAdapter` — cella quadrata con miniatura/icona play |

---

## Come si collegano tra loro

```
ArchivioActivity
    └── ViewPager2 + TabLayout (TabLayoutMediator)
         └── ArchivioPagerAdapter
              ├── FotoFragment   (tipo="foto",  griglia)
              ├── VideoFragment  (tipo="video", griglia)
              └── PdfFragment    (tipo="pdf",   lista)
                   │  tutti estendono
                   ▼
              BaseFileListFragment
                   ├── FileViewModel (istanza per Fragment, ViewModelProvider(this))
                   │        └── FileRepository (via SecureSession)
                   ├── FileAdapter / FileGridAdapter (in base a useGridLayout())
                   └── ActivityResultLauncher (file-picker di sistema)
```

---

## Stato: cosa è completo, cosa resta aperto

✅ Import file, lista/griglia, ricerca, eliminazione, miniature foto con cache.

⬜ Non ancora implementato:
- **Visualizzatore file** (`onFileClick`): tap su un'immagine/video/PDF non apre
  ancora nulla — resta un `TODO` nei tre fragment.
- **Anteprima video**: solo icona statica, nessun frame reale (scelta di design,
  vedi sopra).
- **Caricamento file grandi in RAM**: stesso limite già segnalato per `loadFile` in
  [04-livello-repository.md](04-livello-repository.md) — rilevante specialmente per
  i video quando arriverà il visualizzatore.

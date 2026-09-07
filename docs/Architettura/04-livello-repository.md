# Livello repository

Classi: `NoteRepository`, `FileRepository`
Package: `com.cookie.securenotes.manager.repository`

**Ruolo**: unico punto di contatto tra il futuro ViewModel e tutto il resto del
backend. Mettono in comunicazione due mondi che altrimenti non si parlerebbero:

- Il **DAO** (metadati in chiaro: titolo/nome, date, tipo) — non tocca mai i byte
  cifrati dei contenuti.
- **`CryptoManager`** (contenuto cifrato dei file) — non tocca mai il DB.

Entrambi i repository ricevono un solo parametro nel costruttore: `SecureSession`.
Da lì estraggono tutto ciò che serve (`session.getFilesDatabase().fileDao()`,
`session.getCryptoManager()`, `session.getStoragePaths()`) — un solo punto di verità
invece di passare tre dipendenze separate.

## Le quattro operazioni, stesso schema per entrambi

| Operazione | Cosa fa il Repository | Cosa fa sul DAO |
| --- | --- | --- |
| **Salva** (`saveNote` / `saveFile`) | genera UUID, cifra il contenuto con `CryptoManager`, scrive il file fisico, **poi** inserisce la riga | `dao.insert(riga)` |
| **Cerca** (`searchNote` / `searchFile`) | — | `dao.search(query)` → righe di metadati |
| **Carica** (`loadNote` / `loadFile`) | trova la riga, legge il file fisico dal path indicato, lo decifra | `dao.getById(id)` |
| **Elimina** (`deleteNote` / `deleteFile`) | cancella il file fisico dal disco | `dao.delete(riga)` |

### Perché prima il file, poi il DB (ordine importante)

In `saveNote`/`saveFile`, il file fisico viene scritto **prima** di inserire la riga
nell'indice. Se la scrittura del file fallisce, l'eccezione interrompe il metodo prima
che il DB sappia qualcosa — non resta mai una riga "fantasma" che punta a un file
inesistente. L'ordine inverso sarebbe più rischioso.

### Differenza tra i due: stringhe vs byte

- `NoteRepository` usa `cryptoManager.encryptToString()` / `decryptFromString()` e
  `java.nio.file.Files` (NIO.2, disponibile da API 26) per leggere/scrivere il
  contenuto testuale.
- `FileRepository` usa `cryptoManager.encrypt(byte[])` / `decrypt(byte[])` e stream
  (`FileOutputStream`) per i contenuti binari, evitando l'overhead di Base64
  (~33% di spazio/CPU in più) inutile per foto/video/PDF.

### Limite noto, da tenere a mente

`FileRepository.loadFile()` carica **l'intero file in RAM**
(`Files.readAllBytes`). Accettabile per foto e PDF di dimensioni normali; per **video
lunghi** può diventare un problema di memoria. Servirebbe un approccio a stream
(decifrare a blocchi mentre il player legge) — non ancora implementato, rimandato a
quando si costruirà la schermata di riproduzione video.

---

## Come si collegano tra loro

```
NoteRepository / FileRepository
    │
    ├──costruttore──► SecureSession (session.getXxxDatabase(), getCryptoManager(), getStoragePaths())
    │
    ├──scrive/legge contenuto──► CryptoManager (encrypt/decrypt)
    │
    ├──scrive/legge metadati──► NoteDao / FileDao ──► Nota / FileEntry
    │
    └──scrive/legge file fisico──► StoragePaths (path delle cartelle Media/...)
```

Vedi [05-flussi-sequenze.md](05-flussi-sequenze.md) per il flusso completo di
salvataggio e caricamento passo per passo.

# Livello dati

Classi: `Nota`, `FileEntry`, `NoteDao`, `FileDao`, `NotesDatabase`, `FilesDatabase`,
`StoragePaths`
Package: `com.cookie.securenotes.data.local.db`, `com.cookie.securenotes.data.local.storage`

---

## Entità Room: `Nota` e `FileEntry`

Classi Java annotate `@Entity` — non file su disco, definizioni che Room legge in fase
di compilazione per generare automaticamente le tabelle SQL corrispondenti.

Le note sono trattate **esattamente come i file** (foto/video/PDF): un file fisico per
nota, indice separato. Le due entità sono quindi strutturalmente identiche:

| `Nota` (tabella `notes`) | `FileEntry` (tabella `files`) |
| --- | --- |
| `id` (PK autogenerata) | `id` (PK autogenerata) |
| `titolo` — in chiaro, per ricerca `LIKE` | `nomeOriginale` — in chiaro, per ricerca `LIKE` |
| `nomeFisico` — UUID, punta al file in `Media/Notes/` | `nomeFisico` — UUID, punta al file in `Media/{tipo}/` |
| `dataCreazione`, `dataModifica` | `tipo` (`foto`\|`video`\|`pdf`), `dimensioneByte`, `dataCreazione` |

**Perché titolo/nomeOriginale restano "in chiaro" nel DB**: non è un errore di
sicurezza. L'intero file `.db` è già cifrato per intero da SQLCipher (vedi sotto) — a
livello di codice Java quei campi non passano da `CryptoManager` perché altrimenti le
query `LIKE '%testo%'` (ricerca parziale) smetterebbero di funzionare. È la stessa
ragione per cui il documento di progetto aveva scartato l'idea di un indice con nomi
hashati.

## DAO: `NoteDao` e `FileDao`

**DAO = Data Access Object**. Interfacce (non classi concrete) dove si scrivono solo le
firme dei metodi con annotazioni Room (`@Insert`, `@Query`, `@Delete`...). Room genera
automaticamente, in fase di compilazione, l'implementazione concreta che traduce ogni
annotazione in SQL vero eseguito su SQLite.

Metodi principali, identici nella struttura per entrambi: `insert`, `delete`,
`getById`, `search(query)` (con `LIKE`), `getAll` / `getAllByTipo`.

**Chi li chiama**: solo `NoteRepository`/`FileRepository` — nessun altro livello
dell'app tocca direttamente un DAO.

## `NotesDatabase` e `FilesDatabase`

`RoomDatabase` astratte, il punto in cui Room e SQLCipher si incontrano:

```java
File dbFile = new File(context.getDir(".info", Context.MODE_PRIVATE), "notes_index.db");
SupportFactory factory = new SupportFactory(dbKey); // dbKey: byte[] grezzo, non da CryptoManager
Room.databaseBuilder(context, NotesDatabase.class, dbFile.getAbsolutePath())
    .openHelperFactory(factory)
    .build();
```

- `context.getDir(".info", MODE_PRIVATE)` crea la cartella `.info/` se non esiste già —
  nessun codice a parte deve occuparsene.
- `SupportFactory(dbKey)` sostituisce l'apertura standard di Room con una che richiede
  la passphrase SQLCipher. **`dbKey` arriva da `SecurePrefsManager.getOrCreateDatabaseKey()`,
  non è generato qui.**
- `close()` è ereditato da `RoomDatabase`, non va reimplementato.

### Come cifra davvero SQLCipher (precisazione importante)

Non è "tutto in chiaro mentre l'app gira, poi ricifrato alla chiusura". SQLCipher
cifra/decifra **a livello di pagina** (blocchi ~4KB), al volo, ogni volta che SQLite
legge o scrive una pagina. Il file su disco è **sempre cifrato, in ogni istante** —
solo la singola pagina in uso in quel momento sta temporaneamente in chiaro nella
cache RAM di SQLite. `close()` scarica la cache e chiude il file handle; non c'è un
"passo di ricifratura" a parte, perché il file non è mai stato scritto in chiaro.

## `StoragePaths`

**Ruolo**: unico punto che sa dove vivono le cartelle `Media/Video`, `Media/Immagini`,
`Media/PDF`, `Media/Notes`, e **garantisce che esistano** (`mkdirs()` nel costruttore,
lancia `IllegalStateException` se la creazione fallisce).

Istanziata una sola volta, dentro `SecureSession.unlock()` — così nessun repository
deve mai chiedersi "esisterà questa cartella?".

---

## Come si collegano tra loro

```
NotesDatabase ──openHelperFactory──► SupportFactory(dbKey) ──► SQLCipher
     │
     └──abstract method──► NoteDao ──opera su──► Nota

FilesDatabase ──openHelperFactory──► SupportFactory(dbKey) ──► SQLCipher
     │
     └──abstract method──► FileDao ──opera su──► FileEntry

StoragePaths ──garantisce esistenza──► Media/Video, Media/Immagini, Media/PDF, Media/Notes
```

`dbKey` è lo stesso `byte[]` per entrambi i database, recuperato una sola volta in
`SecureSession.unlock()` e passato a entrambe le `open()`. Vedi
[01-livello-sicurezza.md](01-livello-sicurezza.md) per come quella chiave è protetta
quando non è in uso.

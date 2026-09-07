# SecureNotes — Panoramica dell'architettura

> Indice della documentazione tecnica. Ogni file copre un livello dell'architettura.

## Cos'è il progetto

App Android (Java, API 26+) per note personali e file sensibili (foto, video, PDF), con
autenticazione PIN/biometrica, cifratura locale end-to-end, timeout di sessione, backup
criptato esportabile. Nessun cloud: tutto locale sul dispositivo.

## Pattern architetturale

**MVVM + Repository**:

```
View (Activity/Fragment)
   -> ViewModel (stato UI, LiveData, sopravvive a rotazione schermo)
      -> NoteRepository / FileRepository
         -> CryptoManager (cifra/decifra, Keystore)
         -> Room DAO (query su DB cifrato da SQLCipher)
         -> filesystem (Media/...)
   -> LockManager (indipendente, gestisce solo PIN/timeout)
```

Le Activity restano View sottili. La logica di stato vive nei ViewModel, la logica di
dominio nei Repository e nei Manager di sicurezza.

## I livelli documentati

| File | Cosa contiene |
| --- | --- |
| [01-livello-sicurezza.md](01-livello-sicurezza.md) | `CryptoManager`, `SecurePrefsManager` e le loro eccezioni |
| [02-livello-sessione.md](02-livello-sessione.md) | `SecureSession`, `LockManager`, `BaseActivity`, `SecureNotesApplication` |
| [03-livello-dati.md](03-livello-dati.md) | Entità Room, DAO, Database (SQLCipher), `StoragePaths` |
| [04-livello-repository.md](04-livello-repository.md) | `NoteRepository`, `FileRepository` |
| [05-flussi-sequenze.md](05-flussi-sequenze.md) | Sequenze complete: login, salvataggio, caricamento, eliminazione, timeout |
| [06-punti-aperti.md](06-punti-aperti.md) | Decisioni ancora da prendere |
| [07-livello-ui-notes.md](07-livello-ui-notes.md) | `AppExecutors`, `NoteViewModel`, `NoteAdapter`, `NotesListActivity`, `NoteEditorActivity` |

Vedi anche `SecureNotes.canvas` (apribile in Obsidian) per una vista visiva dei
collegamenti tra tutte le classi.

## Struttura cartelle su disco

```
cartella_base/ (context.getFilesDir(), privata dell'app)
  .info/                    -> notes_index.db, files_index.db (Room + SQLCipher)
                               + altri file di configurazione (es. EncryptedSharedPreferences)
  Media/
    Video/                  -> nomi fisici = UUID casuali
    Immagini/
    PDF/
    Notes/                  -> anche le note sono trattate come file, un file per nota
```

## Principio di fondo: chi cifra cosa

Ci sono **due cifrature indipendenti** che lavorano in sequenza, gestite da meccanismi
diversi — è la fonte di confusione più comune, vale la pena fissarla bene:

1. **Contenuto dei file** (testo delle note, byte di foto/video/PDF) → cifrato
   esplicitamente da `CryptoManager`, usando la chiave nell'Android Keystore.
2. **Il file `.db` degli indici** (`notes_index.db`, `files_index.db`) → cifrato per
   intero, a livello di pagina, da **SQLCipher**, usando una chiave dedicata a 32 byte
   generata a parte — non l'alias Keystore. SQLCipher ha il suo motore di cifratura
   interno e non passa da `CryptoManager` per farlo.

Il collegamento tra i due: quella chiave dedicata per SQLCipher, quando non è in uso,
sta salvata cifrata da `CryptoManager` dentro `EncryptedSharedPreferences`
(gestito da `SecurePrefsManager`). Vedi [01-livello-sicurezza.md](01-livello-sicurezza.md).

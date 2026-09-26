# NoteRepository

## Ruolo

Coordina indice delle note e file cifrato in `Media/Notes`.

## Metodi

- `saveNote` genera UUID, cifra il testo Base64, salva il file e inserisce una nuova `Nota`.
- `updateNote` recupera la nota esistente, riscrive il suo file cifrato, aggiorna titolo e data di modifica, poi invoca `NoteDao.update`.
- `loadNote` legge indice e file, poi decifra il testo.
- `importNote` crea una nota da contenuto in chiaro proveniente da un backup: genera un
  nuovo UUID, cifra con la chiave locale e conserva le date originali dal manifest.
- `searchNote`, `getAllNote`, `getRecentNotes`, `getTitolo` leggono dal DAO; 
- `deleteNote` cancella file e riga.

## Collegamenti

[NoteViewModel](../../../ui/notes/NoteViewModel.md) lo usa in background.
[ExporterManager](../backup/ExporterManager.md) usa `loadNote` nell'export e
`importNote` nel ripristino. Combina [NoteDao](../../../data/local/db/NoteDao.md),
[StoragePaths](../../../data/local/storage/StoragePaths.md) e
[CryptoManager](../../../security/CryptoManager.md).

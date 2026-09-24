# NotesDatabase

## Ruolo e metodi

Database Room/SQLCipher delle `Nota`. `noteDao()` espone il DAO; `open(Context, byte[])` apre `notes_index.db` nella directory `.info`, passando la chiave a `SupportFactory`.

## Collegamenti

[SecureSession](../../../session/SecureSession.md) lo apre; [NoteRepository](../../../manager/repository/NoteRepository.md) usa il DAO.

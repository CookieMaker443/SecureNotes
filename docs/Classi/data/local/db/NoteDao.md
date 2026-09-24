# NoteDao

## Ruolo e metodi

DAO Room per `notes`: `insert`, `update` e `delete` modificano una `Nota`; `getById` la legge; `search(query)` cerca titoli; `getAll()` ordina per modifica; `getRecent(limit)` limita lo stesso risultato.

## Collegamenti

[NotesDatabase](NotesDatabase.md) lo fornisce a [NoteRepository](../../../manager/repository/NoteRepository.md). `update` è disponibile nel DAO ma non è chiamato dal repository attuale.

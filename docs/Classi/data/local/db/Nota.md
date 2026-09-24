# Nota

## Ruolo e campi

Entità Room della tabella `notes`: `id`, `titolo`, UUID `nomeFisico` del contenuto cifrato in `Media/Notes` e date di creazione/modifica. Il titolo rimane nell'indice SQLCipher per la ricerca `LIKE`.

## Collegamenti

[NoteRepository](../../../manager/repository/NoteRepository.md) collega questa riga al file; [NoteAdapter](../../../ui/notes/NoteAdapter.md) la visualizza.

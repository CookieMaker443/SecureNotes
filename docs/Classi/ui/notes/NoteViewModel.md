# NoteViewModel

## Ruolo e metodi

ViewModel per elenco, ricerca, salvataggio e caricamento. I getter espongono LiveData. loadAllNotes e search interrogano NoteRepository; saveNote crea o aggiorna secondo l'id; deleteNote elimina e ricarica; loadNoteContent pubblica titolo e testo. Tutto l'I/O usa AppExecutors.

## Collegamenti

NotesListActivity osserva lista/errori e NoteEditorActivity i dati di editing.

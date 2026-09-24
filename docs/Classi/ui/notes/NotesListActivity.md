# NotesListActivity

## Ruolo e metodi

Schermata per consultare, cercare, creare ed eliminare note. onCreate configura toolbar, NoteViewModel, RecyclerView, ricerca, FAB e osservatori. onResume ricarica; onSupportNavigateUp torna indietro. confirmDelete mostra il dialogo e invoca deleteNote.

## Collegamenti

NoteAdapter apre NoteEditorActivity. NoteViewModel passa da NoteRepository.

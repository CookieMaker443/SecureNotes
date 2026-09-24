# NoteEditorActivity

## Ruolo e metodi

Editor usato sia per creare sia per aprire una nota. newCreateIntent costruisce l'intent senza id; newEditIntent passa l'id. onCreate imposta ViewModel, toolbar, campi e osservatori; se esiste l'id chiede il contenuto. save valida il titolo e passa titolo/contenuto al ViewModel. onSupportNavigateUp chiude.

## Collegamenti

È aperta da lista e dashboard; persiste tramite NoteViewModel e NoteRepository. Quando riceve un id, il salvataggio aggiorna la stessa nota.

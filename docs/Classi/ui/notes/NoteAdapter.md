# NoteAdapter

## Ruolo e metodi

Adapter RecyclerView della lista note. OnNoteClickListener separa click e click lungo. submitList sostituisce l'elenco e aggiorna l'adapter. onCreateViewHolder infla item_note; onBindViewHolder mostra Nota.titolo e inoltra i callback; getItemCount conta gli elementi. NoteViewHolder conserva il TextView.

## Collegamenti

NotesListActivity lo usa per aprire o eliminare; DashboardActivity lo riusa per l'anteprima.

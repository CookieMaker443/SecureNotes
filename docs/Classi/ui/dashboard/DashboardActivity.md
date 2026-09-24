# DashboardActivity

## Ruolo e metodi

Home autenticata. onCreate crea AppSettings e DashboardViewModel, configura i pulsanti verso note, archivio e impostazioni e un NoteAdapter per le note recenti. onResume ricarica il numero configurato di note, dopo il controllo sicurezza di BaseActivity.

## Collegamenti

Apre NotesListActivity, ArchivioActivity, SettingsActivity e NoteEditorActivity. Osserva DashboardViewModel.

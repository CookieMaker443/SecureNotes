# DashboardViewModel

## Ruolo e metodi

ViewModel della dashboard. Il costruttore ottiene NoteRepository dalla sessione. getRecentNotes espone LiveData; loadRecentNotes(count) interroga il repository su AppExecutors e pubblica la lista sul thread UI.

## Collegamenti

DashboardActivity osserva i risultati e li invia al NoteAdapter.

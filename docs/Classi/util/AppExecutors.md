# AppExecutors

## Ruolo

Singleton che separa il lavoro lento dal thread grafico Android.

## Metodi

- `getInstance()` crea o restituisce l'unica istanza.
- `diskIO()` restituisce un `ExecutorService` a singolo thread: DAO, cifratura e file system
  vi vengono eseguiti in ordine, senza bloccare la UI.
- `mainThread(Runnable)` pubblica il risultato sul `Looper` principale.

## Collegamenti

[NoteViewModel](../ui/notes/NoteViewModel.md), [FileViewModel](../ui/archivio/FileViewModel.md),
[DashboardViewModel](../ui/dashboard/DashboardViewModel.md) e i viewer lo usano per alternare
operazioni I/O e aggiornamenti di `LiveData`/viste.

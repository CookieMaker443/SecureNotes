# FileViewModel

## Ruolo e metodi

ViewModel dell'archivio. I getter espongono file ed errori. loadFiles e search interrogano FileRepository. addFile recupera nome e byte dall'URI con queryDisplayName e readAllBytes, poi salva; deleteFile elimina, svuota la cache e ricarica. loadThumbnail cerca nella LruCache oppure decifra, riduce e pubblica la bitmap. decodeSampledBitmap legge prima le dimensioni, calcola il campionamento e poi decodifica.

## Collegamenti

BaseFileListFragment osserva la lista; FileGridAdapter riceve miniature. Dipende da FileRepository e AppExecutors.

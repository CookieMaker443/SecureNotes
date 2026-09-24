# PdfViewerActivity

## Ruolo e metodi

Visualizza un PDF decifrato pagina per pagina. onCreate carica il file con FileRepository, crea un PDF temporaneo in cache, apre ParcelFileDescriptor e PdfRenderer, poi mostra la prima pagina. showPage controlla i limiti, chiude la pagina precedente, la renderizza in Bitmap e la mostra. onDestroy chiude pagina, renderer e descrittore e cancella il temporaneo.

## Collegamenti

BaseFileListFragment lo apre per PDF. SecureSession pulisce file pdf_view residui allo sblocco.

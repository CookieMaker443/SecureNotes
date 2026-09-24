# SecureViewerActivity

## Ruolo e metodi

Base dei viewer protetti. EXTRA_FILE_ID è la chiave usata nell'intent. getFileId legge l'id, con -1 se mancante; onSupportNavigateUp chiude.

## Collegamenti

BaseFileListFragment passa l'id a ImageViewerActivity, PdfViewerActivity e VideoPlayerActivity. Eredita BaseActivity e quindi la protezione sessione.

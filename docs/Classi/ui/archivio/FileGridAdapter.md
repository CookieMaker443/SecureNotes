# FileGridAdapter

## Ruolo e metodi

Adapter a griglia per foto e video. ThumbnailLoader e ThumbnailReadyCallback caricano bitmap fuori dall'adapter. submitList aggiorna i dati; onCreateViewHolder infla item_file_grid; onBindViewHolder azzera la miniatura riciclata, mostra play per video e richiede la miniatura per foto. Prima di applicarla verifica id e posizione dell'holder, evitando immagini errate dopo scroll rapido. GridViewHolder conserva immagini e testo.

## Collegamenti

BaseFileListFragment passa FileViewModel come loader; i click passano a OnFileClickListener.

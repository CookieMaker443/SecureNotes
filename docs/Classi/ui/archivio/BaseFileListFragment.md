# BaseFileListFragment

## Ruolo e metodi

Classe astratta con il comportamento comune delle categorie archivio. Le sottoclassi definiscono getTipo e getMimeTypes e possono modificare griglia e colonne. Il launcher OpenDocument invia l'URI a FileViewModel. onCreateView infla fragment_file_list; onViewCreated configura adapter, ricerca, FAB, observer e callback. Il click sceglie il viewer dal tipo; il click lungo chiama confirmDelete. onResume ricarica la categoria.

## Collegamenti

È estesa da FotoFragment, VideoFragment e PdfFragment. Usa FileAdapter o FileGridAdapter e apre i viewer.

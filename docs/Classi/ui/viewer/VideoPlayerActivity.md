# VideoPlayerActivity

## Ruolo e metodi

Riproduce video decifrati. onCreate recupera FileEntry e crea un temporaneo in cache; FileRepository.loadFileToStream lo riempie a blocchi, poi setupPlayer configura ExoPlayer e avvia la riproduzione. onStop mette in pausa. onDestroy marca il lavoro annullato, rilascia il player e cancella il temporaneo.

## Collegamenti

BaseFileListFragment lo apre per video. Usa AppExecutors, FileRepository e SecureSession; la decifratura streaming passa da CryptoManager.

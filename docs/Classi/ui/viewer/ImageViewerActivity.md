# ImageViewerActivity

## Ruolo e metodi

Visualizza un'immagine decifrata. onCreate prepara la vista, legge l'id tramite SecureViewerActivity e costruisce FileRepository. Su AppExecutors carica e decifra i byte, li converte in Bitmap e aggiorna ImageView sul thread UI. In caso di errore mostra un messaggio e chiude.

## Collegamenti

È scelto da BaseFileListFragment per tipo foto e usa FileRepository e SecureSession.

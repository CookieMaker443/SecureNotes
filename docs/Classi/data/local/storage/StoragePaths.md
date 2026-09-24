# StoragePaths

## Ruolo

Prepara e fornisce le directory private `files/Media/Video`, `Images`, `PDF` e `Notes`.

## Metodi

Il costruttore crea tutte le cartelle tramite `ensureExists`; se `mkdirs()` fallisce interrompe
l'operazione con `IllegalStateException`. I getter `getVideoDir`, `getImmaginiDir`, `getPdfDir`
e `getNotesDir` restituiscono le quattro directory.

## Collegamenti

[SecureSession](../../../session/SecureSession.md) lo crea allo sblocco. I repository lo usano
per risolvere il file fisico associato ai metadati Room.

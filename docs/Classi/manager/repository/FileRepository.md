# FileRepository

## Ruolo

Confine tra indice SQLCipher, file privati e cifratura. Dal `SecureSession` riceve DAO, `CryptoManager` e `StoragePaths`.

## Metodi

- `saveFile` genera UUID, cifra i byte, li scrive nella directory del tipo e inserisce `FileEntry`.
- `loadFile` legge e decifra tutto; 
- `loadFileToStream` decifra in blocchi da 64 KiB per i video.
- `importFile` riceve uno stream in chiaro da un backup, lo ricifra a blocchi con la
  chiave del dispositivo corrente, assegna un nuovo UUID e conserva data e dimensione
  riportate nel manifest.
- `searchFile`, `getAllByTipo`, `getById` delegano al DAO; 
- `deleteFile` elimina file e poi indice.
- Privatamente `getDirByTipo` associa `foto`/ `video`/ `pdf` alla directory corretta.

## Collegamenti

[FileViewModel](../../../ui/archivio/FileViewModel.md) importa/lista; i viewer caricano il contenuto.
[ExporterManager](../backup/ExporterManager.md) usa `loadFileToStream` nell'export e
`importFile` nel ripristino. Usa [CryptoManager](../../../security/CryptoManager.md) e
[FileDao](../../../data/local/db/FileDao.md).

# FileEntry

## Ruolo e campi

Entità Room della tabella `files`: `id`, nome mostrato `nomeOriginale`, UUID fisico `nomeFisico`, `tipo` (`foto`, `video` o `pdf`), dimensione e data di creazione.

## Collegamenti

[FileRepository](../../../manager/repository/FileRepository.md) crea e cerca queste entità tramite [FileDao](FileDao.md); adapter, fragment e viewer le usano per visualizzare o aprire un file.

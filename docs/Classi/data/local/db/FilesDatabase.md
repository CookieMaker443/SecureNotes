# FilesDatabase

## Ruolo e metodi

Database Room/SQLCipher per `FileEntry`. `fileDao()` espone il DAO. `open(Context, byte[])` apre `files_index.db` nella directory privata `.info` con la chiave data a `SupportFactory`.

## Collegamenti

[SecureSession](../../../session/SecureSession.md) lo apre usando la chiave di [SecurePrefsManager](../prefs/SecurePrefsManager.md); [FileRepository](../../../manager/repository/FileRepository.md) ne ottiene il DAO.

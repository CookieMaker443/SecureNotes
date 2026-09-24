# SecureSession

## Ruolo

Singleton che rappresenta una sessione autenticata. Centralizza `CryptoManager`, database
SQLCipher aperti e percorsi di storage, evitando che la UI costruisca tali dipendenze.

## Metodi

- `init(CryptoManager)` inizializza una sola istanza; `getInstance()` fallisce esplicitamente se
  il login non l'ha ancora inizializzata.
- `unlock(Context, SecurePrefsManager)` recupera/genera la chiave DB, apre
  `NotesDatabase` e `FilesDatabase`, crea `StoragePaths`, elimina cache temporanee PDF/video e
  imposta `unlocked`.
- `lock()` chiude i database e annulla il flag; `isUnlocked()` viene verificato dalle Activity.
- I getter espongono le dipendenze già aperte ai repository.

## Collegamenti

[LoginActivity](../ui/login/LoginActivity.md) la apre; [LockManager](LockManager.md) la chiude;
i repository ricevono questa classe nel costruttore.

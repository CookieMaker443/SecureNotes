# SecurePrefsManager

## Ruolo

Archivia PIN, flag biometrico e chiave database in `EncryptedSharedPreferences`, protette da un `MasterKey` AndroidX.

## Metodi

- Il costruttore crea il `MasterKey` AES-256-GCM e lo storage cifrato.
- `savePin` crea un salt casuale e salva hash SHA-256; `isPinCorrect` lo ricrea; `hasPin` verifica la configurazione.
- `setBiometricEnabled` / `isBiometricEnabled` gestiscono il flag biometrico.
- `getOrCreateDatabaseKey(CryptoManager)` crea 32 byte se necessari, li cifra e salva Base64; altrimenti li decifra.

## Collegamenti

[LoginActivity](../../../ui/login/LoginActivity.md) gestisce PIN/biometria; [SecureSession](../../../session/SecureSession.md) usa la chiave per aprire i database.
Per il confronto con `CryptoManager`, le chiavi e il flusso della chiave SQLCipher,
vedi [Crittografia e preferenze](CrittografiaEPreferenze.md).

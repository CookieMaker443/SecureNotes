# CryptoManager

## Ruolo

Gestisce una chiave AES nel `AndroidKeyStore` (alias `SecureNotesKey`) e cifra con AES/GCM.
La chiave non viene esportata dall'archivio sicuro del dispositivo.

## Metodi

- Il costruttore apre il keystore e invoca privatamente `generateKey()` se l'alias manca.
- `encryptToString` / `decryptFromString` cifrano testo UTF-8 e lo codificano/decodificano Base64:
  sono usati per il contenuto delle note.
- `encrypt` / `decrypt` lavorano su `byte[]`: sono usati per immagini, PDF e chiavi DB.
- `decryptStream(InputStream)` legge prima l'IV di 12 byte e restituisce un `CipherInputStream`;
  serve alla decifratura sequenziale dei video.

Ogni cifratura antepone l'IV casuale al ciphertext; GCM rileva dati alterati in decifratura.

## Collegamenti

[SecurePrefsManager](../data/local/prefs/SecurePrefsManager.md) cifra la chiave SQLCipher;
[NoteRepository](../manager/repository/NoteRepository.md) e
[FileRepository](../manager/repository/FileRepository.md) cifrano i contenuti.
La differenza tra le due classi e i livelli di protezione è illustrata in
[Crittografia e preferenze](../data/local/prefs/CrittografiaEPreferenze.md).

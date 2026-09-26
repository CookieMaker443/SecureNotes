# `BackupCrypto`

Package: `com.cookie.securenotes.security`
File: `BackupCrypto.java`

## Ruolo

Cifra/decifra il **blob intero** del file di backup (`.secnotes`) con una password
scelta dall'utente al momento dell'export. È l'unica classe di sicurezza dell'app
che **non** passa dall'Android Keystore — di proposito: la chiave deve poter essere
ricostruita anche su un device diverso da quello che ha fatto l'export (tutto il
senso di un backup), e una chiave Keystore non è mai esportabile per definizione.

## Come si differenzia da `CryptoManager`

| | `CryptoManager` | `BackupCrypto` |
| --- | --- | --- |
| Dove vive la chiave | Android Keystore (mai in RAM come byte grezzi) | Derivata al volo da una password, vive solo in RAM per la durata dell'operazione |
| Portabilità | Legata a QUESTO device | Ricreabile ovunque, con la stessa password |
| Algoritmo di derivazione | Nessuno (la chiave la genera il Keystore) | PBKDF2WithHmacSHA256, 150.000 iterazioni |
| Cifratura effettiva | AES/GCM | AES/GCM (stesso algoritmo, chiave diversa) |

## Metodi pubblici

| Metodo | Cosa fa |
| --- | --- |
| `encryptStream(char[] password, InputStream zipInChiaro, OutputStream dest)` | Genera salt (16 byte) e IV (12 byte) nuovi, li scrive in chiaro in testa a `dest`, poi cifra a blocchi (64KB) tutto quello che legge da `zipInChiaro` |
| `decryptStream(char[] password, InputStream sorgenteCifrata, OutputStream zipDecifrato)` | Legge salt+IV dalla testa, deriva la stessa chiave, decifra a blocchi. Se la password è sbagliata, il tag GCM non torna → `BackupCryptoException` |

Metodo privato `deriveKey(char[] password, byte[] salt)`: il cuore della classe —
PBKDF2 rende deliberatamente costosa (150.000 iterazioni) la derivazione, per
rallentare un eventuale bruteforce offline su un file `.secnotes` rubato/perso.

## Formato prodotto

```
[salt — 16 byte, in chiaro] [IV — 12 byte, in chiaro] [ciphertext + tag GCM]
```

Salt e IV non sono segreti (servono solo a rifare lo stesso percorso in decrypt) —
il segreto resta solo la password, mai scritta su disco in nessuna forma.

## Perché `char[]` e non `String` per la password

`String` in Java è immutabile: una volta creata, resta in memoria finché il garbage
collector non decide di liberarla, senza modo di "azzerarla" a comando. `char[]` si
può sovrascrivere esplicitamente (`Arrays.fill(password, '\0')`) subito dopo l'uso —
è quello che fa `BackupViewModel` in ogni `finally`.

## Chi la usa

Solo `ExporterManager` — mai chiamata direttamente da `SettingsActivity` o da
`BackupViewModel`, coerente con la regola "solo l'orchestratore tocca la cifratura
del blob".

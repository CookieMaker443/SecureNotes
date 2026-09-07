# Livello sicurezza

Classi: `CryptoManager`, `CryptoException`, `SecurePrefsManager`, `SecurePrefsException`
Package: `com.cookie.securenotes.security`, `com.cookie.securenotes.data.local.prefs`

---

## `CryptoManager`

**Ruolo**: wrapper/facade sopra l'Android Keystore. Non implementa alcun algoritmo di
cifratura da solo — orchestra due API standard Java/Android:

- **`KeyStore`** (provider `AndroidKeyStore`): gestisce la chiave. La chiave grezza
  **non esce mai** da lì, nemmeno verso il codice Java — `getSecretKey()` ritorna solo
  un riferimento (`SecretKey`) verso una chiave custodita dal sistema (spesso in
  hardware dedicato, TEE/StrongBox).
- **`Cipher`** (JCA): esegue l'operazione di cifratura/decifratura vera, delegata al
  provider Keystore.

**Due famiglie di metodi pubblici**, entrambe implementate sopra la stessa logica
condivisa (IV casuale a 12 byte + AES/GCM, tag a 128 bit):

| Metodo | Uso | Formato output |
| --- | --- | --- |
| `encryptToString(String)` / `decryptFromString(String)` | Note, metadati, chiavi da salvare come stringa | Base64 (`NO_WRAP`) |
| `encrypt(byte[])` / `decrypt(byte[])` | Foto, video, PDF | byte grezzi, IV concatenato in testa |

**Errori**: ogni fallimento (init, cifratura, decifratura — incluso il caso di dati
manomessi, che GCM rileva tramite `AEADBadTagException`) viene propagato come
`CryptoException` custom, mai `null` silenzioso. Il chiamante (Repository) decide come
reagire.

**Generazione chiave**: `generateKey()` è idempotente — genera l'alias
`SecureNotesKey` solo se non esiste già, chiamato dal costruttore.

> ⚠ Punto aperto: `setUserAuthenticationRequired(false)` — se abilitarlo per richiedere
> autenticazione di sistema recente prima di usare la chiave. Vedi
> [06-punti-aperti.md](06-punti-aperti.md).

---

## `SecurePrefsManager`

**Ruolo**: due responsabilità distinte, entrambe appoggiate su `EncryptedSharedPreferences`
(libreria Jetpack Security — un file XML su disco, cifrato da una `MasterKey` che a
sua volta vive nel Keystore).

### 1. Gestione PIN

- `savePin(pin)` / `isPinCorrect(pin)` / `hasPin()`: hash SHA-256 con salt casuale a 16
  byte. Il PIN **non è mai salvato né in chiaro né cifrato reversibilmente** — solo
  l'hash, confrontato ad ogni login.
- `setBiometricEnabled` / `isBiometricEnabled`: flag per abilitare lo sblocco biometrico.

### 2. Chiave dedicata per SQLCipher

`getOrCreateDatabaseKey(CryptoManager cryptoManager)`:

1. Se una chiave cifrata esiste già in `EncryptedSharedPreferences` → la decifra con
   `cryptoManager.decrypt(...)` e la ritorna.
2. Altrimenti → genera 32 byte casuali nuovi, li cifra con `cryptoManager.encrypt(...)`,
   li salva cifrati, ritorna i byte grezzi.

Nota il parametro: **il metodo vive su `SecurePrefsManager`** (perché è lì che la
chiave cifrata è letta/scritta su disco), ma **riceve** `CryptoManager` perché ha
bisogno di lui per decifrare/cifrare quei byte. Non sono due chiamate — è una sola,
con una dipendenza passata esplicitamente (dependency injection manuale).

**Errori**: il costruttore lancia `SecurePrefsException` se l'inizializzazione di
`EncryptedSharedPreferences` fallisce, invece di lasciare il campo `null` e
crashare al primo uso con `NullPointerException`.

---

## Come si collegano tra loro

```
SecurePrefsManager
    usa  ──────────────►  CryptoManager
    (per cifrare/decifrare la chiave del DB, NON il PIN — il PIN resta solo hash)

CryptoManager
    usa  ──────────────►  Android Keystore
    (la chiave AES vive solo lì, mai su disco in chiaro, mai esportabile)
```

`CryptoManager` non richiede `Context` nel costruttore (parla solo con `KeyStore`/
`Cipher`, API Java pure). `SecurePrefsManager` invece richiede `Context` perché deve
creare/leggere un file XML nella cartella privata dell'app — solo Android sa dov'è
quel path.

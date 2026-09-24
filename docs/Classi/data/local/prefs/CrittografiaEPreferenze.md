# Crittografia, preferenze e chiavi

## In breve

Le due classi hanno compiti diversi e usano chiavi diverse:

| Componente           | Protegge                                          | Chiave principale                                                     | Dove viene usata                                   |
| -------------------- | ------------------------------------------------- | --------------------------------------------------------------------- | -------------------------------------------------- |
| `CryptoManager`      | Contenuto delle note, media e chiave del database | Chiave AES con alias `SecureNotesKey`, conservata in Android Keystore | File in `Media/` e chiave SQLCipher                |
| `SecurePrefsManager` | Piccoli dati riservati dell'app                   | `MasterKey` di AndroidX, usata da `EncryptedSharedPreferences`        | PIN, salt, flag biometrico e chiave DB già cifrata |
|                      |                                                   |                                                                       |                                                    |

Non sono quindi duplicati: uno cifra i dati dell'utente, l'altro gestisce lo
storage sicuro delle preferenze e del materiale necessario ad aprire i database.

## CryptoManager: la cifratura dei contenuti

[CryptoManager](../../../security/CryptoManager.md) crea o recupera la propria chiave AES dal
Keystore Android. Tale chiave non viene letta né salvata come byte nel file system
dell'app. Per ogni cifratura AES/GCM genera un IV casuale di 12 byte, lo antepone
al risultato e l'autenticazione GCM rileva modifiche al contenuto.

Lo usano i repository:

- `NoteRepository` cifra il testo della nota prima di scriverlo in `Media/Notes`;
- `FileRepository` cifra foto, PDF e video prima di scriverli in `Media/`;
- per i video, `decryptStream` decifra progressivamente senza caricare tutto il
  contenuto in memoria.

## SecurePrefsManager: preferenze e segreti piccoli

[SecurePrefsManager](SecurePrefsManager.md) crea `EncryptedSharedPreferences`. AndroidX usa
un suo `MasterKey` e cifra sia le chiavi delle preferenze sia i loro valori. Qui
vengono memorizzati:

- hash SHA-256 del PIN e salt casuale;
- flag che indica se la biometria è abilitata;
- la chiave casuale a 32 byte impiegata da SQLCipher per gli indici Room.

Il PIN non viene cifrato né conservato: viene calcolato un hash con salt e viene
confrontato al login. Non è la chiave usata per cifrare note o media.

## La chiave dei database: due protezioni diverse

Al primo sblocco `getOrCreateDatabaseKey` genera una chiave casuale di 32 byte.
Questa è la chiave che SQLCipher usa per cifrare `notes_index.db` e
`files_index.db`. Prima di salvarla nelle preferenze, `SecurePrefsManager` la
passa a `CryptoManager`, che la cifra con `SecureNotesKey`; il risultato Base64
viene poi salvato nelle `EncryptedSharedPreferences`.

Il percorso è quindi:

```text
chiave SQLCipher casuale
  -> cifrata da CryptoManager (SecureNotesKey)
  -> salvata come valore da EncryptedSharedPreferences (MasterKey AndroidX)
  -> usata da SecureSession per aprire i due database SQLCipher
```

Sono livelli con responsabilità distinte: SQLCipher protegge i database,
`CryptoManager` protegge i contenuti e avvolge la chiave DB, mentre
`EncryptedSharedPreferences` protegge lo storage delle preferenze.

## Rapporto con PIN e biometria

`LoginActivity` verifica il PIN o la biometria prima di chiamare
`SecureSession.unlock`. Nel codice attuale, però, `CryptoManager` crea la sua
chiave Keystore con `setUserAuthenticationRequired(false)`: PIN e biometria sono
un blocco logico dell'app e non una richiesta crittografica del Keystore per ogni
uso della chiave. Questa distinzione è importante se in futuro si desidera
legare materialmente l'uso della chiave a un'autenticazione utente.

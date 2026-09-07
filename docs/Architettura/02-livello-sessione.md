# Livello sessione

Classi: `SecureSession`, `LockManager` (+ `LockListener`), `BaseActivity`,
`SecureNotesApplication`
Package: `com.cookie.securenotes.session`, `com.cookie.securenotes.ui.common`,
`com.cookie.securenotes`

Questo è il livello che tiene insieme tutto quello che deve **sopravvivere tra
un'Activity e l'altra** per tutta la durata di una sessione sbloccata.

---

## `SecureSession` — singleton, "cosa" resta aperto

**Ruolo**: contenitore di tutto ciò che serve mentre l'app è sbloccata: le due
connessioni Room/SQLCipher, i path di storage, il riferimento a `CryptoManager`.

- `init(CryptoManager)`: crea l'istanza la prima volta. Chiamato **solo** da
  `LoginActivity`, l'unico punto che possiede un `CryptoManager` appena creato.
- `getInstance()`: ritorna l'istanza già creata. Lancia `IllegalStateException` se
  chiamato prima di `init()` — segnale immediato di bug invece di un
  `NullPointerException` misterioso più avanti.
- `unlock(Context, SecurePrefsManager)`: recupera la chiave DB (via
  `SecurePrefsManager.getOrCreateDatabaseKey`), apre `NotesDatabase` e `FilesDatabase`,
  crea `StoragePaths` (che garantisce l'esistenza delle cartelle `Media/...`).
- `lock()`: chiude entrambe le connessioni DB (`RoomDatabase.close()`, ereditato,
  non serve reimplementarlo) e azzera i riferimenti.
- `isUnlocked()`: usato da `BaseActivity` per capire se una schermata deve rimandare
  l'utente al login.

## `LockManager` — singleton, "quando" si blocca

**Ruolo**: misura il tempo, non sa nulla di `SecureSession` o della UI (disaccoppiato
di proposito). Due meccanismi complementari, come da specifica:

1. **Background prolungato**: `notifyAppBackgrounded()` (da `onStop`) salva un
   timestamp; `notifyAppForegrounded()` (da `onResume`) confronta subito quanto tempo
   è passato e blocca se supera il timeout.
2. **Inattività continua**: `notifyInteraction()` (da ogni tocco) aggiorna
   `lastInteractionTime`; un `Handler` si ripianifica da solo ogni ~30s
   (`periodicCheck`) e confronta.

- `setTimeoutMinutes(int)`: forza sempre il tetto massimo di 30 minuti
  (`MAX_TIMEOUT_MILLIS`), qualunque valore l'utente provi a impostare.
- `setLockListener(LockListener)`: **il punto di aggancio verso il resto dell'app** —
  `LockManager` non chiude nulla da solo, avvisa chi si è registrato.
- `start()` / `stop()`: da chiamare rispettivamente subito dopo
  `SecureSession.unlock()` e quando la sessione si chiude.

### Perché `LockListener` è un'interfaccia annidata

Vive dentro `LockManager` perché ha senso solo in relazione a lui — nessun'altra parte
dell'app userebbe mai "LockListener" slegato dal manager che lo dichiara. Annidarla
comunica questo legame nella struttura del codice.

## `BaseActivity` — il sensore diffuso

**Ruolo**: classe da cui estendono tutte le Activity (tranne `LoginActivity`), per non
ripetere la stessa logica di tracciamento in ognuna:

- `dispatchTouchEvent(...)`: intercetta ogni tocco su schermo → `LockManager.notifyInteraction()`.
- `onResume()`: → `LockManager.notifyAppForegrounded()`; se `SecureSession.isUnlocked()`
  è `false` (il lock è scattato mentre l'app era in background), rimanda al login.
- `onStop()`: → `LockManager.notifyAppBackgrounded()`.

## `SecureNotesApplication` — il collante, un solo punto di avvio

**Ruolo**: classe `Application` custom, registrata nel Manifest
(`android:name=".SecureNotesApplication"`). All'avvio dell'app, una volta sola,
registra cosa deve succedere quando `LockManager` segnala il timeout:

```java
LockManager.getInstance().setLockListener(() -> {
    SecureSession.getInstance().lock();
    // naviga a LoginActivity
});
```

È l'**unico** punto che collega esplicitamente `LockManager` (misura il tempo) a
`SecureSession` (chiude le risorse) — nessuno dei due sa dell'altro direttamente.

---

## Come si collegano tra loro

```
LoginActivity
    │  init(cryptoManager) + unlock(...)
    ▼
SecureSession ◄──────────────┐
    │                        │ lock()
    │  start()               │
    ▼                        │
LockManager ──── onLockTriggered() ──► SecureNotesApplication (listener)
    ▲
    │  notifyInteraction() / notifyAppForegrounded() / notifyAppBackgrounded()
    │
BaseActivity (estesa da tutte le Activity tranne Login)
```

Nessuna delle quattro classi conosce i dettagli interni delle altre — si parlano solo
tramite i metodi pubblici mostrati sopra. Vedi
[05-flussi-sequenze.md](05-flussi-sequenze.md) per la sequenza temporale completa.

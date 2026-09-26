# `BackupCryptoException`

Package: `com.cookie.securenotes.security`
File: `BackupCryptoException.java`

## Ruolo

Eccezione custom, checked (estende `Exception`, non `RuntimeException`) — costringe
chi chiama `BackupCrypto.encryptStream`/`decryptStream` a gestirla esplicitamente,
stesso principio già usato da `CryptoException` per `CryptoManager`.

## Perché è separata da `CryptoException`

Comunica un problema di natura diversa: non "il Keystore del device ha un problema"
(caso di `CryptoException`), ma "la password di backup inserita dall'utente è
sbagliata, oppure il file `.secnotes` è corrotto/manomesso". Tenerla distinta
permette a `BackupViewModel` di intercettarla con un `catch` dedicato e mostrare un
messaggio specifico ("password errata o file non valido") invece di un errore
tecnico generico.

## Struttura

```java
public class BackupCryptoException extends Exception {
    public BackupCryptoException(String message, Throwable cause) { ... }
}
```

Nessun metodo oltre al costruttore — è solo un contenitore di messaggio + causa
(la causa tipica è `javax.crypto.AEADBadTagException`, quando il tag GCM non torna).

## Chi la lancia / chi la intercetta

- Lanciata da: `BackupCrypto.deriveKey`, `BackupCrypto.encryptStream`,
  `BackupCrypto.decryptStream`.
- Propagata (dichiarata nel `throws`) da: `ExporterManager.exportMedia`,
  `ExporterManager.importMedia`.
- Intercettata esplicitamente da: `BackupViewModel.avviaImport` (per distinguere
  "password sbagliata" da un errore generico). In export non viene quasi mai
  intercettata a parte — un fallimento lì è comunque anomalo, dato che la password è
  quella appena scelta dall'utente nello stesso flusso.

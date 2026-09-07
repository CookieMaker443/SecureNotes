# Flussi e sequenze

Sequenze temporali complete, per capire "chi chiama chi" nell'ordine giusto. I
diagrammi sono in Mermaid — Obsidian li renderizza nativamente aprendo il file.

---

## 1. Login → sessione sbloccata → dashboard

```mermaid
sequenceDiagram
    participant U as Utente
    participant L as LoginActivity
    participant SP as SecurePrefsManager
    participant CM as CryptoManager
    participant SS as SecureSession
    participant LM as LockManager

    U->>L: inserisce PIN
    L->>SP: isPinCorrect(pin)
    SP-->>L: true
    L->>CM: new CryptoManager()
    L->>SS: init(cryptoManager)
    L->>SS: unlock(context, prefsManager)
    SS->>SP: getOrCreateDatabaseKey(cryptoManager)
    SP->>CM: decrypt(chiaveCifrata)
    CM-->>SP: dbKey (byte[])
    SP-->>SS: dbKey
    SS->>SS: NotesDatabase.open(context, dbKey)
    SS->>SS: FilesDatabase.open(context, dbKey)
    SS->>SS: new StoragePaths(context)
    L->>LM: start()
    L->>U: naviga a DashboardActivity
```

---

## 2. Salvataggio di una nota (identico, concettualmente, per un file)

```mermaid
sequenceDiagram
    participant VM as ViewModel
    participant NR as NoteRepository
    participant CM as CryptoManager
    participant FS as Filesystem (Media/Notes/)
    participant DAO as NoteDao

    VM->>NR: saveNote(titolo, testo)
    NR->>NR: genera UUID come nomeFisico
    NR->>CM: encryptToString(testo)
    CM-->>NR: contenutoCifrato (Base64)
    NR->>FS: scrive contenutoCifrato in Media/Notes/{uuid}
    NR->>DAO: insert(Nota{titolo, nomeFisico, date})
    Note over NR,DAO: Il file fisico viene scritto PRIMA<br/>dell'insert: se fallisce, nessuna riga fantasma
```

---

## 3. Caricamento di una nota

```mermaid
sequenceDiagram
    participant VM as ViewModel
    participant NR as NoteRepository
    participant DAO as NoteDao
    participant FS as Filesystem
    participant CM as CryptoManager

    VM->>NR: loadNote(id)
    NR->>DAO: getById(id)
    DAO-->>NR: Nota{nomeFisico, ...}
    NR->>FS: legge Media/Notes/{nomeFisico}
    FS-->>NR: contenutoCifrato
    NR->>CM: decryptFromString(contenutoCifrato)
    CM-->>NR: testo in chiaro
    NR-->>VM: testo
```

---

## 4. Eliminazione (file + riga indice insieme)

```mermaid
sequenceDiagram
    participant VM as ViewModel
    participant NR as NoteRepository
    participant DAO as NoteDao
    participant FS as Filesystem

    VM->>NR: deleteNote(id)
    NR->>DAO: getById(id)
    DAO-->>NR: Nota{nomeFisico}
    NR->>FS: elimina Media/Notes/{nomeFisico}
    NR->>DAO: delete(nota)
```

---

## 5. Timeout / lock automatico

```mermaid
sequenceDiagram
    participant BA as BaseActivity (ogni Activity)
    participant LM as LockManager
    participant APP as SecureNotesApplication
    participant SS as SecureSession
    participant L as LoginActivity

    loop ogni tocco schermo
        BA->>LM: notifyInteraction()
    end
    loop ogni ~30s (Handler)
        LM->>LM: elapsed = now - lastInteractionTime
    end
    Note over LM: elapsed >= timeoutMillis
    LM->>APP: onLockTriggered() [via LockListener]
    APP->>SS: lock()
    SS->>SS: chiude NotesDatabase, FilesDatabase
    APP->>L: naviga a LoginActivity (clear task)
```

**Meccanismo parallelo — background prolungato:**

```mermaid
sequenceDiagram
    participant BA as BaseActivity
    participant LM as LockManager

    BA->>LM: notifyAppBackgrounded() [onStop]
    Note over BA,LM: utente lascia l'app...
    BA->>LM: notifyAppForegrounded() [onResume]
    LM->>LM: elapsed = now - backgroundedAt
    alt elapsed >= timeout
        LM->>LM: triggerLock()
    else rientro in tempo
        LM->>LM: notifyInteraction() (conta come interazione valida)
    end
```

---

## Nota di lettura

Ogni classe in questi diagrammi conosce solo i metodi pubblici di chi chiama —
nessuna sa come è fatta l'implementazione interna delle altre. Se un flusso non
torna, il punto di partenza per il debug è sempre: "quale metodo pubblico è stato
chiamato, e cosa doveva restituire?".

# Punti aperti

Decisioni ancora da prendere, raccolte durante lo sviluppo. Aggiornare questo file
man mano che si chiudono o se ne aprono di nuovi.

## Dal documento di progetto originale

- [ ] **`setUserAuthenticationRequired(true)`** sulla chiave Keystore in
  `CryptoManager.generateKey()` — richiederebbe un'autenticazione di sistema recente
  (PIN/biometria del dispositivo, non della vostra app) prima di poter usare la
  chiave. Aumenta la sicurezza ma introduce vincoli sull'uso in background.
- [ ] **Dettaglio UI esatto** della modalità selezione per l'eliminazione multipla
  delle note (checkbox sulla lista).
- [ ] **Requisiti della password di backup** (lunghezza minima, conferma doppia in UI).
- [ ] **Root detection / tamper detection**: implementarla ora o rimandarla a
  "lavori futuri".

## Emersi durante l'implementazione

- [ ] **Robustezza dell'hash del PIN**: attualmente SHA-256 + salt, un singolo giro.
  Un PIN numerico corto (4-6 cifre) ha poca entropia — se qualcuno riesce a leggere
  il file (es. dispositivo rootato), un bruteforce offline è comunque rapido anche
  con salt, perché lo spazio delle combinazioni resta piccolo. Da valutare: PBKDF2
  con alcune migliaia di iterazioni, costo trascurabile in UX ma alza il costo di un
  attacco offline.
- [ ] **`SupportFactory` e la chiave in memoria**: la versione usata
  (`new SupportFactory(dbKey)`) tiene la chiave in un `byte[]` Java normale, che il
  garbage collector può spostare in memoria senza azzerarlo esplicitamente. Esiste
  una variante di SQLCipher che accetta un `char[]` e lo pulisce dopo l'uso — utile
  per hardening, non blocca lo sviluppo attuale.
- [ ] **Caricamento file grandi in RAM**: `FileRepository.loadFile()` carica l'intero
  file con `Files.readAllBytes()`. Va bene per foto/PDF; per video lunghi rischia
  problemi di memoria. Servirebbe un approccio a stream (decifrare a blocchi mentre
  il player legge) quando si costruirà la schermata di riproduzione.
- [ ] **Naming delle colonne Room**: colonne del DB in inglese
  (`file_name`, `creation_date`...) ma campi Java e commenti in italiano
  (`nomeOriginale`, `titolo`...). Non è un problema tecnico (Room mappa comunque
  correttamente), ma vale la pena scegliere una convenzione unica se il progetto
  cresce.

## Decisioni già chiuse (per riferimento)

- ✅ Le note sono trattate come i file media: un file fisico per nota in
  `Media/Notes/`, indice Room separato — non testo dentro la riga del DB.
- ✅ `SecureSession` e `LockManager` sono singleton con pattern `init()`/`getInstance()`
  separati, per evitare di dover passare un `CryptoManager` ovunque serva solo
  accedere alla sessione già esistente.
- ✅ Threading dei repository verso la UI: `ExecutorService` manuale (non
  RxJava/coroutine), coerente con un progetto in Java puro senza dipendenze
  reattive già presenti.

# `BackupManifest`

Package: `com.cookie.securenotes.manager.backup`
File: `BackupManifest.java`
Classi annidate: `BackupManifest.NoteEntry`, `BackupManifest.FileEntryMeta`

## Ruolo

Modello dati di `manifest.json`, il file che dentro un backup `.secnotes` fa da
indice di cosa c'è: titoli, nomi originali, date. Serve perché quei metadati vivono
**solo** nel DB cifrato (SQLCipher) — i file grezzi decifrati che finiscono nello zip
sono identificati solo da un nome di entry arbitrario (`n_0`, `f_1`, ecc.), senza
manifest sarebbero foto senza nome e note senza titolo.

## Perché non Gson/Moshi

Serializzazione fatta a mano con `org.json` (`JSONObject`/`JSONArray`) — già incluso
nell'SDK Android, zero dipendenze nuove da aggiungere per una struttura dati così
semplice.

## Struttura

```java
public class BackupManifest {
    int schemaVersion;      // versione dello schema — vedi sotto
    String app;             // "SecureNotes", identifica il formato
    String exportDate;      // ISO-8601 (java.time.Instant.toString())
    List<NoteEntry> notes;
    List<FileEntryMeta> files;
}

public static class NoteEntry {
    String entryFile;       // nome della entry dentro lo zip (es. "notes/n_0")
    String titolo;
    long dataCreazione, dataModifica;
}

public static class FileEntryMeta {
    String entryFile;
    String tipo;            // "foto" | "video" | "pdf"
    String nomeOriginale;
    long dimensioneByte;
    long dataCreazione;
}
```

## Metodi principali

| Metodo | Cosa fa |
| --- | --- |
| `static nuovo()` | Crea un manifest vuoto con `schemaVersion` e `exportDate` correnti — usato all'inizio di ogni export |
| `isSchemaSupportata()` | `true` se `schemaVersion` corrisponde a quella che il codice sa leggere — è il "controllo di validità" del backup in import |
| `toJson()` | Serializza in una stringa JSON, scritta come entry `manifest.json` nello zip |
| `static fromJson(String)` | Ricostruisce il manifest letto dallo zip in import |

## Perché `schemaVersion` conta più di ogni altra cosa

È il primo controllo fatto in `ExporterManager.importMedia`: se non corrisponde alla
versione che il codice attuale sa leggere, l'import si rifiuta **subito**, prima di
toccare qualunque nota/file — evita di importare a metà un formato che non capisce
davvero, con conseguenze imprevedibili.

## Chi la usa

- `ExporterManager` la costruisce (export) e la legge (import) — nessun'altra classe
  la tocca direttamente.

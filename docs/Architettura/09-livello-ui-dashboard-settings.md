# Livello UI — Dashboard e Impostazioni

Classi: `DashboardActivity`, `DashboardViewModel`, `AppSettings`, `SettingsActivity`
Package: `com.cookie.securenotes.ui.dashboard`, `com.cookie.securenotes.ui.settings`,
`com.cookie.securenotes.data.local.prefs`

---

## `AppSettings` — preferenze non sensibili, separate da `SecurePrefsManager`

**Perché una classe a parte**: `SecurePrefsManager` gestisce solo dati che hanno
bisogno di cifratura (PIN hash, chiave DB). Il numero di note recenti da mostrare in
dashboard e i minuti di timeout **non sono segreti** — usarli con
`EncryptedSharedPreferences` sarebbe overhead inutile. `AppSettings` usa normali
`SharedPreferences`.

| Metodo | Default | Note |
| --- | --- | --- |
| `getRecentNotesCount()` / `setRecentNotesCount(int)` | 5 | usato da `DashboardViewModel` |
| `getTimeoutMinutes()` / `setTimeoutMinutes(int)` | 3 | persiste ciò che `LockManager` tiene solo in RAM |

**Punto di collegamento importante**: `LockManager.setTimeoutMinutes()` cambia il
valore solo in memoria — si perde al riavvio del processo. `AppSettings` lo rende
persistente; `LoginActivity`, dopo `LockManager.start()`, deve ripristinarlo:

```java
LockManager.getInstance().start();
LockManager.getInstance().setTimeoutMinutes(new AppSettings(this).getTimeoutMinutes());
```

---

## `DashboardViewModel`

Stesso pattern di `NoteViewModel`/`FileViewModel`: costruttore prende
`SecureSession.getInstance()`, usa `AppExecutors` per non bloccare l'UI thread.

Un solo scopo: `loadRecentNotes(count)` → chiama
`NoteRepository.getRecentNotes(count)` (query Room con `ORDER BY data_modifica DESC
LIMIT :count`, applicato lato SQLite — non taglia una lista già caricata in Java) →
popola `recentNotes` (`LiveData<List<Nota>>`).

## `DashboardActivity`

Estende `BaseActivity` (**non** `AppCompatActivity` — fix di sicurezza: senza
`BaseActivity`, questa schermata non traccerebbe tocchi né verificherebbe il timeout
al resume, lasciando potenzialmente dati "sbloccati" visibili a sessione scaduta).

Contiene:
- Tasto **Note** → `startActivity(NotesListActivity)`.
- Tasto **Archivio** → `startActivity(ArchivioActivity)`.
- `ImageButton` **Impostazioni** → `startActivity(SettingsActivity)`.
- `RecyclerView` con **anteprima note recenti**, riusa `NoteAdapter` (nessuna nuova
  classe adapter) — tap apre `NoteEditorActivity` in modifica; long-press
  intenzionalmente senza azione (l'eliminazione resta una funzione della lista
  completa, non dell'anteprima).

`onResume()` chiama `viewModel.loadRecentNotes(appSettings.getRecentNotesCount())` —
non `onCreate()` — così l'anteprima si aggiorna sia al primo ingresso sia tornando da
un'altra schermata (nuova nota salvata, X cambiata nelle Impostazioni).

---

## `SettingsActivity`

Estende `BaseActivity` (stesso fix di sicurezza di `DashboardActivity`). Gestisce
quattro sezioni:

### 1. Timeout inattività
Campo numerico, clampato lato codice tra `MIN_TIMEOUT_MINUTES=3` e
`MAX_TIMEOUT_MINUTES=30` (mai oltre, come da requisito). Al salvataggio, aggiorna sia
`AppSettings` (persistenza) sia `LockManager.getInstance()` (sessione già in corso,
effetto immediato senza dover riavviare l'app).

### 2. Numero note recenti (dashboard)
Campo numerico → `AppSettings.setRecentNotesCount()`.

### 3. Cambio PIN — via biometria
Flusso in due passi, per evitare che chiunque apra le Impostazioni possa cambiare il
PIN senza autenticarsi:

```
1. Tap "Verifica identità" -> BiometricPrompt di sistema
2. Successo -> newPinSection diventa visibile (era GONE) + flag biometricVerified=true
3. Solo con biometricVerified=true, il salvataggio accetta un nuovo PIN
   -> SecurePrefsManager.savePin(nuovoPin)
```

**Nota di design esplicita**: non viene richiesto il vecchio PIN — la sola biometria
del dispositivo autorizza il cambio. Stesso livello di fiducia già usato per lo
sblocco dell'app.

### 4. Backup
`btnExportBackup` presente ma `setEnabled(false)` — placeholder con Toast
"funzione non ancora disponibile". Implementazione rimandata (vedi
[06-punti-aperti.md](06-punti-aperti.md)).

---

## Layout coinvolti

| File | Usato da |
| --- | --- |
| `activity_dashboard.xml` | `DashboardActivity` — 3 tasti + `RecyclerView` anteprima |
| `activity_settings.xml` | `SettingsActivity` — 4 sezioni in `ScrollView` |

---

## Come si collegano tra loro

```
LoginActivity
    │ dopo unlock()
    ▼
DashboardActivity ──tasto Note────────► NotesListActivity
    │              ──tasto Archivio────► ArchivioActivity
    │              ──ImageButton────────► SettingsActivity
    │
    ├── DashboardViewModel ──► NoteRepository.getRecentNotes(count)
    │        ▲
    │        └── AppSettings.getRecentNotesCount()
    │
    └── NoteAdapter (riusato da ui.notes) ──tap──► NoteEditorActivity

SettingsActivity
    ├── AppSettings (timeout minuti, recent notes count — persistenza)
    ├── LockManager.getInstance() (effetto immediato sulla sessione corrente)
    └── SecurePrefsManager (cambio PIN, dietro verifica BiometricPrompt)
```

---

## Stato: cosa è completo, cosa resta aperto

✅ Navigazione dai 3 tasti, anteprima note recenti aggiornata al resume, timeout
configurabile con clamp 3–30 min ed effetto immediato, cambio PIN protetto da
biometria.

⬜ Non ancora implementato:
- **Export backup criptato** — solo placeholder disabilitato.
- **`allowBackup="true"` nel Manifest** — da decidere se disattivare o escludere
  selettivamente le cartelle sensibili (vedi [06-punti-aperti.md](06-punti-aperti.md)).

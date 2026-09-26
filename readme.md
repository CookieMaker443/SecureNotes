# SecureNotes

Applicazione Android per conservare **note personali** e **file sensibili** sul dispositivo. I contenuti restano locali, sono protetti da cifratura e l'accesso all'app richiede un PIN oppure l'autenticazione biometrica disponibile sul dispositivo.

> Il progetto non usa servizi cloud: note, media e indici risiedono esclusivamente nello spazio privato dell'app.

## Funzionalità

- Creazione, modifica, ricerca ed eliminazione di note.
- Archivio di foto, video e documenti PDF, con ricerca e visualizzatori dedicati.
- Accesso tramite PIN e supporto biometrico.
- Blocco automatico dopo un periodo di inattività configurabile.
- Dashboard con le note recenti.
- Backup e ripristino in un file `.secnotes` cifrato con una password dedicata.
- Impostazioni per PIN, timeout e numero di note recenti mostrate in dashboard.

## Sicurezza e dati

I dati applicativi sono conservati nella memoria privata di Android. Le note e i file multimediali sono cifrati con AES-GCM tramite una chiave custodita nell'Android Keystore; gli indici locali sono database Room cifrati con SQLCipher. Le preferenze sensibili usano `EncryptedSharedPreferences`.

I backup sono indipendenti dalla chiave del dispositivo: vengono cifrati con AES-GCM usando una chiave derivata dalla password di backup tramite PBKDF2. Durante l'importazione, i contenuti vengono nuovamente cifrati con la chiave locale del dispositivo di destinazione.

## Tecnologie

| Area | Scelta |
| --- | --- |
| Linguaggio | Java 11 |
| Interfaccia | Android Views, Material Components |
| Architettura | MVVM + Repository + LiveData |
| Database | Room + SQLCipher |
| Crittografia | Android Keystore, AES-GCM, Jetpack Security |
| Autenticazione | PIN e AndroidX Biometric |
| Media | Media3 / ExoPlayer per i video |
| Backup | Storage Access Framework, ZIP, AES-GCM e PBKDF2 |

## Requisiti

- Android Studio aggiornato
- JDK 11
- Android SDK 35
- Dispositivo o emulatore con Android 8.0 (API 26) o successivo

## Avvio rapido

```bash
git clone <url-del-repository>
cd SecureNotes
./gradlew assembleDebug
```

L'APK debug viene generato in `app/build/outputs/apk/debug/`.

Per eseguire l'app da Android Studio, apri la cartella del progetto, attendi la sincronizzazione Gradle e avvia il modulo `app` su un emulatore o un dispositivo.

## Verifica

```bash
# Test unitari JVM
./gradlew test

# Test strumentali: richiedono un emulatore o dispositivo connesso
./gradlew connectedAndroidTest
```

## Struttura del progetto

```text
app/src/main/
├── java/com/cookie/securenotes/
│   ├── ui/                  # Activity, Fragment, adapter e ViewModel
│   ├── data/local/          # Room, preferenze sicure e percorsi di storage
│   ├── security/            # Crittografia dei contenuti e dei backup
│   ├── session/             # Sessione, PIN e blocco automatico
│   ├── manager/repository/  # Accesso a note e file
│   └── manager/backup/      # Esportazione e importazione dei backup
└── res/                     # Layout, stringhe, temi e risorse grafiche
```

## Documentazione

La documentazione tecnica è disponibile nella cartella [`docs`](docs/Architettura/00-panoramica.md): descrive architettura, flussi di sicurezza, persistenza, UI e backup. I file `.canvas` e le note Markdown sono pensati per essere consultati anche con [Obsidian](https://obsidian.md/).

## Stato del progetto

SecureNotes è un progetto Android in sviluppo. Prima di affidargli dati importanti, verifica il comportamento sul tuo dispositivo e conserva copie di backup in un luogo sicuro.

## Licenza

Al momento non è presente una licenza nel repository. Non assumere permessi di riuso o distribuzione finché non ne viene aggiunta una esplicita.

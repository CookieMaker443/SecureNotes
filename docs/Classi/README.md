# Classi di SecureNotes

Questa cartella replica la struttura dei package in `app/src/main/java/com/cookie/securenotes`.
Ogni pagina descrive responsabilità, metodi e dipendenze dirette della classe.

Punti di ingresso: [SecureNotesApplication](SecureNotesApplication.md) registra il blocco globale;
[LoginActivity](ui/login/LoginActivity.md) apre la sessione; [DashboardActivity](ui/dashboard/DashboardActivity.md)
porta a note, archivio e impostazioni. Il nucleo protetto è
[SecureSession](session/SecureSession.md), che fornisce database, chiave e percorsi ai repository.

- [Dati locali](data/local/)
- [Repository](manager/repository/) · [backup](manager/backup/)
- [Sicurezza e sessione](security/) · [sessione](session/)
- [Interfaccia](ui/) · [Utilità](util/)

# SecureNotesApplication

## Ruolo

È la classe `Application` avviata prima delle Activity. Installa il comportamento globale
da eseguire quando la sessione scade.

## Metodi

- `onCreate()` registra su `LockManager` un `LockListener`. Quando riceve `onLockTriggered`,
  chiama `SecureSession.lock()` (chiude i database e marca la sessione bloccata) e apre
  `LoginActivity` come nuova attività, svuotando lo stack precedente.

## Collegamenti

È il ponte fra [LockManager](session/LockManager.md), che rileva il timeout, e
[SecureSession](session/SecureSession.md)/[LoginActivity](ui/login/LoginActivity.md), che
proteggono e riavviano il flusso di accesso.

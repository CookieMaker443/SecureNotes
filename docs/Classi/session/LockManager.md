# LockManager

## Ruolo

Singleton che controlla l'inattività della sessione, sia durante l'uso sia quando l'app è in
background. Il timeout predefinito è 3 minuti, quello massimo 30.

## Metodi

- `setLockListener` riceve l'azione da eseguire al blocco; viene impostata da
  `SecureNotesApplication`.
- `setTimeoutMinutes` limita il valore al massimo; `getTimeoutMillis` lo espone.
- `start` azzera i tempi e avvia un controllo ogni 30 secondi; `stop` lo annulla.
- `notifyInteraction` aggiorna l'ultima attività. `BaseActivity.dispatchTouchEvent` lo chiama
  per ogni tocco.
- `notifyAppBackgrounded` e `notifyAppForegrounded` misurano l'assenza; se supera il limite,
  `triggerLock` invoca il listener.

## Collegamenti

[LoginActivity](../ui/login/LoginActivity.md) lo avvia dopo l'accesso; tutte le
[BaseActivity](../ui/common/BaseActivity.md) lo alimentano; l'Application esegue il lock reale.

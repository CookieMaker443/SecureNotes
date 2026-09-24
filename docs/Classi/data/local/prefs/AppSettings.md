# AppSettings

## Ruolo

Memorizza preferenze non sensibili in `SharedPreferences`: numero di note recenti e timeout.

## Metodi

- `getRecentNotesCount` / `setRecentNotesCount`: leggono o aggiornano l'anteprima dashboard (default 5).
- `getTimeoutMinutes` / `setTimeoutMinutes`: gestiscono il timeout persistente (default 3).

## Collegamenti

[SettingsActivity](../../../ui/settings/SettingsActivity.md) modifica i valori; [DashboardActivity](../../../ui/dashboard/DashboardActivity.md) usa il primo e [LoginActivity](../../../ui/login/LoginActivity.md) applica il secondo a `LockManager`.

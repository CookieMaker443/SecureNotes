# BaseActivity

## Ruolo e metodi

Base delle schermate protette. dispatchTouchEvent segnala ogni tocco a LockManager. onResume segnala il rientro e, se SecureSession non è sbloccata, chiama goToLogin. onStop registra il background. goToLogin apre LoginActivity, pulisce lo stack e chiude la schermata.

## Collegamenti

Dashboard, note, archivio, impostazioni e viewer la ereditano; alimenta LockManager e impedisce di mostrare dati dopo un lock.

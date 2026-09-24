# Problemi rilevati nel codice

Questa nota descrive comportamenti osservati durante la documentazione.

## Risolto — Modifica di una nota crea una nuova nota

Risolto: l'id dell'editor viene passato al ViewModel. Per una nota esistente, `updateNote` riscrive il suo file, aggiorna titolo e data di modifica e usa `NoteDao.update`; senza id continua a creare una nuova nota.

## Risolto — Titolo non mostrato nell'editor

Risolto: `loadNoteContent` pubblica sia `editorTitle` sia `editorContent` sul thread UI.

## Biometria disponibile anche con flag disabilitato

LoginActivity mostra sempre il pulsante biometrico e il suo listener avvia showBiometricPrompt senza controllare prefsManager.isBiometricEnabled(). Il flag attualmente condiziona soltanto l'avvio automatico del prompt. Se il requisito è disabilitare davvero la biometria, l'accesso manuale la aggira.

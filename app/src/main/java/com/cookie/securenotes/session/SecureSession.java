package com.cookie.securenotes.session;

import android.content.Context;

import com.cookie.securenotes.data.local.db.FilesDatabase;
import com.cookie.securenotes.data.local.db.NotesDatabase;
import com.cookie.securenotes.data.local.prefs.SecurePrefsManager;
import com.cookie.securenotes.security.CryptoException;
import com.cookie.securenotes.security.CryptoManager;

public class SecureSession {

    private static SecureSession instance;

    private final CryptoManager cryptoManager;
    private NotesDatabase notesDatabase;
    private FilesDatabase filesDatabase;
    private boolean unlocked = false;

    private SecureSession(CryptoManager cryptoManager) {
        this.cryptoManager = cryptoManager;
    }

    public static synchronized SecureSession getInstance(CryptoManager cryptoManager) {
        if (instance == null) {
            instance = new SecureSession(cryptoManager);
        }
        return instance;
    }

    /** Da chiamare subito dopo un login riuscito (PIN o biometria). */
    public void unlock(Context appContext, SecurePrefsManager prefsManager) throws CryptoException {
        byte[] dbKey = prefsManager.getOrCreateDatabaseKey(cryptoManager);
        notesDatabase = NotesDatabase.open(appContext, dbKey);
        filesDatabase = FilesDatabase.open(appContext, dbKey);
        unlocked = true;
    }

    /** Da chiamare da LockManager al timeout. */
    public void lock() {
        if (notesDatabase != null) notesDatabase.close();
        if (filesDatabase != null) filesDatabase.close();
        notesDatabase = null;
        filesDatabase = null;
        unlocked = false;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public NotesDatabase getNotesDatabase() {
        return notesDatabase;
    }

    public FilesDatabase getFilesDatabase() {
        return filesDatabase;
    }

    public CryptoManager getCryptoManager() {
        return cryptoManager;
    }
}
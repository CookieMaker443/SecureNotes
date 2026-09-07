package com.cookie.securenotes.session;

import android.content.Context;

import com.cookie.securenotes.data.local.db.FilesDatabase;
import com.cookie.securenotes.data.local.db.NotesDatabase;
import com.cookie.securenotes.data.local.prefs.SecurePrefsManager;
import com.cookie.securenotes.data.local.storage.StoragePaths;
import com.cookie.securenotes.security.CryptoException;
import com.cookie.securenotes.security.CryptoManager;

public class SecureSession {

    private static SecureSession instance;

    private final CryptoManager cryptoManager;
    private NotesDatabase notesDatabase;
    private FilesDatabase filesDatabase;
    private StoragePaths storagePaths;
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
        // prende la chiave dal database, tramite il crypto manager passato come DI
        byte[] dbKey = prefsManager.getOrCreateDatabaseKey(cryptoManager);
        notesDatabase = NotesDatabase.open(appContext, dbKey);
        filesDatabase = FilesDatabase.open(appContext, dbKey);
        storagePaths = new StoragePaths(appContext);
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

    public StoragePaths getStoragePaths() {
        return storagePaths;
    }
}
package com.cookie.securenotes.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.cookie.securenotes.security.CryptoException;
import com.cookie.securenotes.security.CryptoManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class SecurePrefsManager {
    private static final String PREF_NAME = "secure_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_DB_KEY_ENCRYPTED = "db_key_encrypted";
    private static final int DB_KEY_LENGTH_BYTES = 32; // 256 bit, chiave SQLCipher

    private final SharedPreferences sharedPreferences;

    public SecurePrefsManager(Context context) throws SecurePrefsException {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new SecurePrefsException("Impossibile inizializzare le preferenze sicure", e);
        }
    }

    // ---- PIN ----

    public void savePin(String pin) {
        byte[] salt = generateSalt();
        String hash = hashPin(pin, salt);

        sharedPreferences.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .apply();
    }

    public boolean isPinCorrect(String pin) {
        String storedHash = sharedPreferences.getString(KEY_PIN_HASH, null);
        String storedSaltBase64 = sharedPreferences.getString(KEY_PIN_SALT, null);

        if (storedHash == null || storedSaltBase64 == null) {
            return false;
        }

        byte[] salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP);
        String inputHash = hashPin(pin, salt);

        return storedHash.equals(inputHash);
    }

    public boolean hasPin() {
        return sharedPreferences.contains(KEY_PIN_HASH);
    }

    public void setBiometricEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
    }

    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    private String hashPin(String pin, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hash = digest.digest(pin.getBytes());
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 è sempre disponibile su Android: non dovrebbe mai capitare.
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }

    private byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    // ---- Chiave dedicata per il DB Room/SQLCipher ----

    /**
     * Ritorna la chiave per aprire notes_index.db / files_index.db.
     * Se non esiste ancora, la genera, la cifra con CryptoManager
     * (che usa l'alias Keystore) e la salva qui cifrata.
     * La chiave grezza non tocca mai il disco in chiaro.
     */
    public byte[] getOrCreateDatabaseKey(CryptoManager cryptoManager) throws CryptoException {
        String storedEncrypted = sharedPreferences.getString(KEY_DB_KEY_ENCRYPTED, null);

        if (storedEncrypted != null) {
            byte[] encryptedKey = Base64.decode(storedEncrypted, Base64.NO_WRAP);
            return cryptoManager.decrypt(encryptedKey);
        }

        byte[] newKey = new byte[DB_KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(newKey);

        byte[] encryptedKey = cryptoManager.encrypt(newKey);
        String encoded = Base64.encodeToString(encryptedKey, Base64.NO_WRAP);
        sharedPreferences.edit()
                .putString(KEY_DB_KEY_ENCRYPTED, encoded)
                .apply();

        return newKey;
    }
}
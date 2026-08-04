package com.cookie.securenotes.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.GeneralSecurityException;

public class SecurePrefsManager {
    private static final String PREF_NAME = "secure_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";

    private SharedPreferences sharedPreferences;

    public SecurePrefsManager(Context context) {
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
            e.printStackTrace();
        }
    }

    public void savePin(String pin) {
        byte[] salt = generateSalt();
        String hash = hashPin(pin, salt);
        
        sharedPreferences.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.DEFAULT))
                .apply();
    }

    public boolean isPinCorrect(String pin) {
        String storedHash = sharedPreferences.getString(KEY_PIN_HASH, null);
        String storedSaltBase64 = sharedPreferences.getString(KEY_PIN_SALT, null);

        if (storedHash == null || storedSaltBase64 == null) {
            return false;
        }

        byte[] salt = Base64.decode(storedSaltBase64, Base64.DEFAULT);
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
            digest.reset();
            digest.update(salt);
            byte[] hash = digest.digest(pin.getBytes());
            return Base64.encodeToString(hash, Base64.DEFAULT);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }
}
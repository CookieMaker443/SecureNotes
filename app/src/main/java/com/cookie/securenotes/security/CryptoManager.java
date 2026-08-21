package com.cookie.securenotes.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoManager {

    private static final String ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM;
    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_NONE;
    private static final String TRANSFORMATION = ALGORITHM + "/" + BLOCK_MODE + "/" + PADDING;
    private static final String KEY_ALIAS = "SecureNotesKey";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private KeyStore keyStore;

    public CryptoManager() {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKey();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setUserAuthenticationRequired(false) // Può essere true per massima sicurezza
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(spec);
        keyGenerator.generateKey();
    }

    private SecretKey getSecretKey() throws Exception {
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    public String encrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // Concateniamo IV e dati criptati per il salvataggio
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
            
            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String decrypt(String encryptedDataWithIv) {
        try {
            byte[] combined = Base64.decode(encryptedDataWithIv, Base64.DEFAULT);
            
            // IV per GCM è solitamente 12 byte
            byte[] iv = new byte[12];
            byte[] encryptedData = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encryptedData, 0, encryptedData.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);
            
            byte[] decodedData = cipher.doFinal(encryptedData);
            return new String(decodedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
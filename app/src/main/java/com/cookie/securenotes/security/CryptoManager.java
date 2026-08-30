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
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final KeyStore keyStore;

    public CryptoManager() throws CryptoException {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKey();
            }
        } catch (Exception e) {
            throw new CryptoException("Impossibile inizializzare il Keystore", e);
        }
    }

    private void generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setUserAuthenticationRequired(false) // TODO: punto aperto #1, vedi documento
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(spec);
        keyGenerator.generateKey();
    }

    private SecretKey getSecretKey() throws Exception {
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    // ---- Famiglia 1: stringhe (note, metadati) — output Base64 ----

    public String encryptToString(String data) throws CryptoException {
        byte[] combined = encryptRaw(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    public String decryptFromString(String encryptedDataWithIv) throws CryptoException {
        byte[] combined = Base64.decode(encryptedDataWithIv, Base64.NO_WRAP);
        byte[] plain = decryptRaw(combined);
        return new String(plain, StandardCharsets.UTF_8);
    }

    // ---- Famiglia 2: byte[] puri (foto, video, PDF) — niente Base64 ----

    public byte[] encrypt(byte[] data) throws CryptoException {
        return encryptRaw(data);
    }

    public byte[] decrypt(byte[] encryptedDataWithIv) throws CryptoException {
        return decryptRaw(encryptedDataWithIv);
    }

    // ---- Implementazione condivisa ----

    private byte[] encryptRaw(byte[] data) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data);

            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
            return combined;
        } catch (Exception e) {
            throw new CryptoException("Errore durante la cifratura", e);
        }
    }

    private byte[] decryptRaw(byte[] combined) throws CryptoException {
        if (combined.length < GCM_IV_LENGTH) {
            throw new CryptoException("Dati cifrati non validi (troppo corti)", null);
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            // Include il caso di manomissione: GCM lancia AEADBadTagException
            // se i dati o l'IV sono stati alterati.
            throw new CryptoException("Errore durante la decifratura (dati corrotti o manomessi?)", e);
        }
    }
}
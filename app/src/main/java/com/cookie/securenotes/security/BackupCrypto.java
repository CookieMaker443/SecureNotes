package com.cookie.securenotes.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifratura del file di backup (.secnotes) con una password scelta dall'utente.
 *
 * A differenza di CryptoManager, qui NON si usa l'Android Keystore: la chiave deve
 * poter essere ricreata anche su un device diverso da quello che ha fatto l'export
 * (è tutto il senso del backup), quindi viene derivata dalla password stessa tramite
 * PBKDF2, non da una chiave hardware legata a un singolo dispositivo.
 */
public class BackupCrypto {

    private static final int SALT_LENGTH = 16;             // byte, per PBKDF2
    private static final int IV_LENGTH = 12;                // byte, standard per GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 150_000;   // alto apposta: l'operazione è rara (un export/import), non va fatta spesso
    private static final int KEY_LENGTH_BITS = 256;         // AES-256
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int BUFFER_SIZE = 64 * 1024;       // 64KB alla volta, mai il file intero in RAM

    /** Deriva una chiave AES a partire da password + salt: stessa password + stesso salt = sempre la stessa chiave. */
    private SecretKey deriveKey(char[] password, byte[] salt) throws BackupCryptoException {
        try {
            // PBKDF2: applica l'hash migliaia di volte apposta, per rallentare un eventuale bruteforce offline
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new BackupCryptoException("Impossibile derivare la chiave dalla password di backup", e);
        }
    }

    /**
     * Cifra tutto ciò che arriva da zipInChiaro e lo scrive su dest, preceduto da un
     * header in chiaro (salt + IV). Salt e IV non sono segreti: servono solo a
     * rifare lo stesso percorso in decrypt, il segreto resta solo la password.
     */
    public void encryptStream(char[] password, InputStream zipInChiaro, OutputStream dest) throws BackupCryptoException {
        try {
            // salt e IV nuovi ad ogni export, mai riusati tra backup diversi
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();

            // header in chiaro in testa al file, prima dei dati cifrati
            dest.write(salt);
            dest.write(iv);

            // tutto quello che passa per questo stream viene cifrato a blocchi, mai un buffer unico
            try (OutputStream cifrato = new CipherOutputStream(dest, cipher)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int letti;
                while ((letti = zipInChiaro.read(buffer)) != -1) {
                    cifrato.write(buffer, 0, letti);
                }
            }
        } catch (BackupCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new BackupCryptoException("Errore durante la cifratura del backup", e);
        }
    }

    /**
     * Percorso inverso: legge salt+IV dalla testa di sorgenteCifrata, deriva la stessa
     * chiave con la password fornita, decifra il resto su zipDecifrato.
     *
     * Se la password è sbagliata, il tag di autenticazione GCM non torna e viene
     * lanciata BackupCryptoException (causa AEADBadTagException) — non c'è altro modo
     * per "validare" una password in anticipo, si scopre solo provando a decifrare.
     */
    public void decryptStream(char[] password, InputStream sorgenteCifrata, OutputStream zipDecifrato) throws BackupCryptoException {
        try {
            byte[] salt = leggiEsatti(sorgenteCifrata, SALT_LENGTH);
            byte[] iv = leggiEsatti(sorgenteCifrata, IV_LENGTH);

            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            try (InputStream decifrato = new CipherInputStream(sorgenteCifrata, cipher)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int letti;
                while ((letti = decifrato.read(buffer)) != -1) {
                    zipDecifrato.write(buffer, 0, letti);
                }
            }
        } catch (BackupCryptoException e) {
            throw e;
        } catch (Exception e) {
            // qui dentro finisce anche AEADBadTagException: password sbagliata o file corrotto/manomesso
            throw new BackupCryptoException("Password errata o file di backup non valido", e);
        }
    }

    /** Legge esattamente n byte da uno stream, gestendo il caso in cui read() ne restituisca meno alla volta. */
    private byte[] leggiEsatti(InputStream in, int n) throws IOException, BackupCryptoException {
        byte[] buffer = new byte[n];
        int totaleLetti = 0;
        while (totaleLetti < n) {
            int letti = in.read(buffer, totaleLetti, n - totaleLetti);
            if (letti == -1) {
                throw new BackupCryptoException("File di backup troncato: header incompleto", null);
            }
            totaleLetti += letti;
        }
        return buffer;
    }
}

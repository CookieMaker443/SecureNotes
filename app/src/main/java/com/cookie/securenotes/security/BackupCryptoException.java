package com.cookie.securenotes.security;

/**
 * Eccezione custom per tutto ciò che riguarda la cifratura/decifratura del file di
 * backup (.secnotes). Separata da CryptoException perché ha una causa tipica diversa
 * e più specifica da comunicare all'utente: "password di backup errata o file non
 * valido" — non un problema del Keystore del device.
 */
public class BackupCryptoException extends Exception {

    public BackupCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}

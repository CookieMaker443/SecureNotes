package com.cookie.securenotes.ui.viewer;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;

import com.cookie.securenotes.security.CryptoManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Legge un file video cifrato e lo espone a Media3 già decifrato, senza mai scrivere
 * dati in chiaro su disco. Supporta solo lettura sequenziale in avanti (limite di GCM):
 * un seek indietro riapre il file dall'inizio e "scarta" i byte fino al punto richiesto.
 */

// siccome certe api sono considerate instabili, questa annotazione permette di dire tipo "si accetto, lo so e vado avanti"
@UnstableApi
public class EncryptedFileDataSource extends BaseDataSource {

    private final File encryptedFile;
    private final CryptoManager cryptoManager;
    private InputStream decryptedStream;
    private long bytesRemaining;

    public EncryptedFileDataSource(File encryptedFile, CryptoManager cryptoManager) {
        super(/* isNetwork= */ false);
        this.encryptedFile = encryptedFile;
        this.cryptoManager = cryptoManager;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        try {
            InputStream fileIn = new FileInputStream(encryptedFile);
            decryptedStream = cryptoManager.decryptStream(fileIn);

            long toSkip = dataSpec.position;
            while (toSkip > 0) {
                long skipped = decryptedStream.skip(toSkip);
                if (skipped <= 0) break;
                toSkip -= skipped;
            }

            bytesRemaining = dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : C.LENGTH_UNSET;
            return bytesRemaining;
        } catch (Exception e) {
            throw new IOException("Impossibile aprire il video cifrato", e);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        int toRead = bytesRemaining != C.LENGTH_UNSET
                ? (int) Math.min(length, bytesRemaining) : length;
        int read = decryptedStream.read(buffer, offset, toRead);
        if (read == -1) return C.RESULT_END_OF_INPUT;
        if (bytesRemaining != C.LENGTH_UNSET) bytesRemaining -= read;
        return read;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return Uri.fromFile(encryptedFile);
    }

    @Override
    public void close() throws IOException {
        if (decryptedStream != null) {
            decryptedStream.close();
            decryptedStream = null;
        }
    }
}
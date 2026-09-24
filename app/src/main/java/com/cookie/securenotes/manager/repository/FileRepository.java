package com.cookie.securenotes.manager.repository;

import com.cookie.securenotes.data.local.db.FileDao;
import com.cookie.securenotes.data.local.db.FileEntry;
import com.cookie.securenotes.data.local.storage.StoragePaths;
import com.cookie.securenotes.security.CryptoException;
import com.cookie.securenotes.security.CryptoManager;
import com.cookie.securenotes.session.SecureSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

public class FileRepository {

    private final FileDao fileDao;
    private final CryptoManager cryptoManager;
    private final StoragePaths storagePaths;

    public FileRepository(SecureSession session) {
        this.fileDao = session.getFilesDatabase().fileDao();
        this.cryptoManager = session.getCryptoManager();
        this.storagePaths = session.getStoragePaths();
    }

    /** Salva un nuovo file cifrato. tipo: "foto" | "video" | "pdf". */
    public void saveFile(String nomeOriginale, String tipo, byte[] datiInChiaro) throws CryptoException, IOException {
        String nomeFisico = UUID.randomUUID().toString();
        File destinazione = new File(getDirByTipo(tipo), nomeFisico);

        byte[] datiCifrati = cryptoManager.encrypt(datiInChiaro);

        try (FileOutputStream out = new FileOutputStream(destinazione)) {
            out.write(datiCifrati);
        }

        FileEntry entry = new FileEntry();
        entry.nomeOriginale = nomeOriginale;
        entry.nomeFisico = nomeFisico;
        entry.tipo = tipo;
        entry.dimensioneByte = datiInChiaro.length;
        entry.dataCreazione = System.currentTimeMillis();

        fileDao.insert(entry);
    }

    /** Carica e decifra il contenuto di un file esistente. */
    public byte[] loadFile(long fileEntryId) throws CryptoException, IOException {
        FileEntry entry = fileDao.getById(fileEntryId);
        if (entry == null) {
            throw new IOException("File non trovato nell'indice: id=" + fileEntryId);
        }

        File sorgente = new File(getDirByTipo(entry.tipo), entry.nomeFisico);

        byte[] datiCifrati = Files.readAllBytes(sorgente.toPath());

        return cryptoManager.decrypt(datiCifrati);
    }

    // in FileRepository.java
    public void loadFileToStream(long fileEntryId, OutputStream destinazione) throws CryptoException, IOException {
        FileEntry entry = fileDao.getById(fileEntryId);
        if (entry == null) {
            throw new IOException("File non trovato nell'indice: id=" + fileEntryId);
        }
        File sorgente = new File(getDirByTipo(entry.tipo), entry.nomeFisico);

        try (InputStream cifrato = new FileInputStream(sorgente);
             InputStream decifrato = cryptoManager.decryptStream(cifrato)) {
            byte[] buffer = new byte[64 * 1024]; // 64KB alla volta, non tutto insieme
            int letti;
            while ((letti = decifrato.read(buffer)) != -1) {
                destinazione.write(buffer, 0, letti);
            }
        }
    }

    /**
     * usato in import di un backup: prende contenuto già decifrato (viene dallo
     * zip del backup) e lo scrive cifrato con la chiave di QUESTO device, a blocchi
     * (mai un array intero in RAM, stesso motivo di loadFileToStream ma al contrario).
     * Genera sempre un nomeFisico (UUID) nuovo, non riusa mai quello del backup —
     * evita collisioni con file già presenti su questo device. dataCreazione viene
     * dal manifest del backup, non da "adesso": si vuole preservare la cronologia originale.
     */
    public void importFile(String tipo, String nomeOriginale, InputStream contenutoChiaro,
                            long dataCreazione, long dimensioneByte) throws CryptoException, IOException {
        String nomeFisico = UUID.randomUUID().toString();
        File destinazione = new File(getDirByTipo(tipo), nomeFisico);

        try (OutputStream fileOut = new FileOutputStream(destinazione);
             OutputStream cifrato = cryptoManager.encryptStream(fileOut)) {
            byte[] buffer = new byte[64 * 1024];
            int letti;
            while ((letti = contenutoChiaro.read(buffer)) != -1) {
                cifrato.write(buffer, 0, letti);
            }
        }

        FileEntry entry = new FileEntry();
        entry.nomeOriginale = nomeOriginale;
        entry.nomeFisico = nomeFisico;
        entry.tipo = tipo;
        entry.dimensioneByte = dimensioneByte;
        entry.dataCreazione = dataCreazione; // preservata dal backup, non System.currentTimeMillis()

        fileDao.insert(entry);
    }

    /** Cerca file per tipo e nome (parziale). */
    public List<FileEntry> searchFile(String tipo, String query) {
        return fileDao.search(tipo, query);
    }

    public List<FileEntry> getAllByTipo(String tipo) {
        return fileDao.getAllByTipo(tipo);
    }

    /** Elimina un file: sia dal disco che dall'indice. */
    public void deleteFile(long fileEntryId) throws IOException {
        FileEntry entry = fileDao.getById(fileEntryId);
        if (entry == null) {
            return; // già non esiste, niente da fare
        }

        File file = new File(getDirByTipo(entry.tipo), entry.nomeFisico);
        if (file.exists() && !file.delete()) {
            throw new IOException("Impossibile eliminare il file fisico: " + file.getAbsolutePath());
        }

        fileDao.delete(entry);
    }

    private File getDirByTipo(String tipo) {
        switch (tipo) {
            case "foto": return storagePaths.getImmaginiDir();
            case "video": return storagePaths.getVideoDir();
            case "pdf": return storagePaths.getPdfDir();
            default: throw new IllegalArgumentException("Tipo file sconosciuto: " + tipo);
        }
    }

    public FileEntry getById(long fileEntryId) {
        return fileDao.getById(fileEntryId);
    }
}

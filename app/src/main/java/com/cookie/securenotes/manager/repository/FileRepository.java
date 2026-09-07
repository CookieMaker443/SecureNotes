package com.cookie.securenotes.manager.repository;

import com.cookie.securenotes.data.local.db.FileDao;
import com.cookie.securenotes.data.local.db.FileEntry;
import com.cookie.securenotes.data.local.storage.StoragePaths;
import com.cookie.securenotes.security.CryptoException;
import com.cookie.securenotes.security.CryptoManager;
import com.cookie.securenotes.session.SecureSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
}
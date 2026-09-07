package com.cookie.securenotes.manager.repository;

import com.cookie.securenotes.data.local.db.NoteDao;
import com.cookie.securenotes.data.local.db.Nota;
import com.cookie.securenotes.data.local.storage.StoragePaths;
import com.cookie.securenotes.security.CryptoException;
import com.cookie.securenotes.security.CryptoManager;
import com.cookie.securenotes.session.SecureSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

public class NoteRepository {

    private final NoteDao noteDao;
    private final CryptoManager cryptoManager;
    private final StoragePaths storagePaths;

    public NoteRepository(SecureSession session) {
        this.noteDao = session.getNotesDatabase().noteDao();
        this.cryptoManager = session.getCryptoManager();
        this.storagePaths = session.getStoragePaths();
    }

    public void saveNote(String titolo, String contenutoInChiaro) throws CryptoException, IOException {
        String nomeFisico = UUID.randomUUID().toString(); // nome salvato sul disco
        File destinazione = new File(storagePaths.getNotesDir(), nomeFisico); // Media/Notes/

        String contenutoCifrato = cryptoManager.encryptToString(contenutoInChiaro); //cifra il contenito
        Files.write(destinazione.toPath(), contenutoCifrato.getBytes(StandardCharsets.UTF_8)); // salva la nota cifrata

        // crea ed inserisce la nota nell'index
        Nota nota = new Nota();
        nota.titolo = titolo;
        nota.nomeFisico = nomeFisico;
        nota.dataCreazione = System.currentTimeMillis();
        nota.dataModifica = nota.dataCreazione;

        noteDao.insert(nota);
    }

    public String loadNote(long notaId) throws CryptoException, IOException {
        Nota nota = noteDao.getById(notaId);
        if (nota == null) {
            throw new IOException("Nota non trovata nell'indice: id=" + notaId);
        }

        File sorgente = new File(storagePaths.getNotesDir(), nota.nomeFisico);
        String contenutoCifrato = new String(Files.readAllBytes(sorgente.toPath()), StandardCharsets.UTF_8);

        return cryptoManager.decryptFromString(contenutoCifrato);
    }

    public List<Nota> searchNote(String query) {
        return noteDao.search(query);
    }

    public List<Nota> getAllNote() {
        return noteDao.getAll();
    }

    public void deleteNote(long notaId) throws IOException {
        Nota nota = noteDao.getById(notaId);
        if (nota == null) {
            return;
        }

        File file = new File(storagePaths.getNotesDir(), nota.nomeFisico);
        if (file.exists() && !file.delete()) {
            throw new IOException("Impossibile eliminare il file fisico: " + file.getAbsolutePath());
        }

        noteDao.delete(nota);
    }
}
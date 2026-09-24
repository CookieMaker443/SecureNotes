package com.cookie.securenotes.manager.backup;

import android.content.Context;

import com.cookie.securenotes.data.local.db.FileEntry;
import com.cookie.securenotes.data.local.db.Nota;
import com.cookie.securenotes.manager.repository.FileRepository;
import com.cookie.securenotes.manager.repository.NoteRepository;
import com.cookie.securenotes.security.BackupCrypto;
import com.cookie.securenotes.security.BackupCryptoException;
import com.cookie.securenotes.security.CryptoException;
import com.cookie.securenotes.session.SecureSession;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Orchestratore dell'export/import del backup cifrato (.secnotes).
 *
 * Non tocca mai CryptoManager o i DAO direttamente: passa sempre da NoteRepository/
 * FileRepository, che restano gli unici proprietari della tripla cifratura+DB+
 * filesystem — stessa regola già in vigore nel resto dell'app (vedi doc 04).
 */
public class ExporterManager {

    // prefisso dei file temporanei in chiaro nella cache, per la pulizia di sicurezza in SecureSession.unlock()
    private static final String TEMP_PREFIX = "secnotes_tmp_";

    private final NoteRepository noteRepository;
    private final FileRepository fileRepository;
    private final Context appContext;
    private final BackupCrypto backupCrypto = new BackupCrypto();

    // executor dedicato: un export/import non deve mai bloccare Note/Archivio,
    // che condividono invece AppExecutors.diskIO() (thread singolo)
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();

    // controllato nel loop di copia, per uscire prima se l'utente annulla — stesso pattern di VideoPlayerActivity
    private volatile boolean cancelled = false;

    public ExporterManager(SecureSession session, Context context) {
        this.noteRepository = new NoteRepository(session);
        this.fileRepository = new FileRepository(session);
        this.appContext = context.getApplicationContext(); // mai l'Activity: l'executor può vivere più a lungo di lei
    }

    /** Callback di avanzamento, opzionale (si può passare null se non interessa). */
    public interface ProgressListener {
        void onProgress(int completati, int totale);
    }

    /** Da chiamare per interrompere un export/import in corso (es. l'utente chiude la schermata). */
    public void cancel() {
        cancelled = true;
    }

    /** Mette in coda un'operazione sull'executor dedicato. */
    public void esegui(Runnable operazione) {
        cancelled = false;
        backupExecutor.execute(operazione);
    }

    /** Da chiamare quando la schermata che possiede questo manager viene distrutta: evita di lasciare thread appesi. */
    public void shutdown() {
        backupExecutor.shutdownNow();
    }

    // ================== EXPORT ==================

    /**
     * Esporta tutte le note e i file in un unico backup cifrato, scritto su destinazione.
     * Due passaggi, come già per PDF/video: prima uno zip in chiaro su file temporaneo
     * in cache, poi cifrato per intero verso destinazione.
     */
    public void exportMedia(char[] password, OutputStream destinazione, ProgressListener listener)
            throws CryptoException, IOException, BackupCryptoException, JSONException {

        File zipTemp = File.createTempFile(TEMP_PREFIX, ".zip", appContext.getCacheDir());
        try {
            scriviZipInChiaro(zipTemp, listener);

            // secondo passaggio: cifra il file temporaneo intero verso la destinazione scelta dall'utente
            try (InputStream zipInChiaro = new FileInputStream(zipTemp)) {
                backupCrypto.encryptStream(password, zipInChiaro, destinazione);
            }
        } finally {
            // il temporaneo in chiaro va sempre cancellato, anche se qualcosa è andato storto a metà
            zipTemp.delete();
        }
    }

    private void scriviZipInChiaro(File zipTemp, ProgressListener listener)
            throws CryptoException, IOException, JSONException {

        List<Nota> tutteLeNote = noteRepository.getAllNote();

        // FileRepository.getAllByTipo prende un tipo alla volta: li combiniamo qui in un'unica lista
        List<FileEntry> tuttiIFile = new ArrayList<>();
        tuttiIFile.addAll(fileRepository.getAllByTipo("foto"));
        tuttiIFile.addAll(fileRepository.getAllByTipo("video"));
        tuttiIFile.addAll(fileRepository.getAllByTipo("pdf"));

        int totale = tutteLeNote.size() + tuttiIFile.size();
        int completati = 0;

        BackupManifest manifest = BackupManifest.nuovo();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipTemp))) {

            // ---- note: già decifrate in chiaro da loadNote, scritte come entry di testo ----
            int contatoreNote = 0;
            for (Nota nota : tutteLeNote) {
                if (cancelled) return;

                String entryName = "notes/n_" + (contatoreNote++);
                String contenuto = noteRepository.loadNote(nota.id);

                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(contenuto.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                BackupManifest.NoteEntry ne = new BackupManifest.NoteEntry();
                ne.entryFile = entryName;
                ne.titolo = nota.titolo;
                ne.dataCreazione = nota.dataCreazione;
                ne.dataModifica = nota.dataModifica;
                manifest.notes.add(ne);

                completati++;
                if (listener != null) listener.onProgress(completati, totale);
            }

            // ---- file: decifrati a blocchi direttamente nella entry, mai un array intero in RAM ----
            int contatoreFile = 0;
            for (FileEntry file : tuttiIFile) {
                if (cancelled) return;

                String entryName = "files/f_" + (contatoreFile++);

                zos.putNextEntry(new ZipEntry(entryName));
                fileRepository.loadFileToStream(file.id, zos); // riusa lo stesso metodo già scritto per il video
                zos.closeEntry();

                BackupManifest.FileEntryMeta fe = new BackupManifest.FileEntryMeta();
                fe.entryFile = entryName;
                fe.tipo = file.tipo;
                fe.nomeOriginale = file.nomeOriginale;
                fe.dimensioneByte = file.dimensioneByte;
                fe.dataCreazione = file.dataCreazione;
                manifest.files.add(fe);

                completati++;
                if (listener != null) listener.onProgress(completati, totale);
            }

            // ---- manifest per ultimo: a questo punto conosciamo tutti i nomi di entry usati ----
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.toJson().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    // ================== IMPORT ==================

    /**
     * Importa un backup cifrato da sorgente. Rigenera sempre UUID nuovi (mai quelli
     * del device di origine) e ricifra tutto con il CryptoManager di QUESTO device —
     * la ricifratura avviene dentro NoteRepository.importNote/FileRepository.importFile,
     * non qui.
     */
    public void importMedia(char[] password, InputStream sorgente, ProgressListener listener)
            throws BackupCryptoException, CryptoException, IOException, JSONException {

        File zipTemp = File.createTempFile(TEMP_PREFIX, ".zip", appContext.getCacheDir());
        try {
            // primo passaggio: decifra tutto il blob verso il file temporaneo in chiaro
            try (OutputStream zipDecifrato = new FileOutputStream(zipTemp)) {
                backupCrypto.decryptStream(password, sorgente, zipDecifrato);
            }

            BackupManifest manifest = leggiManifest(zipTemp);
            if (!manifest.isSchemaSupportata()) {
                throw new IOException("Versione di backup non riconosciuta (schemaVersion=" + manifest.schemaVersion + ")");
            }

            importaContenuti(zipTemp, manifest, listener);
        } finally {
            zipTemp.delete();
        }
    }

    /** Prima passata sullo zip temporaneo: cerca solo manifest.json, salta il resto senza leggerlo. */
    private BackupManifest leggiManifest(File zipTemp) throws IOException, JSONException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipTemp))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    return BackupManifest.fromJson(leggiTuttoComeTesto(zis));
                }
                // getNextEntry() successivo salta automaticamente il resto di questa entry se non letta
            }
        }
        throw new IOException("manifest.json non trovato nel backup: file non valido");
    }

    /** Seconda passata: questa volta importa note e file veri, guidata dal manifest già letto. */
    private void importaContenuti(File zipTemp, BackupManifest manifest, ProgressListener listener)
            throws IOException, CryptoException {

        int totale = manifest.notes.size() + manifest.files.size();
        int completati = 0;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipTemp))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (cancelled) return;

                BackupManifest.NoteEntry noteMatch = trovaNota(manifest, entry.getName());
                if (noteMatch != null) {
                    String testo = leggiTuttoComeTesto(zis);
                    noteRepository.importNote(noteMatch.titolo, testo, noteMatch.dataCreazione, noteMatch.dataModifica);
                    completati++;
                    if (listener != null) listener.onProgress(completati, totale);
                    continue;
                }

                BackupManifest.FileEntryMeta fileMatch = trovaFile(manifest, entry.getName());
                if (fileMatch != null) {
                    // niente buffer intero qui: importFile legge a blocchi direttamente da questa entry
                    fileRepository.importFile(fileMatch.tipo, fileMatch.nomeOriginale, zis,
                            fileMatch.dataCreazione, fileMatch.dimensioneByte);
                    completati++;
                    if (listener != null) listener.onProgress(completati, totale);
                }
                // "manifest.json" e qualunque entry sconosciuta vengono ignorate: nessun ramo le intercetta
            }
        }
    }

    private BackupManifest.NoteEntry trovaNota(BackupManifest manifest, String entryName) {
        for (BackupManifest.NoteEntry n : manifest.notes) {
            if (n.entryFile.equals(entryName)) return n;
        }
        return null;
    }

    private BackupManifest.FileEntryMeta trovaFile(BackupManifest manifest, String entryName) {
        for (BackupManifest.FileEntryMeta f : manifest.files) {
            if (f.entryFile.equals(entryName)) return f;
        }
        return null;
    }

    /** Legge tutto il contenuto rimanente della entry ZIP corrente come stringa UTF-8 (usato solo per manifest e note, sempre piccoli). */
    private String leggiTuttoComeTesto(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int letti;
        while ((letti = zis.read(chunk)) != -1) {
            buffer.write(chunk, 0, letti);
        }
        return buffer.toString("UTF-8");
    }
}

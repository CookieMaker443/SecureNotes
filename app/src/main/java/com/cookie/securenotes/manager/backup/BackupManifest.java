package com.cookie.securenotes.manager.backup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rappresenta il contenuto di manifest.json dentro un file .secnotes: fa da indice
 * di cosa c'è nel backup (titoli, nomi originali, date) perché quei metadati vivono
 * solo nel DB cifrato, non nei file grezzi che finiscono nello zip.
 */
public class BackupManifest {

    // versione corrente dello schema: se in import ne arriva una diversa, si rifiuta subito
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String APP_NAME = "SecureNotes";

    public int schemaVersion;
    public String app;
    public String exportDate;
    public List<NoteEntry> notes = new ArrayList<>();
    public List<FileEntryMeta> files = new ArrayList<>();

    /** Crea un manifest nuovo e vuoto, pronto per essere riempito durante un export. */
    public static BackupManifest nuovo() {
        BackupManifest m = new BackupManifest();
        m.schemaVersion = CURRENT_SCHEMA_VERSION;
        m.app = APP_NAME;
        m.exportDate = Instant.now().toString(); // formato ISO-8601, leggibile e non ambiguo
        return m;
    }

    /** true se questo manifest è di una versione di schema che sappiamo leggere. */
    public boolean isSchemaSupportata() {
        return schemaVersion == CURRENT_SCHEMA_VERSION;
    }

    /** Serializza il manifest in una stringa JSON, da scrivere come entry nello zip. */
    public String toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", schemaVersion);
        root.put("app", app);
        root.put("exportDate", exportDate);

        // note: un oggetto JSON per riga della tabella notes
        JSONArray notesArray = new JSONArray();
        for (NoteEntry n : notes) {
            JSONObject o = new JSONObject();
            o.put("entryFile", n.entryFile);
            o.put("titolo", n.titolo);
            o.put("dataCreazione", n.dataCreazione);
            o.put("dataModifica", n.dataModifica);
            notesArray.put(o);
        }
        root.put("notes", notesArray);

        // file: stesso principio, un oggetto per riga della tabella files
        JSONArray filesArray = new JSONArray();
        for (FileEntryMeta f : files) {
            JSONObject o = new JSONObject();
            o.put("entryFile", f.entryFile);
            o.put("tipo", f.tipo);
            o.put("nomeOriginale", f.nomeOriginale);
            o.put("dimensioneByte", f.dimensioneByte);
            o.put("dataCreazione", f.dataCreazione);
            filesArray.put(o);
        }
        root.put("files", filesArray);

        return root.toString();
    }

    /** Ricostruisce il manifest a partire dalla stringa JSON letta dallo zip in import. */
    public static BackupManifest fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        BackupManifest m = new BackupManifest();
        m.schemaVersion = root.getInt("schemaVersion");
        m.app = root.optString("app", "");
        m.exportDate = root.optString("exportDate", "");

        JSONArray notesArray = root.optJSONArray("notes");
        if (notesArray != null) {
            for (int i = 0; i < notesArray.length(); i++) {
                JSONObject o = notesArray.getJSONObject(i);
                NoteEntry n = new NoteEntry();
                n.entryFile = o.getString("entryFile");
                n.titolo = o.optString("titolo", "");
                n.dataCreazione = o.optLong("dataCreazione", 0);
                n.dataModifica = o.optLong("dataModifica", 0);
                m.notes.add(n);
            }
        }

        JSONArray filesArray = root.optJSONArray("files");
        if (filesArray != null) {
            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject o = filesArray.getJSONObject(i);
                FileEntryMeta f = new FileEntryMeta();
                f.entryFile = o.getString("entryFile");
                f.tipo = o.getString("tipo");
                f.nomeOriginale = o.optString("nomeOriginale", "");
                f.dimensioneByte = o.optLong("dimensioneByte", 0);
                f.dataCreazione = o.optLong("dataCreazione", 0);
                m.files.add(f);
            }
        }

        return m;
    }

    /** Una nota nel manifest: entryFile è solo il nome della entry dentro lo zip interno. */
    public static class NoteEntry {
        public String entryFile;
        public String titolo;
        public long dataCreazione;
        public long dataModifica;
    }

    /** Un file (foto/video/pdf) nel manifest, stessa logica. */
    public static class FileEntryMeta {
        public String entryFile;
        public String tipo;
        public String nomeOriginale;
        public long dimensioneByte;
        public long dataCreazione;
    }
}

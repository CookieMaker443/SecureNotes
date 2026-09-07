package com.cookie.securenotes.data.local.storage;

import android.content.Context;

import java.io.File;

public class StoragePaths {

    private final File videoDir;
    private final File immaginiDir;
    private final File pdfDir;
    private final File notesDir;

    public StoragePaths(Context context) {
        File mediaDir = new File(context.getFilesDir(), "Media");

        videoDir = new File(mediaDir, "Video");
        immaginiDir = new File(mediaDir, "Images");
        pdfDir = new File(mediaDir, "PDF");
        notesDir = new File(mediaDir, "Notes");

        ensureExists(videoDir);
        ensureExists(immaginiDir);
        ensureExists(pdfDir);
        ensureExists(notesDir);
    }

    private void ensureExists(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Impossibile creare la cartella: " + dir.getAbsolutePath());
        }
    }

    public File getVideoDir() { return videoDir; }
    public File getImmaginiDir() { return immaginiDir; }
    public File getPdfDir() { return pdfDir; }
    public File getNotesDir() { return notesDir; }
}
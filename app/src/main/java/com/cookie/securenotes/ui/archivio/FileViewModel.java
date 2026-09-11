package com.cookie.securenotes.ui.archivio;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.LruCache;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cookie.securenotes.data.local.db.FileEntry;
import com.cookie.securenotes.manager.repository.FileRepository;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.util.AppExecutors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class FileViewModel extends ViewModel {

    private static final int THUMBNAIL_TARGET_SIZE_PX = 200;

    private final FileRepository repository;
    private final AppExecutors executors;

    private final MutableLiveData<List<FileEntry>> files = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Cache in RAM delle miniature già decifrate. Dimensione ~1/8 dell'heap disponibile,
    // criterio standard consigliato da Android per LruCache di immagini.
    private final LruCache<Long, Bitmap> thumbnailCache = new LruCache<Long, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
        @Override
        protected int sizeOf(Long key, Bitmap bitmap) {
            return bitmap.getByteCount() / 1024;
        }
    };

    public FileViewModel() {
        this.repository = new FileRepository(SecureSession.getInstance());
        this.executors = AppExecutors.getInstance();
    }

    public LiveData<List<FileEntry>> getFiles() { return files; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void loadFiles(String tipo) {
        executors.diskIO().execute(() -> {
            List<FileEntry> result = repository.getAllByTipo(tipo);
            executors.mainThread(() -> files.setValue(result));
        });
    }

    public void search(String tipo, String query) {
        executors.diskIO().execute(() -> {
            List<FileEntry> result = (query == null || query.isEmpty())
                    ? repository.getAllByTipo(tipo)
                    : repository.searchFile(tipo, query);
            executors.mainThread(() -> files.setValue(result));
        });
    }

    public void addFile(String tipo, Uri uri, ContentResolver resolver) {
        executors.diskIO().execute(() -> {
            try {
                String nomeOriginale = queryDisplayName(resolver, uri);
                byte[] dati = readAllBytes(resolver, uri);
                repository.saveFile(nomeOriginale, tipo, dati);
                loadFiles(tipo);
            } catch (Exception e) {
                executors.mainThread(() ->
                        errorMessage.setValue("Errore nell'importazione: " + e.getMessage()));
            }
        });
    }

    public void deleteFile(String tipo, long id) {
        executors.diskIO().execute(() -> {
            try {
                repository.deleteFile(id);
                thumbnailCache.remove(id); // evita di tenere in RAM una miniatura di un file ormai cancellato
                loadFiles(tipo);
            } catch (IOException e) {
                executors.mainThread(() ->
                        errorMessage.setValue("Errore nell'eliminazione: " + e.getMessage()));
            }
        });
    }

    /** Solo per foto: decifra, ridimensiona e mette in cache la miniatura. */
    public void loadThumbnail(FileEntry entry, FileGridAdapter.ThumbnailReadyCallback callback) {
        Bitmap cached = thumbnailCache.get(entry.id);
        if (cached != null) {
            callback.onReady(cached);
            return;
        }

        executors.diskIO().execute(() -> {
            Bitmap bitmap = null;
            try {
                byte[] datiInChiaro = repository.loadFile(entry.id);
                bitmap = decodeSampledBitmap(datiInChiaro, THUMBNAIL_TARGET_SIZE_PX);
                if (bitmap != null) {
                    thumbnailCache.put(entry.id, bitmap);
                }
            } catch (Exception e) {
                // Errore silenzioso qui apposta: se una singola miniatura fallisce,
                // meglio un placeholder vuoto che un Toast per ogni riga della griglia.
            }
            Bitmap finalBitmap = bitmap;
            executors.mainThread(() -> callback.onReady(finalBitmap));
        });
    }

    /** Decodifica un'immagine ridotta, evitando di caricare la risoluzione piena solo per una miniatura. */
    private Bitmap decodeSampledBitmap(byte[] data, int targetSizePx) {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, boundsOptions);

        int sampleSize = 1;
        int halfWidth = boundsOptions.outWidth / 2;
        int halfHeight = boundsOptions.outHeight / 2;
        while ((halfWidth / sampleSize) >= targetSizePx && (halfHeight / sampleSize) >= targetSizePx) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);
    }

    private String queryDisplayName(ContentResolver resolver, Uri uri) {
        String name = "file_" + System.currentTimeMillis();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String result = cursor.getString(idx);
                    if (result != null) name = result;
                }
            }
        }
        return name;
    }

    private byte[] readAllBytes(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Impossibile aprire il file selezionato");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
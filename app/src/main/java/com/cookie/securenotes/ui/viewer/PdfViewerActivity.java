package com.cookie.securenotes.ui.viewer;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.widget.Toast;

import com.cookie.securenotes.R;
import com.cookie.securenotes.manager.repository.FileRepository;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.util.AppExecutors;

import java.io.File;
import java.io.FileOutputStream;

public class PdfViewerActivity extends SecureViewerActivity {

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private File tempFile;
    private PdfRenderer.Page currentPage;
    private int pageIndex = 0;
    private ImageView pageImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        pageImageView = findViewById(R.id.pdfPageImageView);
        findViewById(R.id.prevPageButton).setOnClickListener(v -> showPage(pageIndex - 1));
        findViewById(R.id.nextPageButton).setOnClickListener(v -> showPage(pageIndex + 1));

        long fileId = getFileId();
        FileRepository repository = new FileRepository(SecureSession.getInstance());

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                byte[] dati = repository.loadFile(fileId);
                tempFile = File.createTempFile("pdf_view_", ".pdf", getCacheDir());
                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    out.write(dati);
                }
                fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(fileDescriptor);

                AppExecutors.getInstance().mainThread(() -> showPage(0));
            } catch (Exception e) {
                AppExecutors.getInstance().mainThread(() -> {
                    Toast.makeText(this, getString(R.string.error_loading_file), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void showPage(int index) {
        if (pdfRenderer == null || index < 0 || index >= pdfRenderer.getPageCount()) return;
        if (currentPage != null) currentPage.close();

        pageIndex = index;
        currentPage = pdfRenderer.openPage(index);
        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        pageImageView.setImageBitmap(bitmap);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (currentPage != null) currentPage.close();
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
        } catch (Exception ignored) {}
        if (tempFile != null && tempFile.exists()) tempFile.delete();
    }
}
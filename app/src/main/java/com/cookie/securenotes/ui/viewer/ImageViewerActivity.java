package com.cookie.securenotes.ui.viewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import com.cookie.securenotes.R;
import com.cookie.securenotes.manager.repository.FileRepository;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.util.AppExecutors;

public class ImageViewerActivity extends SecureViewerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ImageView imageView = findViewById(R.id.fullImageView);
        long fileId = getFileId();
        FileRepository repository = new FileRepository(SecureSession.getInstance());

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                byte[] dati = repository.loadFile(fileId);
                Bitmap bitmap = BitmapFactory.decodeByteArray(dati, 0, dati.length);
                AppExecutors.getInstance().mainThread(() -> {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    } else {
                        Toast.makeText(this, getString(R.string.error_loading_file), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            } catch (Exception e) {
                AppExecutors.getInstance().mainThread(() -> {
                    Toast.makeText(this, getString(R.string.error_loading_file), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }
}
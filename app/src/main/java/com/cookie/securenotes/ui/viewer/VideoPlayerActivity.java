package com.cookie.securenotes.ui.viewer;

import android.os.Bundle;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.db.FileEntry;
import com.cookie.securenotes.manager.repository.FileRepository;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.util.AppExecutors;

import java.io.File;
import java.io.FileOutputStream;

@UnstableApi
public class VideoPlayerActivity extends SecureViewerActivity {

    private static final String TAG = "SecureNotesVideo"; // per debug

    private ExoPlayer player;
    private File tempFile;
    private volatile boolean cancelled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        PlayerView playerView = findViewById(R.id.playerView);

        SecureSession session = SecureSession.getInstance();
        FileRepository repository = new FileRepository(session);
        long fileId = getFileId();

        // Il recupero da Room va fatto fuori dal main thread, come già per Foto/PDF.
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                FileEntry entry = repository.getById(fileId);
                if (entry == null || cancelled) {
                    if (!cancelled) AppExecutors.getInstance().mainThread(this::finish);
                    return;
                }

                // Decifratura in streaming (64KB alla volta), non un array unico in RAM:
                // evita di caricare l'intero video in memoria per file lunghi.
                tempFile = File.createTempFile("video_view_", ".mp4", getCacheDir());
                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    repository.loadFileToStream(fileId, out);
                }

                if (cancelled) return; // l'utente è già uscito, non serve più continuare

                AppExecutors.getInstance().mainThread(() -> {
                    if (!cancelled) setupPlayer(playerView);
                });
            } catch (Exception e) {
                Log.e(TAG, "Errore caricamento video", e);
                if (!cancelled) AppExecutors.getInstance().mainThread(this::finish);
            }
        });
    }

    private void setupPlayer(PlayerView playerView) {
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Errore riproduzione video", error);
            }
        });
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(tempFile.toURI().toString()));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (tempFile != null && tempFile.exists()) tempFile.delete();
    }
}
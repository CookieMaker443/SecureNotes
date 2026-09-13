package com.cookie.securenotes.ui.viewer;

import android.os.Bundle;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.db.FileEntry;
import com.cookie.securenotes.manager.repository.FileRepository;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.util.AppExecutors;

import java.io.File;
import android.util.Log;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

@UnstableApi
public class VideoPlayerActivity extends SecureViewerActivity {

    private ExoPlayer player;

    //per debug
    private static final String TAG = "SecureNotesVideo";

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
            FileEntry entry = repository.getById(fileId);
            AppExecutors.getInstance().mainThread(() -> {
                if (entry == null) {
                    finish();
                    return;
                }
                setupPlayer(playerView, session, entry);
            });
        });
    }

    private void setupPlayer(PlayerView playerView, SecureSession session, FileEntry entry) {
        File encryptedFile = new File(session.getStoragePaths().getVideoDir(), entry.nomeFisico);

        DataSource.Factory factory = () -> new EncryptedFileDataSource(
                encryptedFile, session.getCryptoManager());

        MediaSource mediaSource = new ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(encryptedFile.toURI().toString()));

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                android.util.Log.e(TAG, "Errore riproduzione video", error);
            }
        });
        playerView.setPlayer(player);
        player.setMediaSource(mediaSource);
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
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
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

import java.io.File;

@UnstableApi
public class VideoPlayerActivity extends SecureViewerActivity {

    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        PlayerView playerView = findViewById(R.id.playerView);

        SecureSession session = SecureSession.getInstance();
        FileRepository repository = new FileRepository(session);
        FileEntry entry = repository.getById(getFileId()); // vedi nota sotto

        File encryptedFile = new File(
                session.getStoragePaths().getVideoDir(), entry.nomeFisico);

        DataSource.Factory factory = () -> new EncryptedFileDataSource(
                encryptedFile, session.getCryptoManager());

        MediaSource mediaSource = new ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(encryptedFile.toURI().toString()));

        player = new ExoPlayer.Builder(this).build();
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
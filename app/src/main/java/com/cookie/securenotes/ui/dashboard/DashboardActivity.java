package com.cookie.securenotes.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.db.Nota;
import com.cookie.securenotes.data.local.prefs.AppSettings;
import com.cookie.securenotes.ui.common.BaseActivity;
import com.cookie.securenotes.ui.notes.NoteAdapter;
import com.cookie.securenotes.ui.notes.NoteEditorActivity;
import com.cookie.securenotes.ui.notes.NotesListActivity;
import com.cookie.securenotes.ui.settings.SettingsActivity;

public class DashboardActivity extends BaseActivity {

    private DashboardViewModel viewModel;
    private AppSettings appSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        appSettings = new AppSettings(this);
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        findViewById(R.id.buttonNotes).setOnClickListener(v ->
                startActivity(new Intent(this, NotesListActivity.class)));

        findViewById(R.id.buttonArchivio).setOnClickListener(v -> {
            // TODO: collegare ArchivioActivity quando sarà pronta
        });

        ImageButton settingsBtn = findViewById(R.id.imageButton);
        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        RecyclerView recentNotesRecyclerView = findViewById(R.id.recentNotesRecyclerView);
        NoteAdapter adapter = new NoteAdapter(new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(Nota nota) {
                startActivity(NoteEditorActivity.newEditIntent(DashboardActivity.this, nota.id));
            }

            @Override
            public void onNoteLongClick(Nota nota) {
                // Nessuna azione qui: l'eliminazione resta una funzione della lista completa,
                // non dell'anteprima in dashboard.
            }
        });
        recentNotesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recentNotesRecyclerView.setAdapter(adapter);

        viewModel.getRecentNotes().observe(this, adapter::submitList);
    }

    @Override
    protected void onResume() {
        super.onResume(); // fondamentale: verifica lock/timeout di BaseActivity
        viewModel.loadRecentNotes(appSettings.getRecentNotesCount());
    }
}
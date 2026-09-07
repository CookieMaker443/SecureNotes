package com.cookie.securenotes.ui.notes;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.db.Nota;
import com.cookie.securenotes.ui.common.BaseActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class NotesListActivity extends BaseActivity {

    private NoteViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_notes));
        }

        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        RecyclerView recyclerView = findViewById(R.id.notesRecyclerView);
        NoteAdapter adapter = new NoteAdapter(new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(Nota nota) {
                startActivity(NoteEditorActivity.newEditIntent(NotesListActivity.this, nota.id));
            }

            @Override
            public void onNoteLongClick(Nota nota) {
                confirmDelete(nota.id);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        EditText searchInput = findViewById(R.id.searchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.search(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        FloatingActionButton addButton = findViewById(R.id.addNoteButton);
        addButton.setOnClickListener(v ->
                startActivity(NoteEditorActivity.newCreateIntent(NotesListActivity.this)));

        viewModel.getNotes().observe(this, adapter::submitList);
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadAllNotes();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void confirmDelete(long notaId) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_title))
                .setMessage(getString(R.string.dialog_delete_message))
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> viewModel.deleteNote(notaId))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }
}
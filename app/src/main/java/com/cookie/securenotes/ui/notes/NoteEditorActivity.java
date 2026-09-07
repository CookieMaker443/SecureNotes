package com.cookie.securenotes.ui.notes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.cookie.securenotes.R;
import com.cookie.securenotes.ui.common.BaseActivity;

public class NoteEditorActivity extends BaseActivity {

    private static final String EXTRA_NOTE_ID = "extra_note_id";
    private static final long NO_ID = -1L;

    private NoteViewModel viewModel;
    private EditText titleInput;
    private EditText contentInput;
    private long editingNoteId = NO_ID;

    /** Modalità creazione: nessun id. */
    public static Intent newCreateIntent(Context context) {
        return new Intent(context, NoteEditorActivity.class);
    }

    /** Modalità apertura/modifica: passa l'id della nota esistente. */
    public static Intent newEditIntent(Context context, long noteId) {
        Intent intent = new Intent(context, NoteEditorActivity.class);
        intent.putExtra(EXTRA_NOTE_ID, noteId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        titleInput = findViewById(R.id.titleInput);
        contentInput = findViewById(R.id.contentInput);
        findViewById(R.id.saveButton).setOnClickListener(v -> save());

        editingNoteId = getIntent().getLongExtra(EXTRA_NOTE_ID, NO_ID);
        if (editingNoteId != NO_ID) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.title_edit_note));
            }
            viewModel.loadNoteContent(editingNoteId);
        } else if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.title_new_note));
        }

        viewModel.getEditorTitle().observe(this, titleInput::setText);
        viewModel.getEditorContent().observe(this, contentInput::setText);
        viewModel.getSaveCompleted().observe(this, completed -> {
            if (completed != null && completed) {
                finish();
            }
        });
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }

    private void save() {
        String titolo = titleInput.getText().toString().trim();
        String contenuto = contentInput.getText().toString();

        if (titolo.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_title_required), Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.saveNote(titolo, contenuto);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
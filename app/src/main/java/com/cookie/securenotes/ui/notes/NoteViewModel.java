package com.cookie.securenotes.ui.notes;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cookie.securenotes.data.local.db.Nota;
import com.cookie.securenotes.manager.repository.NoteRepository;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.util.AppExecutors;

import java.util.List;

public class NoteViewModel extends ViewModel {

    private final NoteRepository repository;
    private final AppExecutors executors;

    private final MutableLiveData<List<Nota>> notes = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private final MutableLiveData<String> editorTitle = new MutableLiveData<>();
    private final MutableLiveData<String> editorContent = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveCompleted = new MutableLiveData<>();

    public NoteViewModel() {
        this.repository = new NoteRepository(SecureSession.getInstance());
        this.executors = AppExecutors.getInstance();
    }

    public LiveData<List<Nota>> getNotes() { return notes; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<String> getEditorTitle() { return editorTitle; }
    public LiveData<String> getEditorContent() { return editorContent; }
    public LiveData<Boolean> getSaveCompleted() { return saveCompleted; }

    public void loadAllNotes() {
        executors.diskIO().execute(() -> {
            List<Nota> result = repository.getAllNote();
            executors.mainThread(() -> notes.setValue(result));
        });
    }

    public void search(String query) {
        executors.diskIO().execute(() -> {
            List<Nota> result = (query == null || query.isEmpty())
                    ? repository.getAllNote()
                    : repository.searchNote(query);
            executors.mainThread(() -> notes.setValue(result));
        });
    }

    public void saveNote(String titolo, String contenuto) {
        executors.diskIO().execute(() -> {
            try {
                repository.saveNote(titolo, contenuto);
                executors.mainThread(() -> saveCompleted.setValue(true));
            } catch (Exception e) {
                executors.mainThread(() ->
                        errorMessage.setValue("Errore nel salvataggio: " + e.getMessage()));
            }
        });
    }

    public void deleteNote(long id) {
        executors.diskIO().execute(() -> {
            try {
                repository.deleteNote(id);
                loadAllNotes();
            } catch (Exception e) {
                executors.mainThread(() ->
                        errorMessage.setValue("Errore nell'eliminazione: " + e.getMessage()));
            }
        });
    }

    public void loadNoteContent(long id) {
        executors.diskIO().execute(() -> {
            try {
                String titolo = repository.getTitolo(id);
                String contenuto = repository.loadNote(id);
                // il titolo non serve ricifrarlo: lo recuperiamo dalla lista già in memoria
                // in alternativa, aggiungere un metodo repository.getTitolo(id) se serve isolato
                executors.mainThread(() -> editorContent.setValue(contenuto));
            } catch (Exception e) {
                executors.mainThread(() ->
                        errorMessage.setValue("Errore nel caricamento: " + e.getMessage()));
            }
        });
    }
}
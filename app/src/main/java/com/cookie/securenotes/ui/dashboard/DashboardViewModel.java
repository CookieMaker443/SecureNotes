package com.cookie.securenotes.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cookie.securenotes.data.local.db.Nota;
import com.cookie.securenotes.manager.repository.NoteRepository;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.util.AppExecutors;

import java.util.List;

public class DashboardViewModel extends ViewModel {

    private final NoteRepository noteRepository;
    private final AppExecutors executors;

    private final MutableLiveData<List<Nota>> recentNotes = new MutableLiveData<>();

    public DashboardViewModel() {
        this.noteRepository = new NoteRepository(SecureSession.getInstance());
        this.executors = AppExecutors.getInstance();
    }

    public LiveData<List<Nota>> getRecentNotes() {
        return recentNotes;
    }

    public void loadRecentNotes(int count) {
        executors.diskIO().execute(() -> {
            List<Nota> result = noteRepository.getRecentNotes(count);
            executors.mainThread(() -> recentNotes.setValue(result));
        });
    }
}
package com.cookie.securenotes.ui.archivio;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.db.FileEntry;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public abstract class BaseFileListFragment extends Fragment {

    private FileViewModel viewModel;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    viewModel.addFile(getTipo(), uri, requireContext().getContentResolver());
                }
            });

    protected abstract String getTipo();
    protected abstract String[] getMimeTypes();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(FileViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.filesRecyclerView);
        FileAdapter adapter = new FileAdapter(new FileAdapter.OnFileClickListener() {
            @Override
            public void onFileClick(FileEntry fileEntry) {
                // TODO: aprire un visualizzatore dedicato (immagine/video/PDF)
            }

            @Override
            public void onFileLongClick(FileEntry fileEntry) {
                confirmDelete(fileEntry.id);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        EditText searchInput = view.findViewById(R.id.searchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.search(getTipo(), s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        FloatingActionButton addButton = view.findViewById(R.id.addFileButton);
        addButton.setOnClickListener(v -> filePickerLauncher.launch(getMimeTypes()));

        viewModel.getFiles().observe(getViewLifecycleOwner(), adapter::submitList);
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadFiles(getTipo());
    }

    private void confirmDelete(long fileId) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_delete_file_title))
                .setMessage(getString(R.string.dialog_delete_message))
                .setPositiveButton(getString(R.string.action_delete),
                        (dialog, which) -> viewModel.deleteFile(getTipo(), fileId))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }
}
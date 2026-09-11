package com.cookie.securenotes.ui.archivio;

import com.cookie.securenotes.data.local.db.FileEntry;

import java.util.List;

public interface FilesAdapter {
    void submitList(List<FileEntry> items);
}
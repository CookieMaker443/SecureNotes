package com.cookie.securenotes.ui.archivio;

import com.cookie.securenotes.data.local.db.FileEntry;

public interface OnFileClickListener {
    void onFileClick(FileEntry fileEntry);
    void onFileLongClick(FileEntry fileEntry);
}
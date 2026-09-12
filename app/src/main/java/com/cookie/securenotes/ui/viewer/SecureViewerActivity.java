package com.cookie.securenotes.ui.viewer;

import com.cookie.securenotes.ui.common.BaseActivity;

public abstract class SecureViewerActivity extends BaseActivity {
    public static final String EXTRA_FILE_ID = "extra_file_id";

    protected long getFileId() {
        return getIntent().getLongExtra(EXTRA_FILE_ID, -1);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
package com.cookie.securenotes.ui.archivio;

public class VideoFragment extends BaseFileListFragment {
    @Override protected String getTipo() { return "video"; }
    @Override protected String[] getMimeTypes() {
        return new String[]{"video/mp4", "video/3gpp", "video/webm"};
    }
    @Override protected boolean useGridLayout() { return true; }

}
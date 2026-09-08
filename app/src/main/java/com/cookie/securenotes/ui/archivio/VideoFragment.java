package com.cookie.securenotes.ui.archivio;

public class VideoFragment extends BaseFileListFragment {
    @Override protected String getTipo() { return "video"; }
    @Override protected String[] getMimeTypes() { return new String[]{"video/*"}; }
}
package com.cookie.securenotes.ui.archivio;

public class FotoFragment extends BaseFileListFragment {
    @Override protected String getTipo() { return "foto"; }
    @Override protected String[] getMimeTypes() { return new String[]{"image/*"}; }
}
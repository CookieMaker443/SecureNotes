package com.cookie.securenotes.ui.archivio;

public class PdfFragment extends BaseFileListFragment {
    @Override protected String getTipo() { return "pdf"; }
    @Override protected String[] getMimeTypes() { return new String[]{"application/pdf"}; }
}
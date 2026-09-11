package com.cookie.securenotes.ui.archivio;

public class PdfFragment extends BaseFileListFragment {
    @Override protected String getTipo() { return "pdf"; }
    @Override protected String[] getMimeTypes() { return new String[]{"application/pdf"}; }

    // qui non faccio override perche la visualizzazione dei pdf resta invaraita ed eredita false
    // @Override protected boolean useGridLayout() { return true; }
}
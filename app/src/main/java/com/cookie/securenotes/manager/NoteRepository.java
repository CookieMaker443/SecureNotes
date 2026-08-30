package com.cookie.securenotes.manager;

public class NoteRepository {
    private final SecurePrefsManager prefsManager;
    private final String localDatabase = notes_index.db;

    public NoteRepository(){}

    public String searchNote(String nomeFile){

        // cerca nel database
    }

    public Stream loadNote(String nomeNota){
        // cerca la nota

        // decriptala con cryptomanager
        // crypthomanager dectyptNote(nomeFile)

        // return file
    }

    public void saveNote(String nomeNota){
        // prendi il file

        //criptalo con cryptomanager
        // crypthomanager enctyptFile(nomeNota)

        //salva sul disco

        // return niente
    }
}

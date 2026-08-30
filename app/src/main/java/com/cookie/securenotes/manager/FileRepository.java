package com.cookie.securenotes.manager;

public class FileRepository {

    private final SecurePrefsManager prefsManager;
    private final String localDatabase = files_index.db;

    public FileRepository(){}

    public String searchFile(String nomeFile){

        // cerca nel database
    }

    public Stream loadFile(String nomeFile){
        // cerca il file

        // decriptalo con cryptomanager
        // crypthomanager dectyptFile()

        // return file
    }

    public void saveFile(String nomeFile){
        // prendi il file

        //criptalo con cryptomanager
        // crypthomanager enctyptFile()

        // return niente
    }


}

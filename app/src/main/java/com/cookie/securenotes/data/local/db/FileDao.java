package com.cookie.securenotes.data.local.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FileDao {

    @Insert
    long insert(FileEntry fileEntry);

    @Delete
    void delete(FileEntry fileEntry);

    @Query("SELECT * FROM files WHERE id = :id")
    FileEntry getById(long id);

    @Query("SELECT * FROM files WHERE tipo = :tipo AND nome_originale LIKE '%' || :query || '%' ORDER BY data_creazione DESC")
    List<FileEntry> search(String tipo, String query);

    @Query("SELECT * FROM files WHERE tipo = :tipo ORDER BY data_creazione DESC")
    List<FileEntry> getAllByTipo(String tipo);
}
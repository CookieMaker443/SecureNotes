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

    @Query("SELECT * FROM files WHERE type = :tipo AND file_name LIKE '%' || :query || '%' ORDER BY creation_date DESC")
    List<FileEntry> search(String tipo, String query);

    @Query("SELECT * FROM files WHERE type = :tipo ORDER BY creation_date DESC")
    List<FileEntry> getAllByTipo(String tipo);
}
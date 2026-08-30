package com.cookie.securenotes.data.local.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NoteDao {

    @Insert
    long insert(Nota nota);

    @Update
    void update(Nota nota);

    @Delete
    void delete(Nota nota);

    @Query("SELECT * FROM notes WHERE id = :id")
    Nota getById(long id);

    @Query("SELECT * FROM notes WHERE titolo LIKE '%' || :query || '%' ORDER BY data_modifica DESC")
    List<Nota> search(String query);

    @Query("SELECT * FROM notes ORDER BY data_modifica DESC")
    List<Nota> getAll();
}
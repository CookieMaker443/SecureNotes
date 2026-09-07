package com.cookie.securenotes.data.local.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Nota {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "title")
    public String titolo; // in chiaro, serve per la ricerca LIKE

    @ColumnInfo(name = "name_on_disk")
    public String nomeFisico; // UUID.toString(), nome reale del file in Media/Notes/

    @ColumnInfo(name = "date_creation")
    public long dataCreazione;

    @ColumnInfo(name = "date_modify")
    public long dataModifica;
}
package com.cookie.securenotes.data.local.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Nota {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "titolo")
    public String titolo; // in chiaro, serve per la ricerca LIKE

    @ColumnInfo(name = "nome_fisico")
    public String nomeFisico; // UUID.toString(), nome reale del file in Media/Notes/

    @ColumnInfo(name = "data_creazione")
    public long dataCreazione;

    @ColumnInfo(name = "data_modifica")
    public long dataModifica;
}
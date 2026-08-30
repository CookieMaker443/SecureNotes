package com.cookie.securenotes.data.local.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "files")
public class FileEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "nome_originale")
    public String nomeOriginale; // es. "gatto.png", in chiaro solo qui nell'indice cifrato

    @ColumnInfo(name = "nome_fisico")
    public String nomeFisico; // UUID.toString(), nome reale del file su disco

    @ColumnInfo(name = "tipo")
    public String tipo; // "foto" | "video" | "pdf"

    @ColumnInfo(name = "dimensione_byte")
    public long dimensioneByte;

    @ColumnInfo(name = "data_creazione")
    public long dataCreazione;
}
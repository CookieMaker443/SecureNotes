package com.cookie.securenotes.data.local.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "files")
public class FileEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "file_name")
    public String nomeOriginale; // es. "gatto.png", in chiaro solo qui nell'indice cifrato

    @ColumnInfo(name = "name_on_disk")
    public String nomeFisico; // UUID.toString(), nome reale del file su disco

    @ColumnInfo(name = "type")
    public String tipo; // "foto" | "video" | "pdf"

    @ColumnInfo(name = "dimension_byte")
    public long dimensioneByte;

    @ColumnInfo(name = "creation_date")
    public long dataCreazione;
}
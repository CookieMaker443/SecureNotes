package com.cookie.securenotes.data.local.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import net.sqlcipher.database.SupportFactory;

import java.io.File;

@Database(entities = {Nota.class}, version = 1, exportSchema = false)
public abstract class NotesDatabase extends RoomDatabase {

    public abstract NoteDao noteDao();

    public static NotesDatabase open(Context context, byte[] dbKey) {
        File indexDir = context.getDir(".info", Context.MODE_PRIVATE);
        File dbFile = new File(indexDir, "notes_index.db");

        SupportFactory factory = new SupportFactory(dbKey);

        return Room.databaseBuilder(context.getApplicationContext(), NotesDatabase.class, dbFile.getAbsolutePath())
                .openHelperFactory(factory)
                .build();
    }
}
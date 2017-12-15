package ba.elektronika.wifilock.database;

/**
 * Created by Trax on 09/12/2017.
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHandler extends SQLiteOpenHelper {

    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "WifiLock.db";

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    private void createTableLock(SQLiteDatabase db) {
        String sql = "CREATE TABLE lock (" +
                "bssid TEXT PRIMARY KEY NOT NULL," +
                "ssid TEXT NOT NULL," +
                "password TEXT NOT NULL," +
                "encryption_type TEXT NOT NULL," +
                "pincode TEXT NOT NULL," +
                "created_on INTEGER," +
                "last_accessed_on INTEGER" +
                ");";
        db.execSQL(sql);
    }

    // KEY-VAL Settings for Global usage
    private void createTablePubVar(SQLiteDatabase db) {
        String sql = "CREATE TABLE pubvar ("
                + "IDpubvar INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                + "key TEXT,"
                + "val TEXT);";
        db.execSQL(sql);
    }

    private void createDB(SQLiteDatabase db)
    {
        createTablePubVar(db);
        createTableLock(db);
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        createDB(db);
    }

    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(newVersion > oldVersion) {
            db.execSQL("DROP TABLE IF EXISTS base");
            db.execSQL("DROP TABLE IF EXISTS base_data");
            db.execSQL("DROP TABLE IF EXISTS client2server");
            db.execSQL("DROP TABLE IF EXISTS pubvar");

            createDB(db);
        }
    }

}
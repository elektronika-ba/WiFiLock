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
        String sql = "CREATE TABLE wlock (" +
                "wlockid INT PRIMARY KEY," +
                "bssid TEXT NOT NULL," +
                "ssid TEXT NOT NULL," +
                "password TEXT NOT NULL," +
                "encryption_type TEXT NOT NULL," +
                "pincode TEXT NOT NULL," +
                "created_on INTEGER," +
                "last_accessed_on INTEGER," +
                "title TEXT" +
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

    private void insertTestWLocks(SQLiteDatabase db) {
        String sql;

        sql = "INSERT INTO wlock (bssid, ssid, password, encryption_type, pincode, created_on, last_accessed_on, title) VALUES('00:1c:df:e9:8d:d1','Belkin_N_Wireless_E98DD1','1234567890','WPA-PSK/WPA2-PSK','0000','0','0','Testna brava');";
        db.execSQL(sql);
        sql = "INSERT INTO wlock (bssid, ssid, password, encryption_type, pincode, created_on, last_accessed_on, title) VALUES('01:1c:df:e9:8d:d1','Fake SSID here','1234567890','WPA-PSK/WPA2-PSK','0000','0','0','Ko fol brava');";
        db.execSQL(sql);
    }


    private void createDB(SQLiteDatabase db)
    {
        createTablePubVar(db);
        createTableLock(db);

        // insert some test wlocks
        insertTestWLocks(db);
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
            // TODO: skontaj sta treba ovdje kada bude bilo kakvih upgrejda

            //db.execSQL("DROP TABLE IF EXISTS pubvar");

            createDB(db);
        }
    }

}
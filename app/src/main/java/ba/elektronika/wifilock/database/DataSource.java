package ba.elektronika.wifilock.database;

/**
 * Created by Trax on 09/12/2017.
 */

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;

import ba.elektronika.wifilock.WLock;

public class DataSource {
    private SQLiteDatabase db = null;
    private DatabaseHandler handler = null;
    private static DataSource instance = null;
    private int useCounter = 0;

    private DataSource() {
    }

    private DataSource(Context context) {
        if (handler == null) {
            handler = new DatabaseHandler(context.getApplicationContext());
        }
    }

    /**
     * Call this to get access to the instance of CtrlDataSource Singleton
     */
    public static synchronized DataSource getInstance(Context context) {
        if (instance == null) {
            instance = new DataSource(context);
        }
        return instance;
    }

    private void open() throws SQLException {
        useCounter++;

        db = handler.getWritableDatabase();
    }

    private void close() {
        if (useCounter > 0) {
            useCounter--;
        }

        // this makes it thread-safe the stupid-way, but it kind of works on my
        // phone!
        // TODO UPDATE: this doesn't work great, I still get Database Locked
        // errors
        if (useCounter <= 0) {
            handler.close();
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Clone is not allowed.");
    }

    // Database related functions
    // //////////////////////////

    /**
     * Get one Lock from the database.
     * @param BSSID
     * @return
     */
    public WLock getLock(String BSSID) {
        WLock wlock = null;

        open();
        Cursor cursor = db.rawQuery("SELECT bssid, ssid, password, encryption_type, pincode, created_on, last_accessed_on FROM lock WHERE bssid = ?", new String[] { BSSID });

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            wlock = new WLock(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getLong(6));
        }

        cursor.close();
        close();

        return wlock;
    }

    /**
     * Get all Locks from the database.
     * @return ArrayList of WLock type
     */
    public ArrayList<WLock> getAllLocks() {
        ArrayList<WLock> lockList = new ArrayList<WLock>();

        open();
        Cursor cursor = db.rawQuery("SELECT bssid, ssid, password, encryption_type, pincode, created_on, last_accessed_on FROM lock", null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                WLock wlock = new WLock(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getLong(6));
                lockList.add(wlock);
            }
            while (cursor.moveToNext());
        }

        cursor.close();
        close();

        return lockList;
    }

    public void updateLockLastActivity(String BSSID) {
        if (BSSID.equals(""))
            return;

        open();
        ContentValues values = new ContentValues();
        values.put("last_accessed_on", System.currentTimeMillis());
        db.update("lock", values, "bssid = ?", new String[] { BSSID });

        close();
    }

    public void deleteLock(String BSSID) {
        open();

        db.delete("lock", "bssid = ?", new String[] { BSSID });

        close();
    }

    /**
     * Get pubvar with default value on no-value from DB.
     * @param key
     * @param defaultValue
     * @return
     */
    public String getPubVar(String key, String defaultValue) {
        String r = getPubVar(key);
        if (r == null || r.equals(""))
            return defaultValue;
        else
            return r;
    }

    /**
     * Get pubvar without default value. On no-value returns empty string.
     * @param key
     * @return
     */
    public String getPubVar(String key) {
        if (key.equals("")) {
            return "";
        }

        open();
        Cursor cursor = db.rawQuery("SELECT val FROM pubvar WHERE key = ?", new String[] { key });

        if (cursor.moveToFirst()) {
            String rez = cursor.getString(0);
            cursor.close();
            close();
            return rez;
        }
        else {
            cursor.close();
            close();
            return "";
        }
    }

    /**
     * Saves pubvar value.
     * @param key
     * @param val
     * @return
     */
    public boolean savePubVar(String key, String val) {
        if (key.equals("")) {
            return false;
        }

        open();
        Cursor cursor = db.rawQuery("SELECT val FROM pubvar WHERE key = ?", new String[] { key });
        if (cursor.moveToFirst()) {
            cursor.close();
            close();

            // UPDATE
            open();
            ContentValues values = new ContentValues();
            values.put("val", val);
            db.update("pubvar", values, "key = ?", new String[] { key });
            close();

            return true;
        }
        else {
            cursor.close();
            close();

            // INSERT
            open();
            ContentValues values = new ContentValues();
            values.put("key", key);
            values.put("val", val);
            db.insert("pubvar", null, values);
            close();

            return true;
        }
    }

}
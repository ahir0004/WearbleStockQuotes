package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by macallan on 17-09-16.
 */
public class StockListDB extends SQLiteOpenHelper {
    private static String DATABASE_NAME = "wearable_stocks.db";
    private static int VERSION = 1;
    private static StockListDB sInstance;

    private StockListDB (Context context) {
        super (context, DATABASE_NAME, null, VERSION);
    }

    public static synchronized StockListDB getInstance (Context context) {

        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = new StockListDB (context.getApplicationContext ());
        }
        return sInstance;
    }

    @Override
    public void onCreate (SQLiteDatabase db) {
        db.execSQL ("CREATE TABLE STOCK_QUOTES ( _ID INTEGER PRIMARY KEY AUTOINCREMENT, STOCK_CODE, " +
                "SUSPEND_NOTIFICATION integer, " +
                "HASHED_NAME integer, LIVE_DATA text, UNIQUE (STOCK_CODE) ON CONFLICT ABORT );");

        db.execSQL ("CREATE TABLE HIST_STOCK_QUOTES (HIST_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "TRADE_DATE numeric, " +
                "JSON_OBJECT text, " +
                "STOCK_ID integer NOT NULL, " +
                "INSERT_DATE numeric, " +
                "UNIQUE(TRADE_DATE, JSON_OBJECT, STOCK_ID) ON CONFLICT ABORT," +
                "FOREIGN KEY (STOCK_ID) REFERENCES STOCK_QUOTES(_ID));");
    }

    @Override
    public void onUpgrade (SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL ("DROP TABLE IF EXISTS STOCK_QUOTES");
        db.execSQL ("DROP TABLE IF EXISTS HIST_STOCK_QUOTES");
        onCreate (db);
    }

    protected long insert (String code) {

        ContentValues values = new ContentValues ();
        //values.put ("STOCK_EXCHANGE_CODE", codes[1]);
        values.put ("STOCK_CODE", code);
        values.put ("SUSPEND_NOTIFICATION", 0);
        SQLiteDatabase sqLiteDatabase = this.getWritableDatabase ();
        sqLiteDatabase.beginTransaction ();
        long rowID = sqLiteDatabase.insert ("STOCK_QUOTES", null, values);
        sqLiteDatabase.setTransactionSuccessful ();
        sqLiteDatabase.endTransaction ();
        return rowID;
    }

    protected void updateLiveData (String stockCode, String jsonData, int hashedName) {

        ContentValues values = new ContentValues ();
        values.put ("LIVE_DATA", jsonData);
        values.put ("HASHED_NAME", hashedName);
        SQLiteDatabase sqLiteDatabase = this.getWritableDatabase ();
        sqLiteDatabase.beginTransaction ();
        sqLiteDatabase.update ("STOCK_QUOTES", values, "STOCK_CODE = \'" + stockCode + "\'", null);
        sqLiteDatabase.setTransactionSuccessful ();
        sqLiteDatabase.endTransaction ();

    }

    protected void updateSuspendNotification (int stockNameHash, int isSuspended) {

        ContentValues values = new ContentValues ();
        values.put ("SUSPEND_NOTIFICATION", isSuspended);
        SQLiteDatabase sqLiteDatabase = this.getWritableDatabase ();
        sqLiteDatabase.beginTransaction ();
        sqLiteDatabase.update ("STOCK_QUOTES", values, "HASHED_NAME =" + stockNameHash, null);
        sqLiteDatabase.setTransactionSuccessful ();
        sqLiteDatabase.endTransaction ();

    }

    protected void insertHistoricals (String... histData) {

        long dateInMillis = Long.parseLong (histData[1].replace (":", ""));

        DateTime theDate = new DateTime (dateInMillis * 1000l);

        if (theDate.toLocalDate ().isEqual (new DateTime ().toLocalDate ())) {
            return;
        }

        try {

            JSONObject jsonObject = new JSONObject (histData[0]);

            if ("null".equals (jsonObject.getString ("close"))) {
                return;
            }

        } catch (JSONException e) {
            e.printStackTrace ();
            return;
        }


        ContentValues values = new ContentValues ();
        values.put ("STOCK_ID", histData[2].replace (":", ""));
        values.put ("TRADE_DATE", histData[1].replace (":", ""));
        values.put ("JSON_OBJECT", histData[0]);
        values.put ("INSERT_DATE", new DateTime ().getMillis ());
        SQLiteDatabase sqLiteDatabase = this.getWritableDatabase ();
        sqLiteDatabase.beginTransaction ();
        sqLiteDatabase.insert ("HIST_STOCK_QUOTES", null, values);
        sqLiteDatabase.setTransactionSuccessful ();
        sqLiteDatabase.endTransaction ();

    }


    protected Cursor readHistoricalStockData (String id, int numberOfDays) {
        SQLiteDatabase readableDatabase = this.getReadableDatabase ();
        StringBuilder sb = new StringBuilder ();
        sb.append ("SELECT * FROM HIST_STOCK_QUOTES WHERE STOCK_ID IN ");
        sb.append ("(SELECT _ID FROM STOCK_QUOTES WHERE STOCK_CODE = '");
        sb.append (id);
        sb.append ("')");
        sb.append (" ORDER BY TRADE_DATE DESC LIMIT ");
        sb.append (numberOfDays);
        return readableDatabase.rawQuery (sb.toString (), null);
    }

    protected Cursor getRSIData (String stock_name) {
        SQLiteDatabase readableDatabase = this.getReadableDatabase ();
        return readableDatabase.rawQuery ("SELECT TRADE_DATE, JSON_OBJECT FROM HIST_STOCK_QUOTES " +
                "WHERE STOCK_ID in(select _id from STOCK_QUOTES WHERE STOCK_CODE ='" + stock_name + "')" +
                " ORDER BY TRADE_DATE ASC ", null);
    }

    protected Cursor readStockCodes () {
        SQLiteDatabase readableDatabase = this.getReadableDatabase ();
        return readableDatabase.rawQuery ("SELECT * FROM STOCK_QUOTES ORDER BY STOCK_CODE ASC", null);
    }

    protected long getIdFromStockCode (String code) {
        SQLiteDatabase readableDatabase = this.getReadableDatabase ();
        Cursor cursor = readableDatabase.rawQuery ("SELECT HASHED_NAME FROM STOCK_QUOTES where STOCK_CODE = \'" + code + "\'", null);

        long id = 0l;

        while (cursor.moveToNext ()) {
            id = cursor.getLong (0);
        }
        return id;
    }

    protected Cursor readSuspendedNotification (String stockNameHash) {
        SQLiteDatabase readableDatabase = this.getReadableDatabase ();
        return readableDatabase.rawQuery ("SELECT SUSPEND_NOTIFICATION FROM STOCK_QUOTES WHERE HASHED_NAME =" + stockNameHash, null);
    }

    protected Cursor readSuspendedNotificationById (String stockId) {
        SQLiteDatabase readableDatabase = this.getReadableDatabase ();
        return readableDatabase.rawQuery ("SELECT SUSPEND_NOTIFICATION FROM STOCK_QUOTES WHERE _ID=" + stockId, null);
    }

/*
    protected void removeHistStockDataFromDB () {
        SQLiteDatabase writableDatabase = this.getWritableDatabase ();
        writableDatabase.beginTransaction ();
        writableDatabase.execSQL ("DELETE FROM HIST_STOCK_QUOTES");
        writableDatabase.setTransactionSuccessful ();
        writableDatabase.endTransaction ();
    }*/

    protected void removeStockFromDB (String stockCode) {
        SQLiteDatabase writableDatabase = this.getWritableDatabase ();
        writableDatabase.beginTransaction ();
        writableDatabase.execSQL ("delete from STOCK_QUOTES where STOCK_CODE = '" + stockCode + "'");
        writableDatabase.execSQL ("delete from HIST_STOCK_QUOTES where STOCK_ID in (select _ID from STOCK_QUOTES where STOCK_CODE = '" + stockCode + "')");
        writableDatabase.setTransactionSuccessful ();
        writableDatabase.endTransaction ();
    }

    protected boolean isNotifySuspended (int stockNameHash) {
        Cursor cursor = this.readSuspendedNotification (String.valueOf (stockNameHash));

        int isSuspended = 0;

        while (cursor.moveToNext ()) {
            isSuspended = cursor.getInt (0);
        }

        return isSuspended == 1;
    }
}

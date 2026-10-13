package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MainActivity extends ComponentActivity {

    private static final String TAG = "PhoneActivity";
    private static final String NOTIFICATION_CHANNEL_ID = "stocks";
    private static final String PREFS_NAME = "main_activity";
    private static final String PREF_RATIONALE_SHOWN = "notification_rationale_shown";
    StockListDB stockListDB;
    StockListView listView;
    private AdView mAdView;
    private String[] stockRequestCodes;
    private Handler pollQuotes = new Handler (Looper.getMainLooper ());
    private NotificationManagerCompat notificationManager;
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor ();
    // refreshInFlight, refreshPending, destroyed, dataVersionTracker and the work-info fields are main-thread only
    private final DataVersionTracker dataVersionTracker = new DataVersionTracker ();
    private boolean refreshInFlight;
    private boolean refreshPending;
    private boolean destroyed;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult (new ActivityResultContracts.RequestPermission (), granted -> {
                if (!granted)
                    Log.i (TAG, "Notification permission denied");
            });
    private Runnable pollQuotesRunnable = new Runnable () {
        @Override
        public void run () {

            // the work-info observer refreshes the list when this work has written new data
            QuoteRefreshWorker.enqueueNow (getApplicationContext ());

            pollQuotes.postDelayed (this, 60000);
        }
    };
    private LiveData<List<WorkInfo>> refreshWorkInfos;
    private LiveData<List<WorkInfo>> periodicWorkInfos;
    private LiveData<List<WorkInfo>> historyWorkInfos;
    private AlertDialog rationaleDialog;
    private final Observer<List<WorkInfo>> refreshObserver = infos -> refreshIfStale ();

    private void refreshIfStale () {
        if (dataVersionTracker.isStale (stockListDB.getDataVersion ()))
            refreshList ();
    }

    private void refreshList () {
        if (destroyed)
            return;
        if (refreshInFlight) {
            refreshPending = true;
            return;
        }
        refreshInFlight = true;
        // capture before reading so a write committed during the read still marks the list stale
        dataVersionTracker.onLoadStarted (stockListDB.getDataVersion ());
        try {
            refreshExecutor.execute (() -> {
                ArrayList<HashMap<String, String>> quotes = null;
                try {
                    quotes = getQuotes ();
                } catch (RuntimeException e) {
                    Log.w (TAG, "Failed to read quotes", e);
                }
                final ArrayList<HashMap<String, String>> result = quotes;
                pollQuotes.post (() -> onQuotesLoaded (result));
            });
        } catch (RejectedExecutionException e) {
            refreshInFlight = false;
        }
    }

    private void onQuotesLoaded (ArrayList<HashMap<String, String>> quotes) {
        if (destroyed || isFinishing ())
            return;
        if (quotes != null) {
            listView.getAdapter ().clear ();
            listView.getAdapter ().addAll (quotes);
            listView.populateListView ();
        } else {
            // next work-state emission retries; no direct refresh to avoid a loop
            dataVersionTracker.reset ();
        }
        refreshInFlight = false;
        if (refreshPending) {
            refreshPending = false;
            refreshList ();
        }
    }

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);

        stockListDB = StockListDB.getInstance (getApplicationContext ());

        QuoteRefreshWorker.schedulePeriodic (getApplicationContext ());
        refreshExecutor.execute (this::enqueueHistoricalData);

        setContentView (R.layout.activity_main);
        mAdView = (AdView) findViewById (R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        // Issue the notification
        notificationManager =
                NotificationManagerCompat.from (this);
        notificationManager.createNotificationChannel (
                new NotificationChannelCompat.Builder (NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                        .setName (getString (R.string.notification_channel_name))
                        .build ());
        if (savedInstanceState == null)
            requestNotificationPermissionIfNeeded ();

        listView = (StockListView) findViewById (R.id.list);
        listView.isOnItemClickable (true);

        listView.setOnItemClickListener (new AdapterView.OnItemClickListener () {
            @Override
            public void onItemClick (AdapterView<?> parent, View view, int position, long id) {
                HashMap<String, String> item = listView.getAdapter ().getItem (position);
                if (item == null)
                    return;
                final String name = item.get ("NAME");
                final String symbol = item.get ("SYMBOL");
                final String rsi = item.get ("RSI");
                if (name == null || symbol == null)
                    return;
                try {
                    refreshExecutor.execute (() -> {
                        boolean ready = false;
                        try (Cursor cursor = stockListDB.readHistoricalStockData (symbol, 1)) {
                            ready = cursor.moveToNext ();
                        } catch (RuntimeException e) {
                            Log.w (TAG, "Failed to read historical data for " + symbol, e);
                        }
                        final boolean hasHistory = ready;
                        pollQuotes.post (() -> onHistoryChecked (name, symbol, rsi, hasHistory));
                    });
                } catch (RejectedExecutionException ignored) {
                }
            }
        });
        if (listView != null)
            listView.setOnItemLongClickListener (new AdapterView.OnItemLongClickListener () {
                @Override
                public boolean onItemLongClick (AdapterView<?> parent, View view, int position, long id) {


                   /* HashMap<String, String> items = (HashMap) parent.getItemAtPosition (position);
                    String stockName = items.get ("NAME");

                    StringBuilder sb = new StringBuilder (stockName);

                    if (stockListDB.isNotifySuspended (stockName.hashCode ())) {

                        stockListDB.updateSuspendNotification (stockName.hashCode (), 0);
                        view.setAlpha (1f);
                        sb.append (" has enabled notifications.");

                    } else {
                        stockListDB.updateSuspendNotification (stockName.hashCode (), 1);
                        Drawable backGround = view.getBackground ();
                        view.setAlpha (0.5f);
                        sb.append (" has suspended notifications.");

                    }

                    Toast.makeText (getApplicationContext (),
                            sb.toString (), Toast.LENGTH_LONG).show ();


                    return true;*/
                    return false;
                }
            });

        ImageButton refreshButton = (ImageButton) findViewById (R.id.refresh_button);
        if (refreshButton != null) {
            refreshButton.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View view) {
                    refreshList();
                }
                                              }
            );
        }
        final EditText txtUrl = new EditText (this);
        final ImageButton add_stock_button = (ImageButton) findViewById (R.id.add_stock_button);
        if (add_stock_button != null) {
            add_stock_button.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View view) {

                    startActivity (new Intent (MainActivity.this, AddStockActivity.class));
                }
            });
        }

    }

    private void onHistoryChecked (String name, String symbol, String rsi, boolean hasHistory) {
        if (destroyed || isFinishing () || !getLifecycle ().getCurrentState ().isAtLeast (Lifecycle.State.RESUMED))
            return;
        if (!hasHistory) {
            String msg = String.format (Locale.US, "%s HISTORICAL DATA NOT READY YET", name.toUpperCase (Locale.ROOT));
            Toast.makeText (MainActivity.this, msg, Toast.LENGTH_LONG).show ();
            return;
        }
        Intent graphIntent = new Intent (MainActivity.this, GraphActivity.class);
        graphIntent.addFlags (Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        graphIntent.putExtra ("STOCK_NAME", name);
        graphIntent.putExtra ("STOCK_CODE", symbol);
        graphIntent.putExtra ("RSI", rsi);

        startActivity (graphIntent);
    }

    private void requestNotificationPermissionIfNeeded () {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission (this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            return;
        if (!shouldShowRequestPermissionRationale (Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermissionLauncher.launch (Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        SharedPreferences prefs = getSharedPreferences (PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean (PREF_RATIONALE_SHOWN, false))
            return;
        prefs.edit ().putBoolean (PREF_RATIONALE_SHOWN, true).apply ();
        rationaleDialog = new AlertDialog.Builder (this)
                .setTitle (R.string.notification_rationale_title)
                .setMessage (R.string.notification_rationale_message)
                .setPositiveButton (R.string.notification_rationale_allow,
                        (dialog, which) -> notificationPermissionLauncher.launch (Manifest.permission.POST_NOTIFICATIONS))
                .setNegativeButton (R.string.notification_rationale_not_now, (dialog, which) -> dialog.dismiss ())
                .show ();
    }

    private void enqueueHistoricalData () {
        try (Cursor cursor = StockListDB.getInstance (getApplicationContext ()).readStockCodes ()) {
            while (cursor.moveToNext ()) {
                HistoricalDataWorker.enqueue (getApplicationContext (), cursor.getString (1));
            }
        }
    }


    @Override
    protected void onResume () {
        super.onResume ();
        refreshList ();
        WorkManager workManager = WorkManager.getInstance (getApplicationContext ());
        refreshWorkInfos = workManager.getWorkInfosForUniqueWorkLiveData (QuoteRefreshWorker.ONE_TIME_WORK_NAME);
        refreshWorkInfos.observeForever (refreshObserver);
        periodicWorkInfos = workManager.getWorkInfosForUniqueWorkLiveData (QuoteRefreshWorker.PERIODIC_WORK_NAME);
        periodicWorkInfos.observeForever (refreshObserver);
        historyWorkInfos = workManager.getWorkInfosByTagLiveData (HistoricalDataWorker.WORK_TAG);
        historyWorkInfos.observeForever (refreshObserver);
        pollQuotes.removeCallbacks (pollQuotesRunnable);
        pollQuotes.postDelayed (pollQuotesRunnable, 1000);
    }

    @Override
    protected void onPause () {
        pollQuotes.removeCallbacks (pollQuotesRunnable);
        if (refreshWorkInfos != null) {
            refreshWorkInfos.removeObserver (refreshObserver);
            refreshWorkInfos = null;
        }
        if (periodicWorkInfos != null) {
            periodicWorkInfos.removeObserver (refreshObserver);
            periodicWorkInfos = null;
        }
        if (historyWorkInfos != null) {
            historyWorkInfos.removeObserver (refreshObserver);
            historyWorkInfos = null;
        }
        super.onPause ();
    }

    @Override
    protected void onDestroy () {
        destroyed = true;
        if (rationaleDialog != null && rationaleDialog.isShowing ())
            rationaleDialog.dismiss ();
        rationaleDialog = null;
        pollQuotes.removeCallbacksAndMessages (null);
        refreshExecutor.shutdownNow ();
        super.onDestroy ();
    }

    private ArrayList<HashMap<String, String>> getQuotes () {

        ArrayList<HashMap<String, String>> theArrayList = new ArrayList<> ();
        StockListDB db = StockListDB.getInstance (getApplicationContext ());

        try (Cursor cursor = db.readStockCodes ()) {
            while (cursor.moveToNext ()) {

                HashMap<String, String> theMap = new HashMap<String, String> ();

                String stockCode = cursor.getString (1);
                String jsonData = cursor.getString (4);

                if (jsonData == null || "null".equalsIgnoreCase (jsonData)) {
                    theArrayList.add (waitingRow (stockCode));
                    continue;
                }

                try {
                    JSONObject jsonQueryObj = new JSONObject (jsonData);
                    String symbol = jsonQueryObj.getString ("symbol");
                    double ltpValue = readNumber (jsonQueryObj, "regularMarketPrice");
                    // raw percent is a fraction in Yahoo's price module, so prefer the formatted value
                    JSONObject pctObj = jsonQueryObj.optJSONObject ("regularMarketChangePercent");
                    double pct;
                    if (pctObj == null)
                        pct = readNumber (jsonQueryObj, "regularMarketChangePercent");
                    else
                        pct = pctObj.has ("fmt") ? parseNumber (pctObj.getString ("fmt")) : pctObj.getDouble ("raw") * 100;

                    String ltp = String.format (Locale.US, "%.2f", ltpValue);
                    String deltaRatePercent = String.format (Locale.US, "%.2f", pct);
                    String name = jsonQueryObj.optString ("longName", symbol);
                    String change = String.format (Locale.US, "%.2f", readNumber (jsonQueryObj, "regularMarketChange"));

                    long lastTradeTime = (long) readNumber (jsonQueryObj, "regularMarketTime");
                    DateTimeFormatter fmt = DateTimeFormat.forPattern ("HH:mm");
                    LocalTime lastUpdate = new LocalDateTime (lastTradeTime * 1000).toLocalTime ();

                    theMap.put ("NAME", name);
                    theMap.put ("LTP", ltp);
                    theMap.put ("LTT", fmt.print (lastUpdate));
                    theMap.put ("CHANGE", change);
                    theMap.put ("CHANGE_PCT", deltaRatePercent + "%");
                    theMap.put ("RSI", new RSI (14, symbol, db, ltpValue).calculate ());
                    theMap.put ("SYMBOL", symbol);

                    theArrayList.add (theMap);

                  /*  if (deltaRatePercent != null || !deltaRatePercent.equals ("null")) {
                        pushNotification (symbol, name, deltaRatePercent);
                    }*/

                } catch (JSONException | RuntimeException e) {
                    Log.w (TAG, "Unparsable quote for " + stockCode, e);
                    theArrayList.add (waitingRow (stockCode));
                }
            }
        }

        return theArrayList;
    }

    private static HashMap<String, String> waitingRow (String stockCode) {
        HashMap<String, String> theMap = new HashMap<> ();
        theMap.put ("NAME", stockCode + " waiting for data");
        theMap.put ("LTP", "");
        theMap.put ("LTT", "");
        theMap.put ("CHANGE", "");
        theMap.put ("CHANGE_PCT", "");
        theMap.put ("RSI", "");
        theMap.put ("SYMBOL", stockCode == null ? "" : stockCode);
        return theMap;
    }

    private static double readNumber (JSONObject parent, String key) throws JSONException {
        Object value = parent.get (key);
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if (obj.has ("raw"))
                return obj.getDouble ("raw");
            value = obj.getString ("fmt");
        }
        if (value instanceof Number)
            return ((Number) value).doubleValue ();
        return parseNumber (value.toString ());
    }

    private static double parseNumber (String text) {
        return Double.parseDouble (text.replace (",", "").replace ("%", "").trim ());
    }


    @Override
    public void onStart () {
        super.onStart ();


    }

    @Override
    public void onStop () {
        super.onStop ();// ATTENTION: This was auto-generated to implement the App Indexing API.


        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.

    }

    @Override
    protected void onSaveInstanceState (Bundle bundle) {

        //bundle.putStringArrayList ("THE_LIST", listView.getStocksList ());
        super.onSaveInstanceState (bundle);
    }

    @Override
    public void onRestoreInstanceState (Bundle bundle) {
        super.onRestoreInstanceState (bundle);
        //   listView.setViewListData (bundle.getStringArrayList ("THE_LIST"));
        // listView.populateListView ();
    }

    private void pushNotification (String stockCode, String stockName, String changePct) {

        Intent resultIntent = new Intent (this, MainActivity.class);

        PendingIntent resultPendingIntent =
                PendingIntent.getActivity (
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

        Bitmap icon = BitmapFactory.decodeResource (getResources (), R.mipmap.stockmarket);

        Notification notif = new NotificationCompat.Builder (getApplicationContext (), NOTIFICATION_CHANNEL_ID)
                .setContentTitle (stockName)
                .setContentText ("has changed " + changePct)
                .setLargeIcon (icon)
                .setSmallIcon (R.mipmap.stockmarket)
                .setGroup ("STOCKS")
                .setGroupSummary (true)
                .setVibrate (new long[]{1, 300, 2000})
                .setContentIntent (resultPendingIntent)
                .build ();


        // Sets an ID for the notification
        int notificationId = stockName.hashCode ();

        String change = changePct.replaceAll ("[%+]", "");
        float theChange = Float.parseFloat (change);

        if (!notificationManager.areNotificationsEnabled ()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission (this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED))
            return;

        if (theChange > 2.0 || theChange < -2.0) {
            if (!stockListDB.isNotifySuspended (notificationId)) {
                notificationManager.notify (notificationId, notif);
            }
        }
    }

}

package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.ads.AdView;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String NOTIFICATION_PATH = "/notification";
    private static final String NOTIFICATION_CONTENT = "content";
    private static final String TAG = "PhoneActivity";
    StockListDB stockListDB;
    StockListView listView;
    private AdView mAdView;
    private String[] stockRequestCodes;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private Handler handler = new Handler ();
    private Handler pollQuotes = new Handler ();
    private String nodeId;
    private NotificationManagerCompat notificationManager;
    private Runnable pollQuotesRunnable = new Runnable () {
        @Override
        public void run () {


            refreshList ();

            pollQuotes.postDelayed (this, 30000);
        }
    };

    private void refreshList () {

        listView.getAdapter ().clear ();

        listView.getAdapter ().addAll (getQuotes ());
        //listView.getAdapter ();
        listView.populateListView ();

    }

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);

        stockListDB = StockListDB.getInstance (getApplicationContext ());

        Toast.makeText (this, "starting service", Toast.LENGTH_LONG).show ();
        Intent theServiceIntent = new Intent (MainActivity.this, BackgroundListenerService.class);
        startService (theServiceIntent);

        setContentView (R.layout.activity_main);
       /* mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);*/

        // Issue the notification
        notificationManager =
                NotificationManagerCompat.from (this);

        listView = (StockListView) findViewById (R.id.list);
        listView.isOnItemClickable (true);

        listView.setOnItemClickListener (new AdapterView.OnItemClickListener () {
            @Override
            public void onItemClick (AdapterView<?> parent, View view, int position, long id) {
                try {
                    String name = listView.getAdapter ().getItem (position).get ("NAME");
                    String symbol = listView.getAdapter ().getItem (position).get ("SYMBOL");
                    Cursor cursor = stockListDB.readHistoricalStockData (symbol, 1);

                    if (!cursor.moveToNext ()) {
                        String msg = String.format ("%s HISTORICAL DATA NOT READY YET", name.toUpperCase ());
                        Toast.makeText (MainActivity.this, msg, Toast.LENGTH_LONG).show ();
                        return;
                    }
                    Intent graphIntent = new Intent (MainActivity.this, GraphActivity.class);
                    graphIntent.addFlags (Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    graphIntent.putExtra ("STOCK_NAME", listView.getAdapter ().getItem (position).get ("NAME"));
                    graphIntent.putExtra ("STOCK_CODE", symbol);
                    graphIntent.putExtra ("RSI", listView.getAdapter ().getItem (position).get ("RSI"));

                    startActivity (graphIntent);
                } catch (Exception e) {
                    e.printStackTrace ();
                }
            }
        });
        if (listView != null)
            listView.setOnItemLongClickListener (new AdapterView.OnItemLongClickListener () {
                @Override
                public boolean onItemLongClick (AdapterView<?> parent, View view, int position, long id) {


                    HashMap<String, String> items = (HashMap) parent.getItemAtPosition (position);
                    String stockName = items.get ("NAME");

                    StringBuilder sb = new StringBuilder (stockName);

                    if (stockListDB.isNotifySuspended (stockName.hashCode ())) {

                        stockListDB.updateSuspendNotification (stockName.hashCode (), 0);
                        view.getBackground ().setAlpha (255);
                        sb.append (" has enabled notifications.");

                    } else {
                        stockListDB.updateSuspendNotification (stockName.hashCode (), 1);
                        view.getBackground ().setAlpha (150);
                        sb.append (" has suspended notifications.");

                    }

                    Toast.makeText (getApplicationContext (),
                            sb.toString (), Toast.LENGTH_LONG).show ();


                    return true;
                }
            });

        refreshList ();
        ImageButton refreshButton = (ImageButton) findViewById (R.id.refresh_button);
        if (refreshButton != null) {
            refreshButton.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View view) {
                    /*MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage (
                            client, "ce7d7706", "SEND_DATA", "AllanwasHere".getBytes ()).await ();
                */
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

       /* client = new GoogleApiClient.Builder (this)
                .addApi (Wearable.API)
                .addConnectionCallbacks (this)
                .addOnConnectionFailedListener (this)
                .build ();
*/
        pollQuotes.postDelayed (pollQuotesRunnable, 1000);

    }


    @Override
    protected void onResume () {
        super.onResume ();

        refreshList ();
    }

    @Override
    protected void onPause () {


        super.onPause ();
    }

    private ArrayList<HashMap<String, String>> getQuotes () {

        ArrayList<HashMap<String, String>> theArrayList = new ArrayList<> ();
        Cursor cursor = StockListDB.getInstance (getApplicationContext ()).readStockCodes ();
        while (cursor.moveToNext ()) {

            HashMap<String, String> theMap = new HashMap<String, String> ();

            String jsonData = cursor.getString (4);

            if (jsonData == null || "null".equalsIgnoreCase (jsonData))
                continue;

            try {
                JSONObject jsonQueryObj = new JSONObject (jsonData);
                String symbol = jsonQueryObj.getString ("symbol");
                String ltp = jsonQueryObj.getJSONObject ("regularMarketPrice").getString ("fmt").replace (",", "");
                String tempDelta = jsonQueryObj.getJSONObject ("regularMarketChangePercent").getString ("fmt");

                String deltaRatePercent = String.format (Locale.US, "%.2f", Double.parseDouble (tempDelta.replace ("%", "")));
                String name = jsonQueryObj.getString ("longName");
                String change = String.format ("%.2f", Double.parseDouble (jsonQueryObj.getJSONObject ("regularMarketChange").getString ("fmt")));

                String lastTradeTime = jsonQueryObj.getString ("regularMarketTime");
                DateTimeFormatter fmt = DateTimeFormat.forPattern ("HH:mm");
                LocalTime lastUpdate = new LocalDateTime (new Long (lastTradeTime) * 1000).toLocalTime ();

                theMap.put ("NAME", name);
                theMap.put ("LTP", ltp);
                theMap.put ("LTT", fmt.print (lastUpdate));
                theMap.put ("CHANGE", change);
                theMap.put ("CHANGE_PCT", deltaRatePercent + "%");
                theMap.put ("RSI", new RSI (14, symbol, stockListDB, ltp).calculate ());
                theMap.put ("SYMBOL", jsonQueryObj.getString ("symbol"));

                theArrayList.add (theMap);

                if (deltaRatePercent != null || !deltaRatePercent.equals ("null")) {
                    pushNotification (symbol, name, deltaRatePercent);
                }

            } catch (JSONException e) {
                e.printStackTrace ();
                break;
            }

        }
        DateTimeFormatter fmt = DateTimeFormat.forPattern ("HH:mm:ss");
        LocalTime lastUpdate = new DateTime ().toLocalTime ();

        // ((TextView) findViewById (R.id.lastUpdated)).setText ("Last update: \n" + fmt.print (lastUpdate));


        return theArrayList;
    }


    @Override
    public void onStart () {
        super.onStart ();
        // client.connect ();


    }

    @Override
    public void onStop () {
        super.onStop ();// ATTENTION: This was auto-generated to implement the App Indexing API.


        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.

    }
/*

    @Override
    public void onMessageReceived (MessageEvent messageEvent) {

        */
/*String theTxt =  new String(messageEvent.getData());

        nodeId = messageEvent.getSourceNodeId();

*//*

          Toast.makeText (MainActivity.this, "hello from Mobile", Toast.LENGTH_SHORT).show ();

    }
*/


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
/*

    @Override
    public void onDataChanged (DataEventBuffer dataEvents) {
*/
/*

        try {
            for (DataEvent event : dataEvents) {
                if (event.getType () == DataEvent.TYPE_CHANGED) {
                    String path = event.getDataItem ().getUri ().getPath ();

                    DataMapItem dataMapItem = DataMapItem.fromDataItem (event.getDataItem ());
                    ArrayList<String> quotesList = dataMapItem.getDataMap ()
                            .getStringArrayList ("content");

                    listView.getAdapter ().clear ();
                    int index = 0;
                    for (String quote : quotesList) {

                        //double rate =  Double.parseDouble (quoteChunks[1]);

                        listView.getAdapter ().add (quote)

                        ;


                    }
                    listView.populateListView ();


                }
            }


        } catch (Exception e) {
            e.printStackTrace ();
        }
*//*

    }
*/

/*

    @Override
    public void onConnected (Bundle connectionHint) {
        Wearable.DataApi.addListener (client, this);
        Wearable.MessageApi.addListener (client, this);
        new Thread (new Runnable () {
            @Override
            public void run () {
                NodeApi.GetLocalNodeResult nodes = Wearable.NodeApi.getLocalNode (client).await ();

                System.out.println ("LOCALNODE_main: " + nodes.getNode ().getId ());
            }
        }).start ();

    }
*/

    private void pushNotification (String stockCode, String stockName, String changePct) {

        Intent resultIntent = new Intent (this, MainActivity.class);

        PendingIntent resultPendingIntent =
                PendingIntent.getActivity (
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        Bitmap icon = BitmapFactory.decodeResource (getResources (), R.mipmap.stockmarket);

        Notification notif = new NotificationCompat.Builder (getApplicationContext ())
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

        if (theChange > 2.0 || theChange < -2.0) {
            if (!stockListDB.isNotifySuspended (notificationId)) {
                notificationManager.notify (notificationId, notif);
            }
        }
    }

}

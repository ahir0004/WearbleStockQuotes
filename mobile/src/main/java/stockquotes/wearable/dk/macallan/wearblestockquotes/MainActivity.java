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
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        DataApi.DataListener,
        MessageApi.MessageListener {

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
    private GoogleApiClient client;
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
        /*mAdView = (AdView) findViewById(R.id.adView);
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
                    Intent graphIntent = new Intent (MainActivity.this, GraphActivity.class);
                    graphIntent.addFlags (Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    graphIntent.putExtra ("STOCK_NAME", listView.getAdapter ().getItem (position).get ("NAME"));
                    graphIntent.putExtra ("STOCK_CODE", listView.getAdapter ().getItem (position).get ("SYMBOL"));
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
        Button refreshButton = (Button) findViewById (R.id.refresh_button);
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

        client = new GoogleApiClient.Builder (this)
                .addApi (Wearable.API)
                .addConnectionCallbacks (this)
                .addOnConnectionFailedListener (this)
                .build ();

        pollQuotes.postDelayed (pollQuotesRunnable, 1000);

    }


    @Override
    protected void onResume () {
        super.onResume ();
        client.connect ();
        if (client.isConnected ()) {
            Toast.makeText (this, "OnResume", Toast.LENGTH_LONG);
        }
        refreshList ();
    }

    @Override
    protected void onPause () {
        if ((client != null) && client.isConnected ()) {
            Wearable.DataApi.removeListener (client, this);
            Wearable.MessageApi.removeListener (client, this);
            client.disconnect ();
        }

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
                String deltaRatePercent = jsonQueryObj.getJSONObject ("regularMarketChangePercent").getString ("fmt");
                String name = jsonQueryObj.getString ("longName");

                String lastTradeTime = jsonQueryObj.getString ("regularMarketTime");
                DateTimeFormatter fmt = DateTimeFormat.forPattern ("HH:mm");
                LocalTime lastUpdate = new LocalDateTime (new Long (lastTradeTime) * 1000).toLocalTime ();

                theMap.put ("NAME", name);
                theMap.put ("LTP", ltp);
                theMap.put ("LTT", fmt.print (lastUpdate));
                theMap.put ("CHANGE", jsonQueryObj.getJSONObject ("regularMarketChange").getString ("fmt"));
                theMap.put ("CHANGE_PCT", deltaRatePercent);
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


    private void sendNotification () {


        if (client.isConnected ()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create (NOTIFICATION_PATH);
            // Make sure the data item is unique. Usually, this will not be required, as the payload
            // (in this case the title and the content of the notification) will be different for almost all
            // situations. However, in this example, the text and the content are always the same, so we need
            // to disambiguate the data item by adding a field that contains teh current time in milliseconds.

            //  dataMapRequest.getDataMap ().putStringArrayList (NOTIFICATION_CONTENT, listView.getStocksList ());
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest ();

            Wearable.DataApi.putDataItem (client, putDataRequest);
        } else {
            Log.e (TAG, "No connection to wearable available!");
        }

    }

    @Override
    public void onStart () {
        super.onStart ();
        client.connect ();


    }

    @Override
    public void onStop () {
        super.onStop ();// ATTENTION: This was auto-generated to implement the App Indexing API.


        client.disconnect ();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.

    }

    @Override
    public void onMessageReceived (MessageEvent messageEvent) {

        /*String theTxt =  new String(messageEvent.getData());

        nodeId = messageEvent.getSourceNodeId();


          Toast.makeText (MainActivity.this, "hello from Mobile", Toast.LENGTH_SHORT).show ();
        sendNotification ();*/

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

    @Override
    public void onDataChanged (DataEventBuffer dataEvents) {
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
*/
    }


    @Override
    public void onConnected (Bundle connectionHint) {
        Wearable.DataApi.addListener (client, this);
        Wearable.MessageApi.addListener (client, this);
        new Thread (new Runnable () {
            @Override
            public void run () {
                NodeApi.GetLocalNodeResult nodes = Wearable.NodeApi.getLocalNode (client).await ();

                System.out.println ("LOCALNODE: " + nodes.getNode ().getId ());
            }
        }).start ();

    }

    @Override
    public void onConnectionSuspended (int i) {

    }

    @Override
    public void onConnectionFailed (@NonNull ConnectionResult connectionResult) {

    }

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

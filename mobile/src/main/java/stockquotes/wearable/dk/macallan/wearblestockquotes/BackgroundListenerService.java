package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Notification;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.google.android.gms.wearable.DataMap.TAG;

public class BackgroundListenerService extends WearableListenerService {

    public static final String NOTIFICATION_PATH = "/notification";
    private static final String SEND_MORE_MONEY_PATH = "SEND_MORE_MONEY";
    private static final String NOTIFICATION_CONTENT = "content";
    ArrayList<String> quotesArrayList = new ArrayList<String> ();
    Handler pollQuotesHandler;
    private int counter = 0;
    private GoogleApiClient client;
    private String nodeId;
    private List<Node> nodes;
    private StockListDB stockListDB;
    private NotificationManagerCompat notificationManager;
    private Runnable pollQuotesRunnable = new Runnable () {
        @Override
        public void run () {
            executeRequest ();
            pollQuotesHandler.postDelayed (this, 30000);
        }
    };

    private void sendMessage (final String path, final String text) {

        new Thread (new Runnable () {

            @Override

            public void run () {

               /* NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes( client ).await();

                for(Node node : nodes.getNodes()) {
*/
                MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage (

                        client, "ce7d7706", path, "AllanwasHere".getBytes ()).await ();
  /*                  System.out.println ("CONNECTED nODE: " + node.getId ());
                }
*/


            }

        }).start ();
    }

    private void sendNotification () {
//        if (client.isConnected ()) {


        PutDataMapRequest dataMapRequest = PutDataMapRequest.create (NOTIFICATION_PATH);
        // Make sure the data item is unique. Usually, this will not be required, as the payload
        // (in this case the title and the content of the notification) will be different for almost all
        // situations. However, in this example, the text and the content are always the same, so we need
        // to disambiguate the data item by adding a field that contains teh current time in milliseconds.

        dataMapRequest.getDataMap ().putStringArrayList (NOTIFICATION_CONTENT, (ArrayList<String>) getQuotesArrayList ().clone ());
        PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest ();

        Wearable.DataApi.putDataItem (client, putDataRequest);

    }

    private synchronized ArrayList<String> getQuotesArrayList () {
        return quotesArrayList;
    }

    protected String[] getRequestCodes () {
        Cursor cursor = new StockListDB (BackgroundListenerService.this).readStockCodes ();
        StringBuilder stringBuilder = new StringBuilder ();
        ArrayList<String> requestCodesList = new ArrayList<> ();
        while (cursor.moveToNext ()) {

            requestCodesList.add (stringBuilder.append (cursor.getInt (0)).append ("::")
                    .append (cursor.getString (1))
                    .append (":")
                    .append (cursor.getString (2))
                    .toString ());
            stringBuilder.setLength (0);
        }
        return requestCodesList.toArray (new String[requestCodesList.size ()]);

    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {

        client = new GoogleApiClient.Builder (this).addConnectionCallbacks (new GoogleApiClient.ConnectionCallbacks () {
            @Override
            public void onConnected (Bundle connectionHint) {
                Log.d (TAG, "onConnected: " + connectionHint);
                Wearable.MessageApi.addListener (client, BackgroundListenerService.this);
//                retrieveDeviceNode();
                //sendMessage ("path", "BLABLA");
                sendNotification ();
            }

            @Override
            public void onConnectionSuspended (int cause) {
                Log.d (TAG, "onConnectionSuspended: " + cause);
            }
        })
                .addOnConnectionFailedListener (new GoogleApiClient.OnConnectionFailedListener () {
                    @Override
                    public void onConnectionFailed (@NonNull ConnectionResult result) {
                        Log.d (TAG, "onConnectionFailed: " + result);
                    }
                })
                .addApi (Wearable.API)
                .build ();
        client.connect ();
        Toast.makeText (BackgroundListenerService.this, " TOAST_FROM_SERVICE", Toast.LENGTH_LONG);

        return START_STICKY;
    }

    @Override
    public void onCreate () {
        super.onCreate ();
        pollQuotesHandler = new Handler ();
        pollQuotesHandler.postDelayed (pollQuotesRunnable, 1000);
        stockListDB = new StockListDB (getApplicationContext ());

        getHistoricalData ();
        // Issue the notification
        notificationManager =
                NotificationManagerCompat.from (this);
    }

    private void getHistoricalData () {

        for (String stockCode : getRequestCodes ()) {
            HistoricalDataTask hdt = new HistoricalDataTask (getApplicationContext ());
            hdt.execute (stockCode);
        }
    }

    private void pushNotification (int index, String stockName, String changePct) {
        Bitmap icon = BitmapFactory.decodeResource (getResources (), R.mipmap.stockmarket);

        Notification notif = new NotificationCompat.Builder (getApplicationContext ())
                .setContentTitle (stockName)
                .setContentText ("has changed " + changePct)
                .setLargeIcon (icon)
                .setSmallIcon (R.mipmap.stockmarket)
                .setGroup ("STOCKS")
                .setGroupSummary (true)
                .setVibrate (new long[]{1, 300, 2000})
                .build ();

        String stockId = getRequestCodes ()[index - 1].split ("::")[0];

        // Sets an ID for the notification
        int mNotificationId = Integer.valueOf (stockId);
        // Gets an instance of the NotificationManager service
       /* NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService (NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
*/
        if (changePct == null || changePct.equals ("null"))
            return;

        String change = changePct.replaceAll ("[%+]", "");
        float theChange = Float.parseFloat (change);

        if (theChange > 2.0 || theChange < -2.0) {
            if (!stockListDB.isNotifySuspended (mNotificationId)) {
                notificationManager.notify (mNotificationId, notif);
            }
        }
    }

    private Node getNode () {
        HashSet<String> results = new HashSet<String> ();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes (client).await ();
        for (Node node : nodes.getNodes ()) {
            if (node.isNearby ())
                return node;
        }
        return null;
    }

    @Override
    public void onDataChanged (DataEventBuffer dataEvents) {

    }

    @Override
    public void onMessageReceived (MessageEvent messageEvent) {
    }

    private void executeRequest () {
        String[] reqCodes = getStockRequestCodes ();

        quotesArrayList.clear ();

        for (int i = 0; i < reqCodes.length; i++) {
            BackgroundListenerService.DownloadWebPageTask task = new BackgroundListenerService.DownloadWebPageTask ();
            task.execute (reqCodes[i]);
        }
    }

    private String[] getStockRequestCodes () {
        Cursor cursor = new StockListDB (getApplicationContext ()).readStockCodes ();
        ArrayList<String> quotesList = new ArrayList<> ();
        StringBuilder stringBuilder = new StringBuilder ();
        while (cursor.moveToNext ()) {
            stringBuilder.append (cursor.getString (2))
                    .append (".")
                    .append (cursor.getString (1));
            quotesList.add (stringBuilder.toString ());
            stringBuilder.setLength (0);
        }
        return quotesList.toArray (new String[quotesList.size ()]);
    }

    private class DownloadWebPageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground (String... urls) {

            StringBuilder chunks = new StringBuilder ();
            try {

                StringBuilder sb = new StringBuilder ();
                sb.append ("https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20yahoo.finance.quotes%20where%20symbol%20in%20(%22");
                sb.append (urls[0]);
                sb.append ("%22)&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&callback=");

                URL url = new URL (sb.toString ());

                HttpURLConnection conn = (HttpURLConnection) url.openConnection ();
                conn.setRequestMethod ("GET");

                if (conn.getResponseCode () != 200) {
                    throw new RuntimeException ("Failed : HTTP error code : "
                            + conn.getResponseCode () + urls[0]);
                }

                BufferedReader br = new BufferedReader (new InputStreamReader (
                        (conn.getInputStream ())));

                String output;
                System.out.println ("Output from Server .... \n");
                while ((output = br.readLine ()) != null) {
                    chunks.append (output);
                }
                conn.disconnect ();
            } catch (Exception e) {
                e.printStackTrace ();
            }

            return chunks.toString ();
        }

        @Override
        protected void onPostExecute (String json2Parse) {

            JSONObject jsonObj = null;
            try {
                jsonObj = new JSONObject (json2Parse);

                JSONObject jsonQueryObj = jsonObj.getJSONObject ("query").getJSONObject ("results").getJSONObject ("quote");

                String name = jsonQueryObj.getString ("Name");
                String rate = jsonQueryObj.getString ("LastTradePriceOnly");
                String deltaRate = jsonQueryObj.getString ("Change");
                String deltaRatePercent = jsonQueryObj.getString ("PercentChange");

                final String separator = "::";

                // ArrayList<String> listOfQuotes = new ArrayList<> ();
                quotesArrayList.add (new StringBuilder (name)
                        .append (separator)
                        .append (rate)
                        .append (separator)
                        .append (deltaRate)
                        .append (separator)
                        .append (deltaRatePercent)
//                            .append (separator)
//                            .append (time)
                        .toString ());

                pushNotification (quotesArrayList.size (), name, deltaRatePercent);

                if (getStockRequestCodes ().length == quotesArrayList.size ()) {
                    //getQuotesArrayList ().addAll (listOfQuotes);
                    sendNotification ();

                }

            } catch (JSONException e) {
                e.printStackTrace ();
            }
        }
    }


}


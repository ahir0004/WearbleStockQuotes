package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.support.annotation.NonNull;
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

import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

import static com.google.android.gms.wearable.DataMap.TAG;

public class BackgroundListenerService extends WearableListenerService {

    public static final String NOTIFICATION_PATH = "/notification";
    private static final String SEND_MORE_MONEY_PATH = "SEND_MORE_MONEY";
    private static final String NOTIFICATION_CONTENT = "content";
    Handler pollQuotesHandler;
    private GoogleApiClient client;
    private String nodeId;
    private PowerManager.WakeLock wakeLock;


    private Runnable pollQuotesRunnable = new Runnable() {
        @Override
        public void run() {
            executeRequest();
            pollQuotesHandler.postDelayed (this, 60000);
        }
    };

    private void sendMessage(final String path, final String text) {

        new Thread(new Runnable() {

            @Override
            public void run() {

                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes (client).await ();

                for(Node node : nodes.getNodes()) {
                    nodeId = node.getId ();
                }
                MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(

                        client, nodeId, path, "AllanwasHere".getBytes ()).await ();

            }

        }).start();
    }


    protected String[] getRequestCodes() {
        Cursor cursor = StockListDB.getInstance (BackgroundListenerService.this).readStockCodes ();
        StringBuilder stringBuilder = new StringBuilder();
        ArrayList<String> requestCodesList = new ArrayList<>();
        while (cursor.moveToNext()) {

            requestCodesList.add (stringBuilder
                    .append(cursor.getString(1))
                    .toString());
            stringBuilder.setLength(0);
        }
        return requestCodesList.toArray(new String[requestCodesList.size()]);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        PowerManager powerManager = (PowerManager) getSystemService (POWER_SERVICE);
        wakeLock = powerManager.newWakeLock (PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire ();

        getHistoricalData();

        client = new GoogleApiClient.Builder(this).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle connectionHint) {
                Log.d(TAG, "onConnected: " + connectionHint);
                Wearable.MessageApi.addListener(client, BackgroundListenerService.this);
                System.out.println ("BAck_onconnnected");
                sendMessage ("path", "BLABLA");
            }

            @Override
            public void onConnectionSuspended(int cause) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                .addApi(Wearable.API)
                .build();
        client.connect();
        Toast.makeText(BackgroundListenerService.this, " TOAST_FROM_SERVICE", Toast.LENGTH_LONG);

        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pollQuotesHandler = new Handler();
        pollQuotesHandler.postDelayed(pollQuotesRunnable, 1000);
    }

    @Override
    public void onDestroy () {
        if (wakeLock.isHeld ()) {
            wakeLock.release ();
        }
        super.onDestroy ();

        //  sendBroadcast ();
    }



    private void getHistoricalData() {
        for (String stockCode : getRequestCodes()) {
            HistoricalDataTask hdt = new HistoricalDataTask(getApplicationContext());
            if (!"".equals (stockCode))
                hdt.execute (stockCode);
        }
    }

    private Node getNode() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(client).await();
        for (Node node : nodes.getNodes()) {
            if (node.isNearby())
                return node;
        }
        return null;
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        new Thread (new Runnable () {
            @Override
            public void run () {
                if (client.isConnected ()) {
                    PutDataMapRequest dataMapRequest = PutDataMapRequest.create ("/QUOTES");
                    dataMapRequest.getDataMap ().putStringArrayList (NOTIFICATION_CONTENT, getQuotes ());
                    PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest ();

                    Wearable.DataApi.putDataItem (client, putDataRequest);
                } else {
                    Log.e (TAG, "No connection to wearable available!");
                }

            }
        }) {

        }.start ();
    }

    private void executeRequest() {
        String[] reqCodes = getStockRequestCodes();

        for (int i = 0; i < reqCodes.length; i++) {
            BackgroundListenerService.DownloadWebPageTask task = new BackgroundListenerService.DownloadWebPageTask();
            task.execute (reqCodes[i], String.valueOf (i));
        }
    }

    private String[] getStockRequestCodes() {
        Cursor cursor = StockListDB.getInstance (getApplicationContext ()).readStockCodes ();
        ArrayList<String> quotesList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        while (cursor.moveToNext()) {
            stringBuilder.append (cursor.getString (1));
            quotesList.add(stringBuilder.toString());
            stringBuilder.setLength(0);
        }
        return quotesList.toArray(new String[quotesList.size()]);
    }

    private ArrayList<String> getQuotes () {

        ArrayList<String> theArrayList = new ArrayList<> ();
        Cursor cursor = StockListDB.getInstance (getApplicationContext ()).readStockCodes ();
        while (cursor.moveToNext ()) {


            String jsonData = cursor.getString (4);

            if (jsonData == null || "null".equalsIgnoreCase (jsonData))
                continue;

            try {
                JSONObject jsonQueryObj = new JSONObject (jsonData);
                String ltp = jsonQueryObj.getJSONObject ("regularMarketPrice").getString ("fmt").replace (",", "");
                String tempDelta = jsonQueryObj.getJSONObject ("regularMarketChangePercent").getString ("fmt");

                String deltaRatePercent = String.format (Locale.US, "%.2f", Double.parseDouble (tempDelta.replace ("%", "")));
                String name = jsonQueryObj.getString ("longName");
                String change = String.format ("%.2f", Double.parseDouble (jsonQueryObj.getJSONObject ("regularMarketChange").getString ("fmt")));

                String lastTradeTime = jsonQueryObj.getString ("regularMarketTime");
                DateTimeFormatter fmt = DateTimeFormat.forPattern ("HH:mm");
                LocalTime lastUpdate = new LocalDateTime (new Long (lastTradeTime) * 1000).toLocalTime ();

                StringBuilder sb = new StringBuilder ();
                sb.append (name);
                sb.append (System.getProperty ("line.separator"));
                sb.append (ltp);
                sb.append (System.getProperty ("line.separator"));
                sb.append (change);
                sb.append ("/");
                sb.append (deltaRatePercent);
                sb.append ("%");
                sb.append (System.getProperty ("line.separator"));
                sb.append (fmt.print (lastUpdate));
                theArrayList.add (sb.toString ());


            } catch (JSONException e) {
                e.printStackTrace ();
                break;
            }

        }

        return theArrayList;
    }

    private class DownloadWebPageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            StringBuilder chunks = new StringBuilder();

            String htmlScrape = "";

            StringBuilder sb = new StringBuilder ();
            sb.append ("https://finance.yahoo.com/quote/");
            sb.append (urls[0]);
            sb.append ("?p=");
            sb.append (urls[0]);
            sb.append (")");
            try {
                htmlScrape = Jsoup.connect (sb.toString ()).maxBodySize (270000).get ().html ();

            } catch (IOException e) {
                e.printStackTrace ();
            }

            String retVal = "";
            if (htmlScrape.length () > 1) {
                retVal = htmlScrape.split ("\\},\"price\":")[1]
                        .split (",\"financialData\"")[0];
            }

            return retVal;
        }

        @Override
        protected void onPostExecute(String json2Parse) {

            if ("".equals (json2Parse)) {
                return;
            }

            JSONObject jsonObj = null;
            try {

                jsonObj = new JSONObject (json2Parse.split (":::")[0]);
                String stockCode = jsonObj.getString ("symbol");
                String name = jsonObj.getString ("longName");
                StockListDB.getInstance (getApplicationContext ())
                        .updateLiveData (stockCode, jsonObj.toString (), name.hashCode ());


            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }


}


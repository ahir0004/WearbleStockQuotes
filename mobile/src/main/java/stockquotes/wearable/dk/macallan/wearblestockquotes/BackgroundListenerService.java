package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.google.android.gms.wearable.DataMap.TAG;

public class BackgroundListenerService extends WearableListenerService {

    public static final String NOTIFICATION_PATH = "/notification";
    private static final String SEND_MORE_MONEY_PATH = "SEND_MORE_MONEY";
    private static final String NOTIFICATION_CONTENT = "content";
    Handler pollQuotesHandler;
    private List<String> quotesArrayList = Collections.synchronizedList (new ArrayList<String> ());
    private int counter = 0;
    private GoogleApiClient client;
    private String nodeId;
    private List<Node> nodes;
    private StockListDB stockListDB;

    private Runnable pollQuotesRunnable = new Runnable() {
        @Override
        public void run() {
            executeRequest();
            pollQuotesHandler.postDelayed (this, 25000);
        }
    };

    private void sendMessage(final String path, final String text) {

        new Thread(new Runnable() {

            @Override

            public void run() {

               /* NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes( client ).await();

                for(Node node : nodes.getNodes()) {
*/
                MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(

                        client, "ce7d7706", path, "AllanwasHere".getBytes()).await();
  /*                  System.out.println ("CONNECTED nODE: " + node.getId ());
                }
*/


            }

        }).start();
    }

    private void sendNotification () {
//        if (client.isConnected ()) {


        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(NOTIFICATION_PATH);
        // Make sure the data item is unique. Usually, this will not be required, as the payload
        // (in this case the title and the content of the notification) will be different for almost all
        // situations. However, in this example, the text and the content are always the same, so we need
        // to disambiguate the data item by adding a field that contains teh current time in milliseconds.
/*

        JSONObject jsonQueryObj = jsonObj.getJSONObject("query").getJSONObject("results").getJSONObject("quote");

        String name = jsonQueryObj.getString("Name");
        String rate = jsonQueryObj.getString("LastTradePriceOnly");
        String deltaRate = jsonQueryObj.getString("Change");
        String deltaRatePercent = jsonQueryObj.getString("PercentChange");

        getRequestCodes ()
*/
        ArrayList<String> theArrayList = new ArrayList<String> ();
        Cursor cursor = StockListDB.getInstance (getApplicationContext ()).readStockCodes ();

        while (cursor.moveToNext ()) {


            JSONObject jsonQueryObj = null;
            try {
                jsonQueryObj = new JSONObject (cursor.getString (4));

                StringBuilder sb = new StringBuilder ();
                sb.append (jsonQueryObj.getString ("Name"));
                sb.append ("\n");
                sb.append (jsonQueryObj.getString ("LastTradePriceOnly"));
                sb.append ("   ");
                sb.append (jsonQueryObj.getString ("Change"));
                sb.append (" / ");
                sb.append (jsonQueryObj.getString ("PercentChange"));

                theArrayList.add (sb.toString ());

            } catch (JSONException e) {
                e.printStackTrace ();
            }

        }

        dataMapRequest.getDataMap ().putStringArrayList (NOTIFICATION_CONTENT, theArrayList);
        PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();

        Wearable.DataApi.putDataItem(client, putDataRequest);
    }

    private synchronized List<String> getQuotesArrayList () {
        return quotesArrayList;
    }

    protected String[] getRequestCodes() {
        Cursor cursor = StockListDB.getInstance (BackgroundListenerService.this).readStockCodes ();
        StringBuilder stringBuilder = new StringBuilder();
        ArrayList<String> requestCodesList = new ArrayList<>();
        while (cursor.moveToNext()) {

            requestCodesList.add(stringBuilder.append(cursor.getInt(0)).append("::")
                    .append(cursor.getString(1))
                    .toString());
            stringBuilder.setLength(0);
        }
        return requestCodesList.toArray(new String[requestCodesList.size()]);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        getHistoricalData();

        client = new GoogleApiClient.Builder(this).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle connectionHint) {
                Log.d(TAG, "onConnected: " + connectionHint);
                Wearable.MessageApi.addListener(client, BackgroundListenerService.this);
//                retrieveDeviceNode();
                //sendMessage ("path", "BLABLA");

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

        //getNode();
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pollQuotesHandler = new Handler();
        pollQuotesHandler.postDelayed(pollQuotesRunnable, 1000);
        stockListDB = StockListDB.getInstance (getApplicationContext ());

        //

    }

    private void getHistoricalData() {

        for (String stockCode : getRequestCodes()) {
            HistoricalDataTask hdt = new HistoricalDataTask(getApplicationContext());
            hdt.execute(stockCode);
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
        String theTxt = new String (messageEvent.getData ());
        String thePath = new String (messageEvent.getPath ()),
                nodeId = messageEvent.getSourceNodeId ();


        Toast.makeText (BackgroundListenerService.this, "hello from Listener", Toast.LENGTH_SHORT).show ();
        /*if ("LIST".equals (thePath)) {
            sendNotification ();
        } else {

        }*/
        sendNotification ();
    }

    private void executeRequest() {
        String[] reqCodes = getStockRequestCodes();

        quotesArrayList.clear();

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

    private String getRSI (int index, String liveRate) {

        if (getRequestCodes ().length == index || getRequestCodes ().length < index) {
            return "";
        }

        String stockName = getRequestCodes ()[index].split ("::")[1];

        double rsiIndicator = 0.0;
        RSI rsi = new RSI (14, stockName.toUpperCase (), stockListDB, liveRate);

        return rsi.calculate ();

    }

    private class DownloadWebPageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            StringBuilder chunks = new StringBuilder();
            try {

                StringBuilder sb = new StringBuilder();
                sb.append("https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20yahoo.finance.quotes%20where%20symbol%20in%20(%22");
                sb.append(urls[0]);
                sb.append("%22)&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&callback=");

                URL url = new URL(sb.toString());

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) {
                    return "";
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        (conn.getInputStream())));

                String output;
                System.out.println("Output from Server .... \n");
                while ((output = br.readLine()) != null) {
                    chunks.append(output);
                }
                chunks.append (":::").append (urls[0]);
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return chunks.toString();
        }

        @Override
        protected void onPostExecute(String json2Parse) {

            if ("".equals (json2Parse)) {
                return;
            }

            JSONObject jsonObj = null;
            String stockCode = json2Parse.split (":::")[1].split ("###")[0];
            try {
                jsonObj = new JSONObject (json2Parse.split (":::")[0]);

                JSONObject jsonQueryObj = jsonObj.getJSONObject("query").getJSONObject("results").getJSONObject("quote");

                String name = jsonQueryObj.getString("Name");
                String deltaRatePercent = jsonQueryObj.getString("PercentChange");

                StockListDB.getInstance (getApplicationContext ())
                        .updateLiveData (stockCode, jsonQueryObj.toString (), name.hashCode ());


            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }


}


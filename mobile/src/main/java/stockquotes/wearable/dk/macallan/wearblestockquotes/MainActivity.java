package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

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
    private String[] stockRequestCodes;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;
    private Handler handler = new Handler ();
    private String nodeId;


    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);

        stockListDB = new StockListDB (getApplicationContext ());

        Toast.makeText (this, "starting service", Toast.LENGTH_LONG).show ();
        Intent theServiceIntent = new Intent (MainActivity.this, BackgroundListenerService.class);
        startService (theServiceIntent);

        setContentView (R.layout.activity_main);

        listView = (StockListView) findViewById (R.id.list);
        listView.isOnItemClickable (true);

        listView.setOnItemClickListener (new AdapterView.OnItemClickListener () {
            @Override
            public void onItemClick (AdapterView<?> parent, View view, int position, long id) {
                try {
                    Intent graphIntent = new Intent (MainActivity.this, GraphActivity.class);
                    graphIntent.putExtra ("STOCK_NAME", parent.getItemAtPosition (position).toString ());
                    graphIntent.putExtra ("STOCK_CODE", listView.getRequestCodes ()[position]);
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

                    String stockId = listView.getRequestCodes ()[position].split (":")[0];
                    StringBuilder sb = new StringBuilder (parent.getItemAtPosition (position).toString ().split ("\n")[0]);


                    if (stockListDB.isNotifySuspended (Integer.valueOf (stockId))) {
                        stockListDB.update (stockId, 0);
                        view.getBackground ().setAlpha (255);
                        sb.append (" has enabled notifications.");

                    } else {
                        stockListDB.update (stockId, 1);
                        view.getBackground ().setAlpha (100);
                        sb.append (" has suspended notifications.");

                    }

                    Toast.makeText (getApplicationContext (),
                            sb.toString (), Toast.LENGTH_LONG).show ();


                    return true;
                }
            });

        listView.initList ();

        Button refreshButton = (Button) findViewById (R.id.refresh_button);
        if (refreshButton != null) {
            refreshButton.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View view) {
                    listView.populateListView ();
                }
            });
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

    }


    @Override
    protected void onResume () {
        super.onResume ();
        client.connect ();
        if (client.isConnected ()) {
            Toast.makeText (this, "OnResume", Toast.LENGTH_LONG);
        }
        listView.populateListView ();

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


    private void sendNotification () {


        if (client.isConnected ()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create (NOTIFICATION_PATH);
            // Make sure the data item is unique. Usually, this will not be required, as the payload
            // (in this case the title and the content of the notification) will be different for almost all
            // situations. However, in this example, the text and the content are always the same, so we need
            // to disambiguate the data item by adding a field that contains teh current time in milliseconds.

            dataMapRequest.getDataMap ().putStringArrayList (NOTIFICATION_CONTENT, listView.getStocksList ());
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
*/
        //nodeId = messageEvent.getSourceNodeId();


        //  Toast.makeText (MainActivity.this, "hello from Mobile", Toast.LENGTH_SHORT).show ();
        //sendNotification ();

    }


    @Override
    protected void onSaveInstanceState (Bundle bundle) {

        bundle.putStringArrayList ("THE_LIST", listView.getStocksList ());
        super.onSaveInstanceState (bundle);
    }

    @Override
    public void onRestoreInstanceState (Bundle bundle) {
        super.onRestoreInstanceState (bundle);
        listView.setViewListData (bundle.getStringArrayList ("THE_LIST"));
        listView.populateListView ();
    }

    @Override
    public void onDataChanged (DataEventBuffer dataEvents) {

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
                        String[] quoteChunks = quote.split ("::");

                        //double rate =  Double.parseDouble (quoteChunks[1]);

                        listView.getAdapter ().add (new StringBuilder (quoteChunks[0])
                                .append (" \n")
                                .append (quoteChunks[1])
                                .append (": \t")
                                .append (quoteChunks[2])
                                .append (" / ")
                                .append (quoteChunks[3])
                                .toString ())
                        ;


                    }
                    listView.getAdapter ().notifyDataSetChanged ();
                    Time now = new Time ();
                    now.setToNow ();
                    String lastUdate = now.format ("%H:%M:%S");


                }
            }


        } catch (Exception e) {
            e.printStackTrace ();
        }
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

}
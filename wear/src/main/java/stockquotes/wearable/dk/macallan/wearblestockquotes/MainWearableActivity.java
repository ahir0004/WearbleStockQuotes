package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

import static stockquotes.wearable.dk.macallan.wearblestockquotes.NotificationUpdateService.LOGD;

public class MainWearableActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        DataApi.DataListener,
        MessageApi.MessageListener {

    private static final String TAG = "MainActivity";
    private ArrayAdapter<String> adapter;
    private ListView listView;
    private ImageView imageView;
    private String nodeId;
    private GoogleApiClient mGoogleApiClient;
    private ArrayList<String> quotesArrayList = new ArrayList<> ();
    private Handler handler;
    private Handler pollQuotesHandler;

    private Runnable pollQuotesRunnable = new Runnable () {
        @Override
        public void run () {
            requestQuotes ();
            pollQuotesHandler.postDelayed (this, 60000);
        }
    };

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        setContentView (R.layout.activity_main_wearable);
        setScreenBrightness (0.1f);
        //handler = new Handler ();
        pollQuotesHandler = new Handler ();

        listView = (ListView) findViewById (R.id.list);
        imageView = (ImageView) findViewById (R.id.updateView);

        adapter = new ArrayAdapter<String> (getBaseContext (),
                R.layout.wearable_list_layout) {
            @Override
            public View getView (int position, View convertView, ViewGroup parent) {

                LayoutInflater inflater = getLayoutInflater ();
                View view = inflater.inflate (R.layout.wearable_list_layout, null, true);

                String str = "";
                TextView tv = (TextView) view.findViewById (R.id.stock_name_wear);
                TextView tradePrice = ((TextView) view.findViewById (R.id.stock_rate));
                TextView tradeDate = ((TextView) view.findViewById (R.id.last_trade_date));
                TextView tradeChange = ((TextView) view.findViewById (R.id.change));


                if (this.getCount () > 0) {

                    str = this.getItem (position);
                    String[] stockValues = null;

                    stockValues = str.split ("\n");
                    String theName = stockValues[0];
                    if (!"".equals (theName) && theName != null && theName.length () > 18) {
                        theName = theName.substring (0, theName.lastIndexOf (" "));
                        if (theName.length () > 18) {
                            theName = theName.substring (0, 18);
                        }
                    }

                    tv.setText (theName);
                    tradePrice.setText (stockValues[1]);
                    tradeChange.setText (stockValues[2]);
                    tradeDate.setText (stockValues[3]);

                }


                if (str.contains ("-")) {
                    tradeChange.setTextColor (getScreenBrightness () > 0.0f ? Color.RED : Color.LTGRAY);
                } else {
                    tradeChange.setTextColor (getScreenBrightness () > 0.0f ? Color.GREEN : Color.LTGRAY);
                }

                return view;
            }
        };
        // Assign adapter to ListView
        listView.setAdapter (adapter);

        getWindow ().addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mGoogleApiClient = new GoogleApiClient.Builder (this)
                .addApi (Wearable.API)
                .addConnectionCallbacks (this)
                .addOnConnectionFailedListener (this)
                .build ();

    }

    private void blink () {
        Animation anim = new AlphaAnimation (0.0f, 1.0f);
        anim.setDuration (750); //You can manage the time of the blink with this parameter
        anim.setStartOffset (20);
        anim.setRepeatMode (Animation.REVERSE);
        anim.setRepeatCount (Animation.INFINITE);
    }

    protected void requestQuotes () {
        new WearableActivityTask ().execute ("LIST", "GEN.CO");
        adapter.notifyDataSetChanged ();
    }


    @Override
    protected void onResume () {
        super.onResume ();
        mGoogleApiClient.connect ();
    }

    @Override
    protected void onPause () {
        if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected ()) {
            Wearable.DataApi.removeListener (mGoogleApiClient, this);
            Wearable.MessageApi.removeListener (mGoogleApiClient, this);
            mGoogleApiClient.disconnect ();
        }

        deleteAppData ();

        super.onPause ();

    }

    @Override
    public void onConnected (Bundle connectionHint) {
        LOGD (TAG, "onConnected(): Successfully connected to Google API client");
        Wearable.DataApi.addListener (mGoogleApiClient, this);
        Wearable.MessageApi.addListener (mGoogleApiClient, this);
        new Thread (new Runnable () {
            @Override
            public void run () {
                NodeApi.GetLocalNodeResult nodes = Wearable.NodeApi.getLocalNode (mGoogleApiClient).await ();
                NodeApi.GetConnectedNodesResult conNodes = Wearable.NodeApi.getConnectedNodes (mGoogleApiClient).await ();
                for (Node node : conNodes.getNodes ()) {
                    nodeId = node.getId ();
                }

            }
        }).start ();

        pollQuotesHandler.postDelayed (pollQuotesRunnable, 2000);
    }

    @Override
    public void onConnectionSuspended (int cause) {
        LOGD (TAG, "onConnectionSuspended(): Connection to Google API client was suspended");
    }

    @Override
    public void onConnectionFailed (ConnectionResult result) {
        Log.e (TAG, "onConnectionFailed(): Failed to connect, with result: " + result);
    }

    @Override
    public void onDataChanged (DataEventBuffer dataEvents) {
        LOGD (TAG, "onDataChanged(): " + dataEvents);

        try {
            for (DataEvent event : dataEvents) {

                nodeId = event.getDataItem ().getUri ().getHost ();

                if (event.getType () == DataEvent.TYPE_CHANGED) {
                    String path = event.getDataItem ().getUri ().getPath ();

                    DataMapItem dataMapItem = DataMapItem.fromDataItem (event.getDataItem ());
                    ArrayList<String> quotesList = (ArrayList<String>) dataMapItem.getDataMap ()
                            .getStringArrayList ("content").clone ();

                    adapter.clear ();
                    for (String quote : quotesList) {
                        adapter.add (quote);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace ();
        }
    }

    @Override
    public void onMessageReceived (MessageEvent event) {
    }

    protected float getScreenBrightness () {
        return MainWearableActivity.this.getWindow ().getAttributes ().screenBrightness;
    }

    protected void setScreenBrightness (float brightness) {
        WindowManager.LayoutParams layoutParams = MainWearableActivity.this.getWindow ().getAttributes ();
        layoutParams.screenBrightness = brightness;
        MainWearableActivity.this.getWindow ().setAttributes (layoutParams);
    }

    private void deleteAppData () {
        try {
            // clearing app data
            String packageName = getApplicationContext ().getPackageName ();
            Runtime runtime = Runtime.getRuntime ();
            runtime.exec ("pm clear " + packageName);

        } catch (Exception e) {
            e.printStackTrace ();
        }
    }

    private class WearableActivityTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground (String... args) {

            String PATH = args[0].toUpperCase ();

            String stockCode = args[1];

            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage (mGoogleApiClient, nodeId, PATH,
                    stockCode.getBytes ()).await ();

            if (!result.getStatus ().isSuccess ()) {
                Log.e (TAG, "ERROR: failed to send Message: " + result.getStatus ());
            }

            return "";
        }


    }

}

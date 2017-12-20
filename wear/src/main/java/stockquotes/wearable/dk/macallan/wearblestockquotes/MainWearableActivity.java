package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Time;
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
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

import static stockquotes.wearable.dk.macallan.wearblestockquotes.NotificationUpdateService.LOGD;
//import static stockquotes.wearable.dk.macallan.wearblestockquotes.R.id.list;

public class MainWearableActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        DataApi.DataListener,
        MessageApi.MessageListener {

    private static final String TAG = "MainActivity";
    private ArrayAdapter<String> adapter;
    private ListView listView;
    private ImageView imageView;
    //    private TextView lastUpdateTextView;
    private String nodeId;
    private GoogleApiClient mGoogleApiClient;
    private ArrayList<String> quotesArrayList = new ArrayList<> ();
    private Handler handler;
    private Runnable runnable = new Runnable () {
        @Override
        public void run () {
            MainWearableActivity.this.setScreenBrightness (0.0f);
            adapter.notifyDataSetChanged ();
        }
    };

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        setContentView (R.layout.activity_main_wearable);
        setScreenBrightness (0.0f);
        handler = new Handler ();

        listView = (ListView) findViewById (R.id.list);
        imageView = (ImageView) findViewById (R.id.updateView);
//        lastUpdateTextView = (TextView) findViewById (R.id.last_updated);

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
                /*
                    InputFilter[] filters = new InputFilter[1];
                    filters[0] = new InputFilter.LengthFilter(19);
                    tv.setFilters (filters);
                */
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
        // ATTENTION: This "addApi(AppIndex.API)"was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mGoogleApiClient = new GoogleApiClient.Builder (this)
                .addApi (Wearable.API)
                .addConnectionCallbacks (this)
                .addOnConnectionFailedListener (this)
                .build ();
        imageView.setVisibility (View.VISIBLE);
        Bitmap bitmap = BitmapFactory.decodeResource (getResources (), R.mipmap.stockmarket);
//        imageView.setImageDrawable (new BitmapDrawable ();
        blink ();
    }

    private void blink () {
        Animation anim = new AlphaAnimation (0.0f, 1.0f);
        anim.setDuration (750); //You can manage the time of the blink with this parameter
        anim.setStartOffset (20);
        anim.setRepeatMode (Animation.REVERSE);
        anim.setRepeatCount (Animation.INFINITE);
//        lastUpdateTextView.setText ("WAITING FOR DATA...");
//        lastUpdateTextView.startAnimation (anim);
    }

    protected void requestQuotes (View view) {
        new WearableActivityTask ().execute ("LIST", "GEN.CO");
        setScreenBrightness (0.5f);
        adapter.notifyDataSetChanged ();
        handler.postDelayed (runnable, 10000l);
    }
/*

    private void initList () {
        adapter.clear ();
        StringBuilder sb = new StringBuilder ();
        sb.append ("TESTSTOCK LDT");
        sb.append (System.getProperty("line.separator"));
        sb.append ("1389.00");
        sb.append (System.getProperty("line.separator"));
        sb.append ("-2.60");
        sb.append ("/");
        sb.append ("-0.67%");
        sb.append (System.getProperty("line.separator"));
        sb.append ("13.15");
        adapter.add (sb.toString ());
        sb.delete (0,sb.length ());
        sb.append ("VESTAS WIND SYSTEMS A/S");
        sb.append (System.getProperty("line.separator"));
        sb.append ("593.00");
        sb.append (System.getProperty("line.separator"));
        sb.append ("2.60");
        sb.append ("/");
        sb.append ("0.67%");
        sb.append (System.getProperty("line.separator"));
        sb.append ("13.20");
        adapter.add (sb.toString ());
    }
*/

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
//                nodeId = nodes.getNode ().getId ();
/*for (Node node: conNodes.getNodes ()){

    System.out.println ("connectedNODE_wearable: " + node.getId () );
}*/


                //System.out.println ("LOCALNODE_wearable: " + nodeId );

                //        new WearableActivityTask().execute ("LIST","GEN.CO");
            }
        }).start ();
        requestQuotes (MainWearableActivity.this.getWindow ().getCurrentFocus ());
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
                    ArrayList<String> quotesList = dataMapItem.getDataMap ()
                            .getStringArrayList ("content");

                    adapter.clear ();
                    int index = 0;
                    for (String quote : quotesList) {
                        adapter.add (quote);
                    }

                    // adapter.notifyDataSetChanged ();
                    Time now = new Time ();
                    now.setToNow ();
                    String lastUdate = now.format ("%H:%M:%S");

//                    lastUpdateTextView.setText (lastUdate);
//                    lastUpdateTextView.clearAnimation ();
                }
            }
        } catch (Exception e) {
            e.printStackTrace ();
        }
    }

    @Override
    public void onMessageReceived (MessageEvent event) {
        LOGD (TAG, "onMessageReceived: " + event);
//        /**/mDataFragment.appendItem("Message", event.toString());

        Toast.makeText (MainWearableActivity.this, event.getData ().toString (), Toast.LENGTH_LONG);
        ///event.getData ();
    }

    protected float getScreenBrightness () {
        return MainWearableActivity.this.getWindow ().getAttributes ().screenBrightness;
    }

    protected void setScreenBrightness (float brightness) {
        WindowManager.LayoutParams layoutParams = MainWearableActivity.this.getWindow ().getAttributes ();
        layoutParams.screenBrightness = brightness;
        MainWearableActivity.this.getWindow ().setAttributes (layoutParams);
    }

    private class WearableActivityTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground (String... args) {

            String PATH = args[0].toUpperCase ();

            String stockCode = args[1];

            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage (mGoogleApiClient, "b633274e", PATH,
                    stockCode.getBytes ()).await ();

            if (!result.getStatus ().isSuccess ()) {
                Log.e (TAG, "ERROR: failed to send Message: " + result.getStatus ());
            }

            return null;
        }


    }


}

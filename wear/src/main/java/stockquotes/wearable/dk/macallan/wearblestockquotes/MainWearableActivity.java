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
    private TextView lastUpdateTextView;
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
        lastUpdateTextView = (TextView) findViewById (R.id.last_updated);

        adapter = new ArrayAdapter<String> (this,
                android.R.layout.simple_list_item_1, android.R.id.text1, quotesArrayList) {
            @Override
            public View getView (int position, View convertView, ViewGroup parent) {
                // Get the current item from ListView
                View view = super.getView (position, convertView, parent);


                String txt = ((TextView) view).getText ().toString ();
                // Set a background color for ListView regular row/item

                if (txt.contains ("+")) {
                    view.setBackgroundColor (getScreenBrightness () > 0.0f ? Color.GREEN : Color.LTGRAY);
                } else {
                    view.setBackgroundColor (getScreenBrightness () > 0.0f ? Color.RED : Color.GRAY);
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
        lastUpdateTextView.setText ("WAITING FOR DATA...");
        lastUpdateTextView.startAnimation (anim);
    }

    protected void requestQuotes (View view) {
        new WearableActivityTask ().execute ();
        setScreenBrightness (0.5f);
        adapter.notifyDataSetChanged ();
        handler.postDelayed (runnable, 10000l);
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

        super.onPause ();
    }

    @Override
    public void onConnected (Bundle connectionHint) {
        LOGD (TAG, "onConnected(): Successfully connected to Google API client");
        Wearable.DataApi.addListener (mGoogleApiClient, this);
        Wearable.MessageApi.addListener (mGoogleApiClient, this);

        /*Wearable.NodeApi.getConnectedNodes (mGoogleApiClient).setResultCallback (new ResultCallback<NodeApi.GetConnectedNodesResult> () {
            @Override
            public void onResult (NodeApi.GetConnectedNodesResult nodes) {
                for (Node node : nodes.getNodes ()) {
                    nodeId = node.getId ();
                }
            }
        });*/

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


                    quotesArrayList.clear ();
                    int index = 0;
                    for (String quote : quotesList) {
                        String[] quoteChunks = quote.split ("::");

                        quotesArrayList.add (new StringBuilder (quoteChunks[0])
                                .append (": ")
                                .append (quoteChunks[1])
                                .append (" \n")
                                .append (quoteChunks[2]).toString ());


                    }

                    adapter.notifyDataSetChanged ();
                    Time now = new Time ();
                    now.setToNow ();
                    String lastUdate = now.format ("%H:%M:%S");


                    lastUpdateTextView.setText (lastUdate);
                    lastUpdateTextView.clearAnimation ();
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

     /*   Toast.makeText (MainWearableActivity.this,event.getData ().toString (),Toast.LENGTH_LONG);

        lastUpdateTextView.setText (new String(event.getData ()));*/
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

    private class WearableActivityTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground (Void... args) {

            byte[] bytes = "hello_there".getBytes ();

            MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage (mGoogleApiClient, nodeId, "SEND_MORE_MONEY",
                    bytes).await ();

            if (!result.getStatus ().isSuccess ()) {
                Log.e (TAG, "ERROR: failed to send Message: " + result.getStatus ());
            }

            return null;
        }
    }


}

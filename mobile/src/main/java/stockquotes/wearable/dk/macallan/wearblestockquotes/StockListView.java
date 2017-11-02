package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by AHI003 on 08-11-2016.
 */

public class StockListView extends ListView implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {


    private boolean isOnItemClickable;
    private StockListDB stockListDB;
    private ArrayAdapter<HashMap<String, String>> adapter;
    private String[] stocks;
    private ArrayList<String> quotesArrayList;

    public StockListView (Context context) {
        super (context, null);

    }


    public StockListView (Context context, AttributeSet attrs) {
        super (context, attrs);

        stockListDB = StockListDB.getInstance (context);
        quotesArrayList = new ArrayList<> ();


        adapter = new ArrayAdapter<HashMap<String, String>> (this.getContext (),
                R.layout.list_layout) {


            @Override
            public View getView (int position, View convertView, ViewGroup parent) {

                LayoutInflater inflater = ((MainActivity) getContext ()).getLayoutInflater ();
                View view = inflater.inflate (R.layout.list_layout, null, true);
                view.setBackgroundColor (Color.WHITE);
                HashMap hashMap;
                String theChange = "";

                if (this.getCount () > 0) {
                    //txt = this.getItem (position);
                    hashMap = this.getItem (position);
                    theChange = hashMap.get ("CHANGE").toString () + "/" + hashMap.get ("CHANGE_PCT").toString ();
                    ((TextView) view.findViewById (R.id.stock_name)).setText (hashMap.get ("NAME").toString ());
                    ((TextView) view.findViewById (R.id.stock_rate)).setText (hashMap.get ("LTP").toString ());
                    ((TextView) view.findViewById (R.id.change)).setText (theChange);
                    ((TextView) view.findViewById (R.id.last_trade_date)).setText (hashMap.get ("LTT").toString ());
                    //((TextView) view.findViewById (R.id.rsi)).setText ("RSI: " +hashMap.get ("RSI").toString ());
                    // Set a background color for ListView regular row/item

                    if (theChange.contains ("+")) {
                        view.setBackgroundColor (Color.GREEN);
                    } else {
                        view.setBackgroundColor (Color.RED);
                    }


                    if (getRequestCodes ().length > 0 && getRequestCodes ().length > position) {

                        String stockCode = getRequestCodes ()[position].split (":")[1];
                        long stockId = stockListDB.getIdFromStockCode (stockCode.trim ());
                        Cursor cursor = stockListDB.readSuspendedNotificationById (String.valueOf (stockId));

                        int isSuspended = 0;

                        while (cursor.moveToNext ()) {
                            isSuspended = cursor.getInt (0);
                        }

                        if (isSuspended == 1) {
                            view.getBackground ().setAlpha (150);
                        }
                    }
                }
                blink (view);

                return view;
            }
        };

        this.setAdapter (adapter);
        populateListView ();
        setOnItemClickListener (this);
        // initList ();
        isOnItemClickable = false;

    }

    @Override
    public ArrayAdapter<HashMap<String, String>> getAdapter () {
        return adapter;
    }

    protected void isOnItemClickable (boolean isClickable) {
        isOnItemClickable = isClickable;
    }

    protected void initList () {
        setViewListData (new ArrayList<String> (Arrays.asList (getRequestCodes ())));
    }


    @Override
    public void onItemClick (AdapterView<?> parent, View view, int position, long id) {
        /*String stockId = parent.getItemAtPosition (position).toString ().split (":")[0];
        if (isOnItemClickable) {
            stockListDB.removeStockFromDB (stockId);
            setViewListData (new ArrayList<String> (Arrays.asList (getRequestCodes ())));
            populateListView ();
        }*/
    }

    protected String[] getRequestCodes () {
        Cursor cursor = StockListDB.getInstance (getContext ()).readStockCodes ();
        StringBuilder stringBuilder = new StringBuilder ();
        ArrayList<String> requestCodesList = new ArrayList<> ();
        while (cursor.moveToNext ()) {
            requestCodesList.add (stringBuilder.append (cursor.getInt (0))
                    .append (": ")
                    .append (cursor.getString (1))
                    .toString ());
            stringBuilder.setLength (0);
        }
        return requestCodesList.toArray (new String[requestCodesList.size ()]);

    }

    private void blink (View view) {
        Animation anim = new AlphaAnimation (0.0f, 1.0f);
        anim.setDuration (750); //You can manage the time of the blink with this parameter
        //anim.setStartOffset (20);
        //anim.setRepeatMode (Animation.REVERSE);
        //anim.setRepeatCount (1);
       /* lastUpdateTextView.setText ("WAITING FOR DATA...");
        lastUpdateTextView.startAnimation (anim);*/
        view.startAnimation (anim);
    }

    protected void populateListView () {
        adapter.notifyDataSetChanged ();
    }

    /*public void setViewListData (String[] data) {
        setViewListData (new ArrayList<> (Arrays.asList (data)));
    }*/

    public void setViewListData (ArrayList<String> data) {
        getStocksList ().clear ();

        for (String str : data) {
            getStocksList ().add (str);

        }
        populateListView ();
    }

    public ArrayList<String> getStocksList () {

        return quotesArrayList;
    }

    @Override
    public boolean onItemLongClick (AdapterView<?> parent, View view, int position, long id) {
        return false;
    }


}

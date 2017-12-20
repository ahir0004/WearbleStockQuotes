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
import java.util.Comparator;
import java.util.HashMap;

/**
 * Created by AHI003 on 08-11-2016.
 */

public class StockListView extends ListView implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {


    private boolean isOnItemClickable;
    private StockListDB stockListDB;
    private ArrayAdapter<HashMap<String, String>> adapter;
    private String[] stocks;
    // private ArrayList<String> quotesArrayList;

    public StockListView (Context context) {
        super (context, null);

    }

    public StockListView (Context context, AttributeSet attrs) {
        super (context, attrs);

        stockListDB = StockListDB.getInstance (context);
        //   quotesArrayList = new ArrayList<> ();



        adapter = new ArrayAdapter<HashMap<String, String>> (this.getContext (),
                R.layout.list_layout) {

/*

            @Override
            public void notifyDataSetChanged(){

                this.sort (new Comparator<HashMap<String, String>> () {
                    @Override
                    public int compare (HashMap<String, String> o1, HashMap<String, String> o2) {
                        return o1.get ("NAME").compareTo (o2.get ("NAME"));
                    }
                });
                super.notifyDataSetChanged ();
            }
*/

            @Override
            public View getView (int position, View convertView, ViewGroup parent) {

                LayoutInflater inflater = ((MainActivity) getContext ()).getLayoutInflater ();
                View view = inflater.inflate (R.layout.list_layout, null, true);
                //view.setBackgroundColor (Color.WHITE);
                HashMap hashMap;
                String theChange = "";

                if (this.getCount () > 0) {
                    //txt = this.getItem (position);
                    hashMap = this.getItem (position);
                    if ("".equals (hashMap.get ("CHANGE").toString ()))
                        theChange = "";
                    else {
                        theChange = hashMap.get ("CHANGE").toString () + "/" + hashMap.get ("CHANGE_PCT").toString ();
                    }
                    TextView tvName = ((TextView) view.findViewById (R.id.stock_name));
                    tvName.setText (hashMap.get ("NAME").toString ());

                    if ("".equals (hashMap.get ("LTP").toString ())) {

                        blink (tvName, -1, 2000);
                    }

                    TextView tvLTP = ((TextView) view.findViewById (R.id.stock_rate));
                    tvLTP.setText (hashMap.get ("LTP").toString ());


                    TextView tvCng = ((TextView) view.findViewById (R.id.change));
                    tvCng.setText (theChange);
                    TextView tvLTD = ((TextView) view.findViewById (R.id.last_trade_date));
                    tvLTD.setText (hashMap.get ("LTT").toString ());
                    //TextView tvRSI = ((TextView) view.findViewById (R.id.rsi));
                    // Set a background color for ListView regular row/item
                    //tvRSI.setBackgroundColor (Color.parseColor ("#80000000"));
                    if (theChange.contains ("-")) {
                        //view.setBackgroundColor (Color.RED);
                        tvCng.setTextColor (Color.rgb (245, 0, 0));
                        //  tvRSI.setBackgroundResource (R.mipmap.down);
                    } else {
                        //view.setBackgroundColor (Color.GREEN);
                        tvCng.setTextColor (Color.rgb (0, 200, 0));
                        //tvRSI.setBackgroundResource (R.mipmap.up);
                    }

                    if (getRequestCodes ().length > 0 && getRequestCodes ().length > position) {

                        // String stockCode = getRequestCodes ()[position].split (":")[1];
                        //long stockId = stockListDB.getIdFromStockCode (hashMap.get ("NAME").toString ());
                        Cursor cursor = stockListDB.readSuspendedNotification (String.valueOf (hashMap.get ("NAME").toString ().hashCode ()));

                        int isSuspended = 0;

                        while (cursor.moveToNext ()) {
                            isSuspended = cursor.getInt (0);
                        }

                        if (isSuspended == 1) {
                            view.getBackground ().setAlpha (150);
                        }
                    }
                }
                blink (view, 0, 750);

                return view;
            }
        };

        this.setAdapter (adapter);
//        populateListView ();
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
/*

    protected void initList () {
        setViewListData (new ArrayList<String> (Arrays.asList (getRequestCodes ())));
    }
*/


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

    private void blink (View view, int repeatCount, long duration) {
        Animation anim = new AlphaAnimation (0.0f, 1.0f);
        anim.setDuration (duration); //You can manage the time of the blink with this parameter
        //  anim.setStartOffset (20);
        // anim.setRepeatMode (Animation.REVERSE);
        anim.setRepeatCount (repeatCount);
       /* lastUpdateTextView.setText ("WAITING FOR DATA...");
        lastUpdateTextView.startAnimation (anim);*/
        view.startAnimation (anim);
    }

    protected void populateListView () {
        getAdapter ().sort (new Comparator<HashMap<String, String>> () {
            @Override
            public int compare (HashMap<String, String> o1, HashMap<String, String> o2) {
                return o1.get ("NAME").compareTo (o2.get ("NAME"));
            }
        });
        adapter.notifyDataSetChanged ();
    }


    @Override
    public boolean onItemLongClick (AdapterView<?> parent, View view, int position, long id) {
        return false;
    }


}

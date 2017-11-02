package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;

/**
 * Created by AHI003 on 07-11-2016.
 */
public class AddStockActivity extends Activity {
    private EditText editText;
    private StockListDB stockListDB;
    private ListView listView;
    private ArrayAdapter<String> arrayAdapter;
    @Override
    public void onCreate (Bundle bundle) {
        super.onCreate (bundle);
        this.setTitle (R.string.add_stock_title);

        stockListDB = StockListDB.getInstance (this);

        editText = new EditText (this);

        listView = new ListView (this);
        arrayAdapter = new ArrayAdapter<String> (this, android.R.layout.simple_list_item_1, getQuotesList ());
        listView.setAdapter (arrayAdapter);
        //listView.isOnItemClickable (true);
        listView.setOnItemClickListener (new AdapterView.OnItemClickListener () {
            @Override
            public void onItemClick (AdapterView<?> parent, View view, int position, long id) {

                String stockCode = parent.getItemAtPosition (position).toString ();
                stockListDB.removeStockFromDB (stockCode);
                arrayAdapter.remove (stockCode);
                arrayAdapter.notifyDataSetChanged ();
            }
        });
        Button addStockbutton = new Button (this);
        addStockbutton.setText ("Add stock");
        LinearLayout linearLayout = new LinearLayout (this);
        linearLayout.setOrientation (LinearLayout.VERTICAL);
        linearLayout.addView (editText);
        linearLayout.addView (addStockbutton);

        linearLayout.addView (listView);

        addStockbutton.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                String stockCode = editText.getText ().toString ().toUpperCase ();
                editText.setText ("");
                stockListDB.insert (stockCode);
                long id = stockListDB.getIdFromStockCode (stockCode);

                if (id != -1l) {
                    hideKeyboard ();
                    arrayAdapter.add (stockCode);
                    arrayAdapter.notifyDataSetChanged ();
                    HistoricalDataTask hdt = new HistoricalDataTask (getApplicationContext ());
                    hdt.execute (id + "::" + stockCode);
                }

            }
        });


        //listView.initList ();
        Intent theServiceIntent = new Intent (AddStockActivity.this, BackgroundListenerService.class);
        startService (theServiceIntent);

        setContentView (linearLayout);

    }

    private ArrayList<String> getQuotesList () {
        ArrayList<String> theList = new ArrayList<> ();
        Cursor cursor = stockListDB.readStockCodes ();
        while (cursor.moveToNext ()) {
            theList.add (cursor.getString (1));
        }
        return theList;
    }

    private void hideKeyboard () {
        InputMethodManager inputManager =
                (InputMethodManager) editText.getContext ().
                        getSystemService (Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow (
                editText.getWindowToken (),
                InputMethodManager.HIDE_NOT_ALWAYS);
    }


}

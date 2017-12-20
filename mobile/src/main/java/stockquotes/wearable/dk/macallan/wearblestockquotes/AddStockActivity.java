package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
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
        listView.setOnItemLongClickListener (new AdapterView.OnItemLongClickListener () {
            @Override
            public boolean onItemLongClick (AdapterView<?> parent, View view, int position, long id) {

                new AlertDialog.Builder (AddStockActivity.this)
                        .setIcon (android.R.drawable.ic_dialog_alert)
                        .setTitle ("Delete stock code")
                        .setMessage ("Are you sure you want to delete this stock code?")
                        .setPositiveButton ("Yes", new DialogInterface.OnClickListener () {
                            @Override
                            public void onClick (DialogInterface dialog, int which) {
                                String stockCode = parent.getItemAtPosition (position).toString ();
                                stockListDB.removeStockFromDB (stockCode);
                                arrayAdapter.remove (stockCode);
                                arrayAdapter.notifyDataSetChanged ();
                            }

                        })
                        .setNegativeButton ("No", null)
                        .show ();

                return true;
            }
        });

        LinearLayout linearLayoutHorizontal = new LinearLayout (this);
        Button addStockbutton = new Button (this);
        Button help = new Button (this);
        help.setText ("?");
        help.setTextSize (24);
        help.setTypeface (help.getTypeface (), Typeface.BOLD);
        addStockbutton.setText ("Add stock");
        LinearLayout linearLayout = new LinearLayout (this);
        linearLayout.setOrientation (LinearLayout.VERTICAL);
        linearLayoutHorizontal.setWeightSum (6);
        editText.setLayoutParams (new LinearLayout.LayoutParams (LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 5.9f));
        linearLayoutHorizontal.addView (editText);
        help.setLayoutParams (new LinearLayout.LayoutParams (LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0.1f));
        linearLayoutHorizontal.addView (help);

        linearLayout.addView (linearLayoutHorizontal);
        linearLayout.addView (addStockbutton);

        linearLayout.addView (listView);


        help.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                StringBuilder sb = new StringBuilder ("Stock codes should be entered \n");
                sb.append ("in a Yahoo finance way (https://finance.yahoo.com/).\n\n");
                sb.append ("Example: \n");
                sb.append ("Facebook has stock code: FB\n");
                sb.append ("Disney has stock code: DIS\n\n");
                sb.append ("Find codes at: https://finance.yahoo.com.\n\n");
                sb.append ("To REMOVE a stock from the list:\n");
                sb.append ("long-press the desired stock item in the list.\n\n");
                sb.append ("When done entering stockcodes\n");
                sb.append ("just hit Back-button to return to live list.");
                new AlertDialog.Builder (AddStockActivity.this)
                        //.setIcon (android.R.drawable.mdialog_holo_light_frame)
                        .setTitle ("How to enter stock codes")
                        .setMessage (sb.toString ())
                        .setPositiveButton ("Close", new DialogInterface.OnClickListener () {
                            @Override
                            public void onClick (DialogInterface dialog, int which) {
                            }

                        })
                        //.setNegativeButton ("No", null)
                        .show ();

                //return true;
            }
        });

        addStockbutton.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                String stockCode = editText.getText ().toString ().toUpperCase ();
                if ("".equals (stockCode))
                    return;

                editText.setText ("");
                stockListDB.insert (stockCode);
                long id = stockListDB.getIdFromStockCode (stockCode);

                if (id != -1l) {
                    hideKeyboard ();
                    arrayAdapter.add (stockCode);
                    arrayAdapter.notifyDataSetChanged ();
                    HistoricalDataTask hdt = new HistoricalDataTask (getApplicationContext ());
                    hdt.execute (stockCode);
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

package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * Created by AHI003 on 07-11-2016.
 */
public class AddStockActivity extends Activity {
    private EditText editText;
    private StockListDB stockListDB;
    private StockListView listView;

    @Override
    public void onCreate (Bundle bundle) {
        super.onCreate (bundle);
        this.setTitle (R.string.add_stock_title);

        stockListDB = new StockListDB (this);

        editText = new EditText (this);

        listView = new StockListView (this, null);
        listView.isOnItemClickable (true);

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
                String input = editText.getText ().toString ().toUpperCase ();
                // String[] codes = input.split ("\\.");

                editText.setText ("");

                long id = stockListDB.insert (input);

                if (id != -1l) {
                    hideKeyboard ();
                    listView.initList ();
                    HistoricalDataTask hdt = new HistoricalDataTask (getApplicationContext ());
                    hdt.execute (id + "::" + input);
                }

            }
        });


        listView.initList ();
        Intent theServiceIntent = new Intent (AddStockActivity.this, BackgroundListenerService.class);
        startService (theServiceIntent);

        setContentView (linearLayout);

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

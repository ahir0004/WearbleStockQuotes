package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/**
 * Created by AHI003 on 01-08-2017.
 */

public class GraphActivity extends Activity {

    private static int defaultDays = 5;
    private ArrayList<Double> sma;
    private int windowHeight;
    private double deltaY;
    private double minY = 0.0;
    private FrameLayout graphViewFrameLayout;
    private ArrayList<Double> stockValues;
    private int days2Chart;
    private LinearLayout yValuesLLayout;
    private LinearLayout xValuesLLayout;
    private String stockName;
    private String stockId;
    private int i = 0;
    private ArrayList<DateTime> stockDates;


    public void shiftPeriod (View view) {
        int periods[] = {defaultDays, 20, 60, 200};
        days2Chart = periods[i == (periods.length - 1) ? i = 0 : ++i];

        try {
            initializeGraphData (stockId);
            calculateAndDisplayCoordinates ();
        } catch (JSONException e) {
            e.printStackTrace ();
        }
    }


    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        // as the ContentView for this Activity.
        getWindow ().addFlags (WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow ().addFlags (WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView (R.layout.graph_layout);


        yValuesLLayout = (LinearLayout) findViewById (R.id.y_axis_linearlayout);
        xValuesLLayout = (LinearLayout) findViewById (R.id.x_axis_linearlayout);
        graphViewFrameLayout = (FrameLayout) findViewById (R.id.graph_view_framelayout);
        stockName = getIntent ().getStringExtra ("STOCK_NAME").split ("\n")[0];
        stockId = getIntent ().getStringExtra ("STOCK_CODE").split (":")[0].trim ();
        days2Chart = getIntent ().getIntExtra ("chartDays", 5);

        try {
            loadSMA (stockId, 60);
            initializeGraphData (stockId);
            calculateAndDisplayCoordinates ();
        } catch (JSONException e) {
            e.printStackTrace ();
        }

    }


    protected ArrayList<String> getGraphDataFromDB (String id, int days) {
        Cursor cursor = StockListDB.getInstance (getApplicationContext ()).readHistoricalStockData (id, days);
        ArrayList<String> histDataList = new ArrayList<> ();
        while (cursor.moveToNext ()) {
            histDataList.add (cursor.getString (2));
        }
        return histDataList;
    }

    private void initializeGraphData (String id) throws JSONException {

        ArrayList<String> jsonObjects = getGraphDataFromDB (id, days2Chart);
        stockValues = new ArrayList<Double> ();
        stockDates = new ArrayList<DateTime> ();

        for (int i = 0; i < jsonObjects.size (); i++) {
            JSONObject jsonObj = new JSONObject (jsonObjects.get (i));

            try {
                String close = jsonObj.getString("close");
                if ("null".equals(close))
                    stockValues.add(0.0);
                else
                    stockValues.add(new Double(close));
            } catch (JSONException e) {
                continue;
            }
            long tmp = Long.parseLong (jsonObj.get ("date").toString ());
            DateFormat df =
                    new SimpleDateFormat ("MMM d, yyyy", Locale.US);
            Date d = new Date (tmp * 1000l);
            DateTime dt = new DateTime (d);
            stockDates.add (dt);
        }
    }

    private void calculateAndDisplayCoordinates () {
        TextView xs;
        TextView ys;
        xValuesLLayout.removeAllViewsInLayout ();
        yValuesLLayout.removeAllViewsInLayout ();

        ArrayList<Double> yCoords = (ArrayList<Double>) stockValues.clone ();
        Collections.sort (stockDates);
        Collections.sort (yCoords);

        double max = 0.0f;
        double min = 0.0f;
        double midlle = 0.0f;

        double[] yCoordsArr = new double[stockValues.size ()];
        int numberOfYs = yCoords.size ();
        float yWeight = 0.5f;


        max = Collections.max (yCoords);
        min = Collections.min (yCoords);
        midlle = (max + min) / 2;
        double delta = (max - min) / (yCoordsArr.length - 1);


        yCoordsArr[0] = min;


        if (defaultDays != days2Chart) {
            numberOfYs = 3;
            yCoordsArr[1] = midlle;
            yWeight = 1.0f;
            yCoordsArr[2] = max;
        } else {
            for (int k = 1; k < numberOfYs; k++) {
                yCoordsArr[k] = yCoordsArr[k - 1] + delta;
            }
            yCoordsArr[numberOfYs - 1] = max;
        }


        for (int k = numberOfYs - 1; k >= 0; k--) {
            ys = new TextView (getApplicationContext ());

            ys.setLayoutParams (new LinearLayout.LayoutParams (
                    160,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    yWeight
            ));
            ys.setBackgroundColor (Color.TRANSPARENT);
            ys.setTextColor (Color.WHITE);

            ys.setText (String.format (Locale.US, "%.1f", yCoordsArr[k]));
            ys.setTextAlignment (View.TEXT_ALIGNMENT_TEXT_END);
            yValuesLLayout.addView (ys);
        }


        if (days2Chart != defaultDays) {

            DateTime[] dates =
                    {
                            stockDates.get (0),
                            stockDates.get (stockDates.size () - 1)

                    };

            for (int i = 0; i < dates.length; i++) {


                xs = new TextView (getApplicationContext ());

                xs.setText (dates[i].toString (DateTimeFormat.forPattern ("d MMM")).replace (".", ""));
                xs.setTextColor (Color.WHITE);
                xs.setLayoutParams (new LinearLayout.LayoutParams (
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        140,
                        2.0f
                ));

                xValuesLLayout.addView (xs);
            }
        } else {
            for (DateTime date : stockDates) {


                xs = new TextView (getApplicationContext ());
                xs.setText (date.toString (DateTimeFormat.forPattern ("d MMM")).replace (".", ""));
                xs.setTextColor (Color.WHITE);
                xs.setLayoutParams (new LinearLayout.LayoutParams (
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        140,
                        0.5f
                ));

                xValuesLLayout.addView (xs);
            }
        }
        graphViewFrameLayout.addView (new ChartView (getApplicationContext ()));
        setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        TextView headLine = (TextView) findViewById (R.id.headline_textView);
        headLine.setText (stockName.toUpperCase () + " (" + days2Chart + " days)");
        headLine.setTextColor (Color.YELLOW);
        headLine.setTextSize (25.0f);
        headLine.setGravity (Gravity.CENTER_HORIZONTAL);
        headLine.setBackgroundColor (Color.TRANSPARENT);
    }

    private void loadSMA (String stockId, int days) {

        sma = new ArrayList<> ();
        Cursor cursor = StockListDB.getInstance (getApplicationContext ()).readHistoricalStockData (stockId, 250);
        ArrayList<String> histDatesList = new ArrayList<> ();
        ArrayList<String> histDataList = new ArrayList<> ();

        while (cursor.moveToNext ()) {
            histDatesList.add (cursor.getString (1));
            histDataList.add (cursor.getString (2));
        }

        for (int i = 0; i < histDataList.size () - 60; i++) {
            sma.add (calculateAverage (histDataList, (i)));
        }

        Collections.reverse (sma);
    }

    private double calculateAverage (ArrayList<String> data, int i) {
        double theAverage = 0.0;
        double close = 0.0;

        for (int j = i; j < (60 + i); j++) {

            if (j > data.size () - 1) {
                break;
            }
            try {
                JSONObject jsonObject = new JSONObject (data.get (j));
                close = new Double (jsonObject.get ("close").toString ());
            } catch (JSONException e) {
                e.printStackTrace ();
                continue;
            }
            theAverage = theAverage + close;
        }
        return theAverage / 60.0;
    }

    private class ChartView extends View {

        double lineCoords[] = new double[(stockValues.size () + stockDates.size ())];

        public ChartView (Context context) {
            super (context);
        }

        @Override
        protected void onSizeChanged (int w, int h, int oldw, int oldh) {
            super.onSizeChanged (w, h, oldw, oldh);
            Double max = 0.0;
            Double min = 0.0;
            try {
                max = Collections.max (stockValues);
                min = Collections.min (stockValues);
                minY = min;
            } catch (Exception e) {
                e.printStackTrace ();
            }
            double delta = max - min;
            deltaY = delta;
            windowHeight = h;

            int j = stockValues.size () - 1;

            double deltaX = (double) w / (double) j;

            lineCoords[0] = 0f;

            for (int i = 1; i < (stockValues.size () + stockDates.size ()); i++) {

                if (i % 2 == 0) {
                    lineCoords[i] = lineCoords[i - 2] + deltaX;

                } else {
                    double diff = stockValues.get (j) - min;
                    double tmp = ((diff / delta) * h);

                    lineCoords[i] = h - tmp;
                    j--;
                }
            }

        }

        @Override
        public void onDraw (Canvas canvas) {

            Paint paint = new Paint ();
            paint.setColor (Color.GREEN);
            paint.setStrokeWidth (10f);

            Paint paintSMA = new Paint ();
            paintSMA.setColor (Color.BLUE);
            paintSMA.setStrokeWidth (3f);

            setBackgroundColor (Color.WHITE);

            for (int i = 0; i < (lineCoords.length - 2); i = i + 2) {

                float startX = (float) lineCoords[i];
                float startY = (float) lineCoords[i + 1];

                float endX = (float) lineCoords[i + 2];
                float endY = (float) lineCoords[i + 3];

                canvas.drawLine (startX, startY, endX, endY, paint);

            }


            //if(lineCoords.length > 100 ) {
            int k = 2;
            for (int j = sma.size (); j > 2; j--) {

                if ((k + 2) > lineCoords.length) {
                    break;
                }
                float startX2 = (float) lineCoords[lineCoords.length - k];
                double diff = sma.get (j - 1).doubleValue () - minY;
                double tmp = ((diff / deltaY) * windowHeight);
                float startY2 = (float) (windowHeight - tmp);

                float endX2 = (float) lineCoords[lineCoords.length - (k + 2)];

                diff = sma.get (j - 2).doubleValue () - minY;
                tmp = ((diff / deltaY) * windowHeight);
                float endY2 = (float) (windowHeight - tmp);
                canvas.drawLine (startX2, startY2, endX2, endY2, paintSMA);

                k = k + 2;
            }
        }


    }


}

package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Created by AHI003 on 01-08-2017.
 */

public class GraphActivity extends Activity {

    private static final String TAG = "GraphActivity";
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
    private String rsi;
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor ();
    private final Handler mainHandler = new Handler (Looper.getMainLooper ());
    // destroyed, loadGeneration and the chart data fields are main-thread only
    private boolean destroyed;
    private int loadGeneration;


    public void shiftPeriod (View view) {
        int periods[] = {defaultDays, 20, 60, 200};
        requestGraphData (periods[i == (periods.length - 1) ? i = 0 : ++i]);
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
        stockName = getIntent ().getStringExtra ("STOCK_NAME");
        String code = getIntent ().getStringExtra ("STOCK_CODE");
        if (code == null) {
            finish ();
            return;
        }
        stockId = code.trim ();
        if (stockName == null)
            stockName = stockId;
        rsi = getIntent ().getStringExtra ("RSI");
        if (rsi == null || rsi.isEmpty ())
            rsi = RSI.NOT_AVAILABLE;
        days2Chart = getIntent ().getIntExtra ("chartDays", 5);

        graphViewFrameLayout.addOnLayoutChangeListener ((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft)
                v.post (this::alignXAxisLabels);
        });
        requestGraphData (days2Chart);

    }

    @Override
    protected void onDestroy () {
        destroyed = true;
        loadExecutor.shutdownNow ();
        mainHandler.removeCallbacksAndMessages (null);
        super.onDestroy ();
    }

    private void requestGraphData (final int days) {
        final int generation = ++loadGeneration;
        final String id = stockId;
        try {
            loadExecutor.execute (() -> {
                ArrayList<Double> values = new ArrayList<> ();
                ArrayList<DateTime> dates = new ArrayList<> ();
                ArrayList<Double> smaValues = new ArrayList<> ();
                try {
                    parseGraphData (getGraphDataFromDB (id, days), values, dates);
                } catch (RuntimeException e) {
                    Log.w (TAG, "Failed to load graph data for " + id, e);
                    values.clear ();
                    dates.clear ();
                }
                try {
                    smaValues = calculateSMA (getGraphDataFromDB (id, 250), 60);
                } catch (RuntimeException e) {
                    Log.w (TAG, "Failed to calculate SMA for " + id, e);
                    smaValues = new ArrayList<> ();
                }
                final ArrayList<Double> smaResult = smaValues;
                mainHandler.post (() -> onGraphDataLoaded (generation, days, values, dates, smaResult));
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void onGraphDataLoaded (int generation, int days, ArrayList<Double> values,
                                    ArrayList<DateTime> dates, ArrayList<Double> smaValues) {
        if (destroyed || isFinishing () || generation != loadGeneration)
            return;
        days2Chart = days;
        stockValues = values;
        stockDates = dates;
        sma = smaValues;
        calculateAndDisplayCoordinates ();
    }


    protected ArrayList<String> getGraphDataFromDB (String id, int days) {
        ArrayList<String> histDataList = new ArrayList<> ();
        try (Cursor cursor = StockListDB.getInstance (getApplicationContext ()).readHistoricalStockData (id, days)) {
            while (cursor.moveToNext ()) {
                histDataList.add (cursor.getString (2));
            }
        }
        return histDataList;
    }

    static void parseGraphData (List<String> jsonObjects, List<Double> values, List<DateTime> dates) {

        for (int i = 0; i < jsonObjects.size (); i++) {
            double value;
            long tmp;
            try {
                JSONObject jsonObj = new JSONObject (jsonObjects.get (i));
                String close = jsonObj.getString ("close");
                value = "null".equals (close) ? 0.0 : Double.parseDouble (close);
                tmp = Long.parseLong (jsonObj.get ("date").toString ());
            } catch (JSONException | NumberFormatException | NullPointerException e) {
                continue;
            }
            values.add (value);
            dates.add (new DateTime (new Date (tmp * 1000l)));
        }
    }

    private void calculateAndDisplayCoordinates () {
        TextView xs;
        TextView ys;
        xValuesLLayout.removeAllViews ();
        yValuesLLayout.removeAllViews ();
        graphViewFrameLayout.removeAllViews ();

        TextView headLine = (TextView) findViewById (R.id.headline_textView);
        headLine.setText (stockName.toUpperCase (Locale.ROOT) + " (" + days2Chart + " days)         RSI: " + rsi);
        headLine.setTextColor (Color.YELLOW);
        headLine.setTextSize (25.0f);
        headLine.setGravity (Gravity.CENTER_HORIZONTAL);
        headLine.setBackgroundColor (Color.TRANSPARENT);

        if (stockValues.size () < 2) {
            Toast.makeText (this, R.string.graph_not_enough_data, Toast.LENGTH_LONG).show ();
            return;
        }

        ArrayList<Double> yCoords = new ArrayList<> (stockValues);
        Collections.sort (stockDates);
        Collections.sort (yCoords);

        double max = 0.0f;
        double min = 0.0f;
        double midlle = 0.0f;

        int numberOfYs = yCoords.size ();
        double[] yCoordsArr = new double[Math.max (3, numberOfYs)];
        float yWeight = 0.5f;

        max = Collections.max (yCoords);
        min = Collections.min (yCoords);
        midlle = (max + min) / 2;
        double delta = (max - min) / (numberOfYs - 1);


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
                xs.setGravity (Gravity.CENTER_HORIZONTAL);
                xs.setLayoutParams (new LinearLayout.LayoutParams (0, 140, 1.0f));

                xValuesLLayout.addView (xs);
            }
        } else {
            for (DateTime date : stockDates) {


                xs = new TextView (getApplicationContext ());
                xs.setText (date.toString (DateTimeFormat.forPattern ("d MMM")).replace (".", ""));
                xs.setTextColor (Color.WHITE);
                xs.setGravity (Gravity.CENTER_HORIZONTAL);
                xs.setLayoutParams (new LinearLayout.LayoutParams (0, 140, 1.0f));

                xValuesLLayout.addView (xs);
            }
        }
        alignXAxisLabels ();
        graphViewFrameLayout.addView (new ChartView (getApplicationContext ()));
    }

    // centres the equal-width label cells on the chart's first..last point x positions
    private void alignXAxisLabels () {
        int width = graphViewFrameLayout.getWidth ();
        if (width == 0)
            return;
        int[] insets = AxisLabelLayout.insets (width, graphViewFrameLayout.getPaddingStart (),
                graphViewFrameLayout.getPaddingEnd (), xValuesLLayout.getChildCount ());
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) xValuesLLayout.getLayoutParams ();
        if (params.getMarginStart () == insets[0] && params.getMarginEnd () == insets[1])
            return;
        // negative margins let edge cells extend past the screen so their centres still match the chart
        params.setMarginStart (insets[0]);
        params.setMarginEnd (insets[1]);
        xValuesLLayout.setLayoutParams (params);
    }

    static ArrayList<Double> calculateSMA (List<String> histDataList, int days) {

        ArrayList<Double> sma = new ArrayList<> ();

        for (int i = 0; i < histDataList.size () - days; i++) {
            double average = calculateAverage (histDataList, i, days);
            if (!Double.isNaN (average))
                sma.add (average);
        }

        Collections.reverse (sma);
        return sma;
    }

    private static double calculateAverage (List<String> data, int i, int days) {
        double theAverage = 0.0;
        int count = 0;

        for (int j = i; j < (days + i); j++) {

            if (j > data.size () - 1) {
                break;
            }
            try {
                JSONObject jsonObject = new JSONObject (data.get (j));
                theAverage = theAverage + Double.parseDouble (jsonObject.get ("close").toString ());
                count++;
            } catch (JSONException | NumberFormatException | NullPointerException e) {
                e.printStackTrace ();
            }
        }
        return count == 0 ? Double.NaN : theAverage / count;
    }

    private class ChartView extends View {

        double lineCoords[] = new double[(stockValues.size () + stockDates.size ())];

        public ChartView (Context context) {
            super (context);
            setBackgroundColor (Color.WHITE);
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
                    double tmp = delta == 0 ? h / 2.0 : ((diff / delta) * h);

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

            for (int i = 0; i < (lineCoords.length - 2); i = i + 2) {

                float startX = (float) lineCoords[i];
                float startY = (float) lineCoords[i + 1];

                float endX = (float) lineCoords[i + 2];
                float endY = (float) lineCoords[i + 3];

                canvas.drawLine (startX, startY, endX, endY, paint);

            }


            //if(lineCoords.length > 100 ) {
            int k = 2;
            for (int j = sma.size (); j > 2 && deltaY != 0; j--) {

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

package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.database.Cursor;

import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;

/**
 * Created by AHI003 on 27-09-2017.
 */

public class RSI {
    private final ArrayList<Averages> avgList;
    private final ArrayList<Double> prices;
    private int periodLength;


    public RSI (int periodLength, String symbol, StockListDB db, String latestDayRate) {
        super ();
        this.periodLength = periodLength;
        avgList = new ArrayList<Averages> ();
        prices = getPrices (symbol, db);
        prices.add (Double.parseDouble (latestDayRate));

    }

    private ArrayList getPrices (String symbol, StockListDB db) {

        ArrayList localAL = new ArrayList ();

        Cursor cursor = db.getRSIData (symbol);

        while (cursor.moveToNext ()) {
            JSONObject jsonObject = null;
            try {
                DateTime date = new DateTime (1000l * cursor.getLong (0));
                jsonObject = new JSONObject (cursor.getString (1));
                localAL.add (Double.parseDouble (jsonObject.get ("close").toString ()));
            } catch (JSONException e) {
                e.printStackTrace ();
            }


        }
        return localAL;
    }


    public String calculate () {


        double value = 0;
        int pricesSize = prices.size ();
        int lastPrice = pricesSize - 1;
        int firstPrice = 0;

        double gains = 0;
        double losses = 0;
        double avgUp = 0;
        double avgDown = 0;

        double change = prices.get (firstPrice + 1)
                - prices.get (firstPrice);

        gains = Math.max (0, change);
        losses = Math.max (0, -change);

//        for (int i = 2 ; i < pricesSize ; i++){
        int i = 2;


        while (i < pricesSize) {

            change = prices.get (i)
                    - prices.get (i - 1);

            if (i > periodLength) {
                if (avgList.isEmpty ()) {

                    avgUp = gains / periodLength;
                    avgDown = losses / periodLength;
                    avgList.add (new Averages (avgUp, avgDown));
                } else {

                    gains = Math.max (0, change);
                    losses = Math.max (0, -change);

                    Averages avg = avgList.get (avgList.size () - 1);
                    avgUp = avg.getAvgUp ();
                    avgDown = avg.getAvgDown ();
                    avgUp = ((avgUp * (periodLength - 1)) + gains) / (periodLength);
                    avgDown = ((avgDown * (periodLength - 1)) + losses)
                            / (periodLength);
                    avgList.add (new Averages (avgUp, avgDown));

                }
            } else {

                gains += Math.max (0, change);
                losses += Math.max (0, -change);
            }
            i++;
        }
        value = 100 - (100 / (1 + (avgUp / avgDown)));

        NumberFormat formatter = new DecimalFormat ("#0.00");
        return formatter.format (value);
    }

    private class Averages {

        private final double avgUp;
        private final double avgDown;

        public Averages (double up, double down) {
            this.avgDown = down;
            this.avgUp = up;
        }

        public double getAvgUp () {
            return avgUp;
        }

        public double getAvgDown () {
            return avgDown;
        }
    }
}

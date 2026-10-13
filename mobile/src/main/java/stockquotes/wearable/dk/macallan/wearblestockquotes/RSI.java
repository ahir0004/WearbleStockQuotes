package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by AHI003 on 27-09-2017.
 */

public class RSI {
    public static final String NOT_AVAILABLE = "-";

    private final List<Double> prices;
    private final int periodLength;


    public RSI (int periodLength, String symbol, StockListDB db, double latestPrice) {
        super ();
        this.periodLength = periodLength;
        prices = getPrices (symbol, db);
        if (!Double.isNaN (latestPrice) && !prices.isEmpty ())
            prices.add (latestPrice);

    }

    private List<Double> getPrices (String symbol, StockListDB db) {

        List<Double> localAL = new ArrayList<> ();

        try (Cursor cursor = db.getRSIData (symbol)) {
            while (cursor.moveToNext ()) {
                String json = cursor.getString (1);
                if (json == null) {
                    continue;
                }
                try {
                    localAL.add (Double.parseDouble (new JSONObject (json).get ("close").toString ()));
                } catch (JSONException | NumberFormatException e) {
                    e.printStackTrace ();
                }
            }
        }
        return localAL;
    }


    public String calculate () {
        double value = calculate (prices, periodLength);
        if (Double.isNaN (value))
            return NOT_AVAILABLE;

        NumberFormat formatter = new DecimalFormat ("#0.00", DecimalFormatSymbols.getInstance (Locale.US));
        return formatter.format (value);
    }

    /**
     * Wilder's RSI over the given closes (oldest first). Returns NaN when there are fewer than period + 1 closes.
     */
    public static double calculate (List<Double> closes, int period) {
        if (closes == null || period <= 0 || closes.size () < period + 1)
            return Double.NaN;

        double gains = 0;
        double losses = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes.get (i) - closes.get (i - 1);
            gains += Math.max (0, change);
            losses += Math.max (0, -change);
        }
        double avgUp = gains / period;
        double avgDown = losses / period;

        for (int i = period + 1; i < closes.size (); i++) {
            double change = closes.get (i) - closes.get (i - 1);
            avgUp = ((avgUp * (period - 1)) + Math.max (0, change)) / period;
            avgDown = ((avgDown * (period - 1)) + Math.max (0, -change)) / period;
        }

        if (avgDown == 0)
            return avgUp > 0 ? 100 : 50;
        return 100 - (100 / (1 + (avgUp / avgDown)));
    }
}

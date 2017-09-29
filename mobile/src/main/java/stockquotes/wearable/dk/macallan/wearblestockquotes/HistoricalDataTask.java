package stockquotes.wearable.dk.macallan.wearblestockquotes;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

/**
 * Created by AHI003 on 12-09-2017.
 */
class HistoricalDataTask extends AsyncTask<String, Void, String> {
    private int days2Chart = 250;
    private StockListDB histDB;

    public HistoricalDataTask (Context context) {
        super ();
        histDB = new StockListDB (context);
    }

    @Override
    protected String doInBackground (String... urls) {

        String stockId = urls[0].split ("::")[0];
        String stockCode = urls[0].split ("::")[1];

        StringBuilder sb = new StringBuilder ();
        sb.append (stockCode);

        Cursor cursor = histDB.readHistoricalStockData (stockId, 1);

        long j = 0;

        while (cursor.moveToNext ()) {
            j = cursor.getLong (4);
        }
        long milis = j;
        DateTime latestInsertDay = new DateTime (milis);

        if (latestInsertDay.toLocalDate ().isEqual (new DateTime ().toLocalDate ())) {
            return "";
        }

        String historicalRequest = String.format ("https://finance.yahoo.com/quote/%s/history?p=%s", sb.toString (), sb.toString ());

        StringBuilder chunks = new StringBuilder ();
        try {

            String page = Jsoup.connect (historicalRequest).maxBodySize (0).get ().outerHtml ()
                    .split ("\"HistoricalPriceStore\":")[1]
                    .split (",\"isPending\"")[0] + "}";

            JSONObject json = new JSONObject (page);
            JSONArray histArray = json.getJSONArray ("prices");

            for (int i = 0; i < days2Chart; i++) {
                JSONObject temp = histArray.getJSONObject (i);
                chunks.append (temp);
                chunks.append (":::");
                chunks.append (temp.get ("date"));
                chunks.append (":::");
                chunks.append (stockId);
                chunks.append (":::");
            }

        } catch (Exception e) {
            e.printStackTrace ();
        }

        return chunks.toString ();
    }

    @Override
    protected void onPostExecute (String res) {
        String[] historicalRatesAndDates = res.split (":::");

        if (historicalRatesAndDates.length > 1)
            for (int i = 0; i < historicalRatesAndDates.length; i = i + 3) {
                histDB.insertHistoricals (historicalRatesAndDates[i],
                        historicalRatesAndDates[i + 1], historicalRatesAndDates[i + 2]);
            }
    }
}

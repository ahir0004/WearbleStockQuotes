import org.joda.time.DateTime;

import java.text.SimpleDateFormat;

/**
 * Created by AHI003 on 03-08-2017.
 */

public class Test {

    public static void main (String args[]) {
        DateTime dt = new DateTime ();

        SimpleDateFormat sdf = new SimpleDateFormat ("MMM d yyyy");
        StringBuilder sb = new StringBuilder ();
        String endDate = sdf.format (dt.toDate ());
        String startDate = sdf.format (dt.minusDays (10).toDate ());
        sb.append (args[0]);
        sb.append (":");
        sb.append (args[1]);
        sb.append (" ");
        sb.append (endDate);
        sb.append (" ");
        sb.append (startDate);

        String[] theDates = sb.toString ().replaceAll ("\\.", "").split (" ");


        String historicalRequest = "https://www.google.com/finance/historical?q=%s&enddate=%s+%s,+%s&startdate=%s+%s,+%s";
        System.out.println (String.format (historicalRequest, theDates));

    }


}

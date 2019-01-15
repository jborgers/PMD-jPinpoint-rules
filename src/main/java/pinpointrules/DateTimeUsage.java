package pinpointrules;

import org.joda.time.DateTime;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateTimeUsage {

	void myMethod() {
		Date now = Calendar.getInstance().getTime();
		setDate(Calendar.getInstance().getTime());
		setDate(GregorianCalendar.getInstance().getTime());
		
		DateTime nowDT = DateTime.now();
		DateTime nowDT1 = new DateTime(GregorianCalendar.getInstance());
		DateTime nowDT2 = new DateTime(Calendar.getInstance());
		
		Calendar cal = Calendar.getInstance();
		Date now2 = cal.getTime();
		// check for NullPointerExceptions
		int warmestMonth = Calendar.getInstance().AUGUST;

		Date date = Calendar.getInstance().getTime();

		// PCC-172
		long time = Calendar.getInstance().getTimeInMillis();

		// PCC-172
		Long.toString(Calendar.getInstance().getTimeInMillis());

		Object[] all = new Object[] {now, nowDT, nowDT1, nowDT2, now2, warmestMonth, time};
		
	}
	
    private String toKey(final Date rekenDatum) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        return formatter.format(rekenDatum);
    }
	
	private void setDate(Date when){
		System.out.println("When: " + when);
	}
	

}

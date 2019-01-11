package pinpointrules;

import org.joda.time.format.*;

public class CreateDateTimeFormatterOnceTest {
    final org.joda.time.format.DateTimeFormatter wrong = ISODateTimeFormat.basicDate();
    static DateTimeFormatter wrongAgain = ISODateTimeFormat.basicDate();
    DateTimeFormatter stillWrong = ISODateTimeFormat.basicDate();
    static final DateTimeFormatter cigar = DateTimeFormat.forPattern("xxx");
    private static final DateTimeFormatter ok = DateTimeFormat.forPattern("xxx");
    public static final org.threeten.bp.format.DateTimeFormatter YMD_FORMATTER = org.threeten.bp.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");


    public void testViolation1(DateTimePrinter printer, DateTimeParser parser)  {
        DateTimeFormatter dtf = null;
        dtf = new DateTimeFormatter(printer, parser);
    }
    public void testViolation2()  {
        DateTimeFormatter dtf =  new DateTimeFormatterBuilder().toFormatter();
    }
    public void testViolation3()  {
        DateTimeFormatter dtf = ISODateTimeFormat.date();
    }
    public void testViolation4()  {
        stillWrong = DateTimeFormat.fullDateTime();
    }

    public void testViolation5()  {
        stillWrong = DateTimeFormat.forPattern("");
    }
    public void testViolation6(DateTimePrinter printer,DateTimeParser parser)  {
        stillWrong =  new DateTimeFormatter(printer, parser);
    }
    public void testViolation7_PCC_171() {
        long dt = 1L;
        String s = DateTimeFormat.forPattern("yyDDD").print(dt);
    }

    public DateTimeFormatter testNoViolation(){
        return null;
    }

    public void testNoViolation_JPCC_16(String dateFormat) {
        DateTimeFormatter dateTimeFormatter = getDateTimeFormatterFromCacheViolation(dateFormat);
    }

    private DateTimeFormatter getDateTimeFormatterFromCacheViolation(String dateFormat) {
        return new DateTimeFormatterBuilder().toFormatter(); // wrong
    }
    public void testNoViolation_2(String dateFormat) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(dateFormat);
    }
}

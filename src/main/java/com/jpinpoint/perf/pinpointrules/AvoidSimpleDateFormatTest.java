package com.jpinpoint.perf.pinpointrules;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AvoidSimpleDateFormatTest {

    private String toKey(final Date rekenDatum) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        return formatter.format(rekenDatum);
    }
}

package com.jpinpoint.perf.pinpointrules;

import java.text.ChoiceFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class AvoidDecimalAndChoiceFormatAsField {
    public static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("###.###");

    private final NumberFormat numFormat = new DecimalFormat("###.###");

    private double[] limits = {1, 2, 3, 4, 5, 6, 7};
    private String[] dayOfWeekNames = {"Sun", "Mon", "Tue", "Wed", "Thur", "Fri", "Sat"};
    private ChoiceFormat form = new ChoiceFormat(limits, dayOfWeekNames);

    public void shouldNotMatchInsideMethod() {
        NumberFormat format = new DecimalFormat("##.##");
        ChoiceFormat choiceFormat = new ChoiceFormat(limits, dayOfWeekNames);
    }
}

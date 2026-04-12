package me.allync.horizonblacksmith.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class NumberUtil {

    private static final DecimalFormat integerFormatter;
    private static final DecimalFormat doubleFormatter;

    static {

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("in", "ID"));
        integerFormatter = new DecimalFormat("#,###", symbols);
        doubleFormatter = new DecimalFormat("#,##0.0#", symbols);
    }

    public static String format(double number) {

        if (number == (long) number) {
            return integerFormatter.format(number);
        }
        return doubleFormatter.format(number);
    }

    public static String format(int number) {
        return integerFormatter.format(number);
    }

    public static String format(long number) {
        return integerFormatter.format(number);
    }
}

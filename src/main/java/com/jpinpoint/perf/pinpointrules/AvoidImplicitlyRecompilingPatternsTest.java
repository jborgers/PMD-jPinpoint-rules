package com.jpinpoint.perf.pinpointrules;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by BorgersJM on 14-8-2015.
 */
public class AvoidImplicitlyRecompilingPatternsTest {

    private static final String PAT_STRING = "(?=\\p{Lu})";
    private static final String PAT_STRING_SHORT = "/";
    private static final Pattern PATTERN = Pattern.compile(PAT_STRING); // ok
    private static final Matcher MATCHER = PATTERN.matcher("input"); // ok
    private static final PathMatcher PATH_MATCHER = FileSystems.getDefault().getPathMatcher("*.java)");
    private String dynPatString; // field should be transformed to Pattern
    private static final Path path = new File("fileName").toPath();
    private static final Pattern TRAILING_SLASH_OR_BACKSLASH = Pattern.compile("(/|\\\\)$");
    private static final String ERROR_MSG = "Error Message";
    private String context;

    void violatingSplit3x(String action) {
        String[] splitString = action.split("(?=\\p{Lu})"); // should precompile and use separate pattern, for str len > 1
        String[] splitString2 = action.split(PAT_STRING); // should precompile and use separate pattern
        String[] splitStringDynamic = action.split(dynPatString); // violation, field should be transformed to Pattern
    }

    void correctSplit(String action, String dyn) {
        String[] splitStringShort = action.split(","); // ok, short
        String[] splitStringDynamic = action.split(dyn); // ok, dynamic, cannot precompile
        PATTERN.split(action); // ok
    }


    void violatingReplaceAll3x(String action) {
        action.replaceAll("(?=\\p{Lu})", "-"); // should precompile and use separate pattern, for str len > 1
        action.replaceAll(PAT_STRING, "-"); // should precompile and use separate pattern
        //@TODO
        // action.replaceAll(PAT_STRING_SHORT, "-"); // regex short, OK
        action.replaceAll(dynPatString, ";"); // field should be pattern
    }

    void correctReplaceAll(String action, String dyn) {
        String dyn2 = dyn;
        action.replaceAll(",", ";"); // ok, regex short
        action.replaceAll(",", ";----;"); // ok, regex short
        action.replaceAll(dyn, ";"); // ok, dynamic, cannot precompile
        action.replace(dyn2, ";"); // ok, dynamic, cannot precompile
        MATCHER.replaceAll("------"); // ok, on Matcher, not on String
        Matcher m = PATTERN.matcher(action); // matcher creation is ok
        m.replaceAll("------"); // ok, on Matcher, not on String
    }

    void violatingReplaceFirst3x(String action) {
        action.replaceFirst("(?=\\p{Lu})", "-"); // should precompile and use separate pattern, for str len > 1
        action.replaceFirst(PAT_STRING, "-"); // should precompile and use separate pattern
        action.replaceFirst(dynPatString, ";"); // field should be Pattern
    }

    void correctReplaceFirst(String action, String dyn) {
        action.replaceFirst(",", ";"); // ok, regex short
        action.replaceFirst(",", ";----;"); // ok, regex short
        action.replaceFirst(dyn, ";"); // ok, dynamic, cannot precompile
    }

    // .matches on a String

    void violatingMatches4x(String action) {
        boolean b = action.matches("(?=\\p{Lu})"); // should precompile and use separate pattern, for str len > 1
        boolean c = action.matches(PAT_STRING); // should precompile and use separate pattern
        boolean d = action.matches(dynPatString); // field should be Pattern
        String action2 = action;
        boolean e = action2.matches(PAT_STRING); // should precompile and use separate pattern
    }

    void correctMatches(String action, String dyn) {
        boolean b = action.matches(","); // ok, short
        boolean c = action.matches(dyn); // ok, dynamic, cannot precompile
        boolean e = MATCHER.matches(); // ok, on Matcher, not on String
        Matcher m = PATTERN.matcher(action); // matcher creation is ok
        boolean f = m.matches(); // ok, on Matcher, not on String
    }

    // Pattern.matches

    void violatingPatternMatches3x(String action) {
        boolean b = Pattern.matches("(?=\\p{Lu})", action); // should precompile and use separate pattern, for str len > 1
        boolean c = Pattern.matches(PAT_STRING, action); // should precompile and use separate pattern
        boolean d = Pattern.matches(context, action); // also ok
    }

    void correctPatternMatches(String action, String dyn) {
        boolean b = Pattern.matches(",", action); // ok, short
        boolean c = Pattern.matches(dyn, action); // ok, dynamic, cannot precompile
    }

    void correctMatcher3x() {
        PATTERN.matcher("input").replaceAll("-"); //  Matcher created every time, pattern already compiled
        Matcher m = PATTERN.matcher("input"); //  Matcher created every time, pattern already compiled
        PATTERN.matcher(dynPatString).replaceAll("-"); //  could be Matcher field
    }

    void correctMatcher(String dyn) {
        MATCHER.replaceAll("-"); //  Matcher created before,
        boolean b = MATCHER.matches();
        Matcher m = PATTERN.matcher(","); //  Matcher created every time, yet short pattern
        PATTERN.matcher(dyn).replaceAll("-"); //  Matcher created every time, yet dynamic
    }

    public void violatingPathMatcher4x(String action) {
        PathMatcher p = FileSystems.getDefault().getPathMatcher("(?=\\p{Lu})"); // violation, should not be inside method, make static final
        FileSystems.getDefault().getPathMatcher(PAT_STRING); // violation
        FileSystems.getDefault().getPathMatcher(dynPatString).matches(path); //  field should be PathMatcher

        FileSystem fs = FileSystems.getDefault();
        p = fs.getPathMatcher(PAT_STRING); // violation
    }

    public void correctPathMatcher(String fileName, String dyn, PathMatcher pm) {
        Path p = new File(fileName).toPath();
        PATH_MATCHER.matches(p); //  Matcher created before,
        PATH_MATCHER.matches(path); //  Matcher created before,
        pm.matches(p); // PathMatcher dynamic
        FileSystems.getDefault().getPathMatcher(dyn).matches(p); //  Matcher created every time, yet dynamic
        PathMatcher q = FileSystems.getDefault().getPathMatcher("(" + dyn + "?=\\p{Lu})"); // ok, dynamic
        PathMatcher r = FileSystems.getDefault().getPathMatcher(String.format(PAT_STRING, dyn)); // ok, dynamic
        PathMatcher t = FileSystems.getDefault().getPathMatcher(dyn); // violation?
        FileSystem fs = FileSystems.getDefault();
        fs.getPathMatcher(dyn).matches(p); //  Matcher created every time, yet dynamic
    }

    /**
     * Case from Auto JPCC-59
     *
     * @param haystack
     * @param needle
     * @return
     */
    public static int countOccurrences_correct(String haystack, String needle) {
        if (haystack == null) return 0;
        if (needle == null) return 0;

        if (needle.contains("\\")) {
            needle = needle.replace("\\", "\\\\");
        }
        Pattern p = Pattern.compile(needle); // used to violate
        Matcher m = p.matcher(haystack);
        int count = 0;
        while (m.find()) {
            count += 1;
        }
        return count;
    }

    private static final String FREE_TEXT_PATTERN = "[A-Z\\s]+";

    /**
     * case JPCC-25
     */
    public static boolean isValid_violate1x(final String text) {
        if (text.length() > 0 && !Pattern.matches(FREE_TEXT_PATTERN, text.trim())) {
            return false;
        }
        return true;
    }

    public void testFromMCenterOk() {
        String oldContext = TRAILING_SLASH_OR_BACKSLASH.matcher(context).replaceAll("");
    }

    private static final String RUN_ID_SERVER_SEPARATOR = "--";

    public void testFromAutoOk(String nameOne) {
        String serverOne = nameOne.split(RUN_ID_SERVER_SEPARATOR)[1]; // false postive to solve
    }

    //from pi
    private static final int EXPONENT = 2;
    private static final int TEN = 10;
    private static final int ZERO = 0;
    private static final Pattern DIGITS_ONLY_REGEX = Pattern.compile("[^0-9]");
    private static final String EMPTY_STRING = "";

    // False positive from pi
    private String getLastTenDigitsOfNumberOk(final String number) {
        String numberDigits = DIGITS_ONLY_REGEX.matcher(number).replaceAll(EMPTY_STRING);
        if (numberDigits.length() > TEN) {
            return numberDigits.substring(numberDigits.length() - TEN);
        } else if (numberDigits.length() == ZERO) {
            return String.valueOf(ZERO);
        }
        return numberDigits;
    }

    // from work group
    private final Pattern LINE_BREAK_PATTERN = Pattern.compile(">\\s+<");
    private static final String LINE_BREAK_REPLACEMENT = "><";

    private String fromRLOk(String inputMessage) {
        inputMessage = LINE_BREAK_PATTERN.matcher(inputMessage).replaceAll(LINE_BREAK_REPLACEMENT);
        return inputMessage;
    }

    // false positive from VS
    private void stringSplitNonRegexOK() {
        String string = "Blah Error Message Blah2 Error Message Blah3";
        String[] parts = string.split("Error Message");
        String[] partsField = string.split(ERROR_MSG);
    }



    /* ignoring String.replace - strange to replace by Pattern.compile explicitely
    void violatingReplace(String action) {
        action.replace("(?=\\p{Lu})", "-"); // should precompile and use separate pattern, for str len > 1
        action.replace(PAT_STRING, "-"); // should precompile and use separate pattern
    }

    void correctReplace(String action, String dyn) {
        action.replace(",", ";"); // ok, regex short
        action.replace(",", ";----;"); // ok, regex short
        action.replace(dyn, ";"); // ok, dynamic, cannot precompile
    }*/

}

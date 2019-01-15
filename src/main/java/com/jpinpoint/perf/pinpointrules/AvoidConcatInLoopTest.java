package com.jpinpoint.perf.pinpointrules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.io.InputStream;
import java.util.*;

public class AvoidConcatInLoopTest {

    private static final String ROLE_PREFIX = "role-";
    private static final Logger logger = LoggerFactory.getLogger(AvoidConcatInLoopTest.class);

    public void testConcatInLoopDefect() {
        String logStatement = "";
        List<String> values = Arrays.asList("tic", "tac", "toe");
        for (String val : values) {
            logStatement = logStatement + val + ", ";
        }
        Iterator iter = values.iterator();
        while (iter.hasNext()) {
            logStatement = logStatement + iter.next() + ", ";
        }
    }

    public void testConcatInLoopCorrect() {
        int log = 0;
        List<Integer> values = Arrays.asList(new Integer[]{1, 2, 3});
        for (int val : values) {
            log = log + val;
        }
        Iterator<Integer> iter = values.iterator();
        while (iter.hasNext()) {
            log = log + iter.next();
        }
    }

    public void testConcatInLoopDefect2() {
        String log = "";
        List<String> values = Arrays
                .asList(new String[]{"tic", "tac", "toe"});
        for (String val : values) {
            log += val;
        }
    }

    public void testConcatInLoopCorrect2() {
        int log = 0;
        List<Integer> values = Arrays.asList(new Integer[]{1, 2, 3});
        for (int val : values) {
            log += val;
        }
    }


    public void testConcatInLoopDefect3FoundInAppend() {
        StringBuilder logStatement = new StringBuilder();
        List<String> values = Arrays
                .asList(new String[]{"tic", "tac", "toe"});
        for (String val : values) {
            logStatement.append(val + ", ");
        }
    }

    public void testConcatInLoopCorrect3() {
        StringBuilder logStatement = new StringBuilder();
        List<String> values = Arrays
                .asList(new String[]{"tic", "tac", "toe"});
        for (String val : values) {
            logStatement.append(val);
        }
    }

    public void testConcatInLoopDefect4() {
        String logStatement = "";
        List<String> values = Arrays
                .asList(new String[]{"tic", "tac", "toe"});
        for (String val : values) {
            logStatement += val + ", ";
        }
    }

    /**
     * done
     */
    public String testConcatInLoopDefect5() {
        String description = " " + ";";
        List<String> persons = new ArrayList<String>();
        for (final String person : persons) {
            if (person != null) {
                description += "0" + ":";
            } else {
                description += ":";
            }
            description += person.toString() + ":";
            description += ";";
        }
        return description;
    }

    public void writePortfolioBodyCorrect() {
        double totalParticipationPercentage = 0;
        for (Object portfolioByCategory : new ArrayList()) {

            for (Object portfolioInstrumentDetails : new ArrayList()) {

                totalParticipationPercentage = totalParticipationPercentage
                        + (double) portfolioInstrumentDetails.hashCode();

                // putting portfolio line with its attributes in the columns csv

                // add tokens needed in csv file

            }
        }

    }

    public int testCorrectKruithof_indexOfCorrect(String keyName) {
        int index = 0;
        HashMap<String, String> columnsTypes = new HashMap<String, String>();
        for (String variableName : columnsTypes.keySet()) {
            if (keyName.equals(variableName)) {
                return index;
            }
            index += 1;
            //A String is concatenated in a loop. Use StringBuilder.append.
            //•	Open
        }
        throw new RuntimeException(String.format("Key '%s' not found in matrix.", keyName));
    }

    public long testLong_indexOfCorrect(String keyName) {
        long index = 0;
        HashMap<String, String> columnsTypes = new HashMap<String, String>();
        for (String variableName : columnsTypes.keySet()) {
            if (keyName.equals(variableName)) {
                return index;
            }
            index += 1;
            //A String is concatenated in a loop. Use StringBuilder.append.
            //•	Open
        }
        throw new RuntimeException(String.format("Key '%s' not found in matrix.", keyName));
    }

    public void testRDCorrect() {
        List<String> functionNames = Arrays.asList(new String[]{"a", "b"});
        for (final String functionName : functionNames) {
            if (true) {
                functionNames.add(ROLE_PREFIX + functionName);
            }
        }
    }

    public static void testROMInitLoggingCorrect(ServletContext servletContext) {
        String propertyFile = servletContext.getInitParameter("X");
        String[] properyFilenames = propertyFile.split(",");
        for (String propertyFilename : properyFilenames) {
            if (propertyFilename != null) {
                try {
                    InputStream inputStream = AvoidConcatInLoopTest.class.getClassLoader().getResourceAsStream(propertyFilename);
                } catch (Exception e) {
                    logger.error("Failed to load propertyFile with name " + propertyFilename + ": ", e);
                }
            }
        }
    }

    public static void testROMInitLoggingCorrect2(ServletContext servletContext) {
        String propertyFile = servletContext.getInitParameter("X");
        String[] properyFilenames = propertyFile.split(",");
        for (String propertyFilename : properyFilenames) {
            if (propertyFilename != null) {
                propertyFilename = propertyFilename.trim();
                try {
                    InputStream inputStream = AvoidConcatInLoopTest.class.getClassLoader().getResourceAsStream(propertyFilename);
                } catch (Exception e) {
                    logger.error("Failed to load propertyFile with name " + propertyFilename + ": ", e);
                }
            }
        }
    }

    public static void correctAfov_PCC_477() {
        List<String> linkNames = new ArrayList<String>();
        Map<String, String> messages = new HashMap<String, String>();
        // Get texts from RetrieveLinkMessage service
        for (String linkName : linkNames) {
            // Object link = getFtmLink(linkName);
            messages.put(linkName + ".url", "url");
            messages.put(linkName + ".description", "desc");
        }
    }

    public static void correct2() {
        List<String> linkNames = new ArrayList<String>();
        Map<String, String> messages = new HashMap<String, String>();
        String URL = "", DESCRIPTION = "";
        for (String linkName : linkNames) {
            if (!messages.containsKey(linkName + URL)) {
                messages.put(linkName + URL, "some");
            }
            if (!messages.containsKey(linkName + DESCRIPTION)) {
                messages.put(linkName + DESCRIPTION,
                        "some");
            }
        }
    }
}


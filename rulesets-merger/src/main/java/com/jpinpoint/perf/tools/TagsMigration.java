package com.jpinpoint.perf.tools;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagsMigration {

    public static final Pattern VALUE_PATTERN = Pattern.compile("value=\"(.*?)\"");

    public static void main(String[] args) {

        try {
            // get the directory path
            String directoryPath = "src/main/resources/category/java";
            File dir = new File(directoryPath);

            // Get all the files from a directory.
            File[] fileList = dir.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isFile() && file.getName().endsWith(".xml")) {
                        // create a list of lines from file
                        List<String> lines = Files.readAllLines(Paths.get(file.getPath()), StandardCharsets.UTF_8);

                        // call the migrate function
                        List<String> newLines = migrate(lines);

                        // write the new lines into a new file
                        Files.write(Paths.get(directoryPath + "/new_" + file.getName()), newLines, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static List<String> migrate(List<String> lines) {

        boolean propertiesStarted = false;
        List<String> newLines = new ArrayList<>();
        List<String> currentPropertiesLines = new ArrayList<>();

        for (String line : lines) {
            if (line.contains("<properties")) { // collect all lines between the <properties> tags
                currentPropertiesLines = new ArrayList<>();
                newLines.add(line);
                propertiesStarted = true;
            } else if (line.contains("</properties")) {
                newLines.addAll(processProperties(currentPropertiesLines));
                newLines.add(line);
                propertiesStarted = false;
            } else if (propertiesStarted) {
                currentPropertiesLines.add(line);
            } else {
                newLines.add(line);
            }
        }
        return newLines;
    }

    /**
     * <p>All lines between the <pre><properties></properties></pre> tags are expected as input.</p>
     *
     * <p>All values of properties with <pre>name="tag"</pre> will be put into one property with <pre>name="tags"</pre>.
     * The type and description fields of the property lines are ignored, the final tags property has:
     * <pre>type="String" description="classification"</pre>.</p>
     */
    private static List<String> processProperties(List<String> propertiesLines) {
        List<String> newPropertiesLines = new ArrayList<>();
        List<String> tags = new ArrayList<>();

        for (String line : propertiesLines) {
            if (line.contains("<property name=\"tag\"")) { // assumes the property is on one line
                Matcher matcher = VALUE_PATTERN.matcher(line);
                if (matcher.find()) {
                    String tag = matcher.group(1);
                    tags.add(tag);
                }
            }
            else {
                newPropertiesLines.add(line);
            }
        }
        // re-add the version tag for PMD-6
        newPropertiesLines.add("            <property name=\"version\" value=\"2.0\"/>");
        if (!tags.isEmpty()) {
            Collections.sort(tags);
            newPropertiesLines.add(String.format("            <property name=\"tags\" value=\"%s\" type=\"String\" description=\"classification\"/>", StringUtils.join(tags, ",")));
        }
        return newPropertiesLines;
    }

}

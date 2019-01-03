package com.jpinpoint.perf.tools;

import org.apache.commons.io.IOUtils;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Merger tool for merging company specific custom rules with the generic jpinpoint rules.
 */
public class RulesetMerger {
    // parameters likely to have to change for a different company
    private static final String PATH_TO_GEN_RULES = "../../../PMD-jPinpoint-rules/rulesets/java/";
    private static final String PATH_TO_SPEC_RULES = "../../rulesets/java/";
    private static final String COMPANY_SPECIFIC = "Company-X";
    private static final String COMPANY_DOC_ROOT = "https://confluence.company-X.com/..";
    private static final boolean USE_COMPANY_SPEC_NAME_AND_DOC = false;

    // jpinpoint-rules.xml + companyX-rules.xml --> comp-jpinpoint-rules.xml
    private static final String RESULT_RULES_NAME = "comp-jpinpoint-rules";
    private static final File RULES_FILE_1 = new File(PATH_TO_GEN_RULES + "jpinpoint-rules.xml");
    private static final File RULES_FILE_2 = new File(PATH_TO_SPEC_RULES + "companyX-rules.xml");

    // constants unlikely to have to change
    private static final File RESULT_FILE = new File(PATH_TO_SPEC_RULES + RESULT_RULES_NAME + ".xml");
    private static final String RESULT_START_LINE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String RESULT_END_LINE = "</ruleset>";
    private static final String RESULT_RULE_SET_LINE = "<ruleset name=\"" + RESULT_RULES_NAME + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://pmd.sourceforge.net/ruleset/2.0.0 http://pmd.sourceforge.net/ruleset_2_0_0.xsd\" xmlns=\"http://pmd.sourceforge.net/ruleset/2.0.0\" xmlns:fn=\"http://www.w3.org/TR/xpath-functions/\">";
    private static final String RESULT_DESC_LINE = "<description>" + COMPANY_SPECIFIC + " specific rules merged with the jpinpoint rules for performance aware Java coding, sponsored by Rabobank.</description>";
    private static final String LSEP = IOUtils.LINE_SEPARATOR;


    public static void main(String[] args) {
        try(InputStream is1 = new FileInputStream(RULES_FILE_1); InputStream is2 = new FileInputStream(RULES_FILE_2)) {
            List<String> file1Lines = IOUtils.readLines(is1, Charset.defaultCharset());
            List<String> file2Lines = IOUtils.readLines(is2, Charset.defaultCharset());
            List<String> mergedFileLines = new ArrayList<String>(file1Lines.size() + file2Lines.size());
            mergedFileLines.add(RESULT_START_LINE);
            mergedFileLines.add(RESULT_RULE_SET_LINE);
            mergedFileLines.add(RESULT_DESC_LINE);
            mergedFileLines.add("");
            List<List<String>> ruleLinesList1 = parseIntoRuleLinesList(file1Lines);
            List<List<String>> ruleLinesList2 = parseIntoRuleLinesList(file2Lines);

            for (List<String> ruleLines : ruleLinesList1) {
                mergedFileLines.addAll(ruleLines);
                mergedFileLines.add("");
            }
            for (List<String> ruleLines : ruleLinesList2) {
                mergedFileLines.addAll(ruleLines);
                mergedFileLines.add("");
            }
            mergedFileLines.add(RESULT_END_LINE);

            if (USE_COMPANY_SPEC_NAME_AND_DOC) {
                mergedFileLines = replaceNameAndDoc(mergedFileLines);
            }
            IOUtils.writeLines(mergedFileLines, LSEP, new FileOutputStream(RESULT_FILE), Charset.defaultCharset());
            System.out.println("Merged '" + RULES_FILE_1.getPath() +"' and '" + RULES_FILE_2.getPath() + "' into '" + RESULT_FILE.getPath() + "'." );
        }
        catch(IOException e) {
            System.out.println("Error while trying to merge '" + RULES_FILE_1.getPath() + "' and '" + RULES_FILE_2.getPath() + "' into '" + RESULT_FILE.getPath() + "'.");
            System.out.println(e);
        }
    }

    private static List<String> replaceNameAndDoc(List<String> mergedFileLines) {
        List<String> fileLinesWithReplaced = new ArrayList<String>(mergedFileLines.size());
        for(String line : mergedFileLines) {
            line = line.replace("(jpinpoint-rules)", "(" + RESULT_RULES_NAME + ")");
            line = line.replace("http://www.jpinpoint.com/doc", COMPANY_DOC_ROOT);
            fileLinesWithReplaced.add(line);
        }
        return fileLinesWithReplaced;
    }

    private static List<List<String>> parseIntoRuleLinesList(List<String> fileLines) {
        boolean ruleStarted = false;
        List currentRuleLines = new ArrayList();
        List<List<String>> ruleLinesList = new ArrayList<>();
        for(String line : fileLines) {
            if (line.contains("<rule name")) {
                currentRuleLines = new ArrayList();
                currentRuleLines.add(line);
                ruleStarted = true;
            }
            else if (line.contains("</rule>")) {
                currentRuleLines.add(line);
                ruleLinesList.add(currentRuleLines);
                ruleStarted = false;
            }
            else if (ruleStarted) {
                currentRuleLines.add(line);
            }
        }
        return ruleLinesList;
    }
}

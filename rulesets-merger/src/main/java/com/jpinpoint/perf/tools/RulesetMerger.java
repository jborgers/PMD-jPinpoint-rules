package com.jpinpoint.perf.tools;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merger tool for merging categories and company specific custom rules with the generic jpinpoint rules.
 */
public class RulesetMerger {

    // parameters likely to have to change for a different company
    private static final String COMPANY_SPECIFIC = "jPinpoint";
    private static final String RESULT_COMPANY_RULES_NAME = "jpinpoint-rules";
    private static final String RESULT_ALL_RULES_NAME = "jpinpoint-rules";
    private static final String PATH_TO_SPEC_RULES = "src/main/resources/category/java/";
    private static final String COMPANY_DOC_ROOT = "http://www.jpinpoint.com/doc";
    private static final boolean IS_ADD_TAG_TO_DESCRIPTION_AND_DOC = true;

    // constants unlikely to have to change
    private static final String MERGED_RULESETS_DIR = "rulesets/java";
    private static final String RESULT_START_LINE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String RESULT_END_LINE = "</ruleset>";
    private static final String DESCRIPTION_END_TAG = "</description>";
    private static final String RESULT_RULE_SET_LINE_TEMPLATE = "<ruleset name=\"%s\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xsi:schemaLocation=\"http://pmd.sourceforge.net/ruleset/2.0.0 http://pmd.sourceforge.net/ruleset_2_0_0.xsd\" " +
            "xmlns=\"http://pmd.sourceforge.net/ruleset/2.0.0\" " +
            "xmlns:fn=\"http://www.w3.org/TR/xpath-functions/\">";
    private static final String MERGED_WITH_TEMPLATE = "merged with the %s ";
    private static final String RESULT_DESC_LINE_TEMPLATE = "<description>%s specific rules %sfor performance aware Java coding, sponsored by Rabobank.</description>";
    private static final String LSEP = IOUtils.LINE_SEPARATOR;
    private static final String BEGIN_INCLUDED_FILE_COMMENT_TEMPLATE = "<!-- BEGIN Included file '%s' -->";
    private static final String END_INCLUDED_FILE_COMMENT_TEMPLATE = "<!-- END Included file '%s' -->";
    private static final String DESCRIPTION_END_TAG_PATTERN = DESCRIPTION_END_TAG.replace('<','.').replace('>', '.');
    public static final Pattern TAGGED_DESCRIPTION_END_TAG_PATTERN = Pattern.compile("\\(.*\\) *" + DESCRIPTION_END_TAG_PATTERN);

    public static void main(String[] args) {

        // Find our project base working directory (differs when running from jar or using your IDE to run this main)
        File repositoryBaseDir = getRepositoryBaseDir();
        String resultRulesName = RESULT_COMPANY_RULES_NAME; // default
        File optionalExternalRulesFile = null;
        String mergeWithRepositoryName = null;
        if (args.length >= 3) {
            // Check if the other project we use for merging the rules is present
            mergeWithRepositoryName = args[0];
            String repositoryRelativeRulesDir = args[1];
            String repositoryRulesFilename = args[2];
            System.out.println("INFO: arguments (3) are specified. Looking for: ");
            System.out.println(String.format("\t repository directory name:           %s", mergeWithRepositoryName));
            System.out.println(String.format("\t repository relative rules directory: %s", repositoryRelativeRulesDir));
            System.out.println(String.format("\t repository rules file name:          %s", repositoryRulesFilename));

            File projectDir = repositoryBaseDir.getParentFile();
            File githubProjectDir = new File(projectDir, mergeWithRepositoryName);
            if (!githubProjectDir.exists() || !githubProjectDir.isDirectory()) {
                System.out.println(String.format("ERROR: expected the external rules project to be available at '%s'", githubProjectDir.getAbsolutePath()));
                System.exit(1);
            }
            File githubProjectRulesDir = new File(githubProjectDir, repositoryRelativeRulesDir);
            if (!githubProjectRulesDir.exists() || !githubProjectRulesDir.isDirectory()) {
                System.out.println(String.format("ERROR: expected the external rules project to have the following directory structure present '%s'", githubProjectRulesDir.getAbsolutePath()));
                System.exit(1);
            }
            final File rulesFile1 = new File(githubProjectRulesDir, repositoryRulesFilename);
            if (!rulesFile1.exists()) {
                System.out.println(String.format("ERROR: Cannot find rules file '%s'", rulesFile1.getAbsolutePath()));
                System.exit(1);
            }
            optionalExternalRulesFile = rulesFile1;
            resultRulesName = RESULT_ALL_RULES_NAME;
        }

        // Check our project structure
        File ourProjectRulesDir = new File(repositoryBaseDir, PATH_TO_SPEC_RULES);
        if (!ourProjectRulesDir.exists() || !ourProjectRulesDir.isDirectory()) {
            System.out.println(String.format("ERROR: expected the rules to be present in '%s'", ourProjectRulesDir.getAbsolutePath()));
            System.exit(1);
        }
        String[] ourRulesFiles = ourProjectRulesDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        });
        Arrays.sort(ourRulesFiles);
        if (ourRulesFiles.length == 0) {
            System.out.println(String.format("ERROR: no rules files (*.xml) found in '%s'", ourProjectRulesDir.getAbsolutePath()));
            System.exit(1);
        }
        File outputDir = new File(repositoryBaseDir, MERGED_RULESETS_DIR);
        if (!outputDir.exists() || !outputDir.isDirectory()) {
            System.out.println(String.format("ERROR: expected the output directory ('%s') for storing merged results to be exist", outputDir.getAbsolutePath()));
            System.exit(1);
        }
        File resultFile = new File(outputDir, resultRulesName + ".xml");
        if (resultFile.exists()) {
            if (resultFile.canWrite()) {
                System.out.println(String.format("INFO: merging will overwrite the existing rules file at '%s'", resultFile.getAbsolutePath()));
            } else {
                System.out.println(String.format("ERROR: expected the output file ('%s') for storing merged results to be writable", resultFile.getAbsolutePath()));
                System.exit(1);
            }
        }
        if ((optionalExternalRulesFile != null) && (optionalExternalRulesFile.equals(resultFile))) {
            System.out.println(String.format("INFO: skipping processing '%s' to prevent we get double entries in our output!", optionalExternalRulesFile.getName()));
            optionalExternalRulesFile = null;
            mergeWithRepositoryName = null;
        }


        try {
            List<String> mergedFileLines = new ArrayList<String>();
            mergedFileLines.add(RESULT_START_LINE);
            mergedFileLines.add(String.format(RESULT_RULE_SET_LINE_TEMPLATE, resultRulesName));
            String mergeWithText = mergeWithRepositoryName == null ? "" : String.format(MERGED_WITH_TEMPLATE, mergeWithRepositoryName);
            String resultDescription = String.format(RESULT_DESC_LINE_TEMPLATE, COMPANY_SPECIFIC, mergeWithText);
            mergedFileLines.add(resultDescription);
            mergedFileLines.add("");
            mergedFileLines.add("<!-- IMPORTANT NOTICE: The content of this file is generated. Do not edit this file directly since changes may be lost when this file is regenerated! -->");
            mergedFileLines.add("");

            if (optionalExternalRulesFile != null) {
                try (InputStream is1 = new FileInputStream(optionalExternalRulesFile)) {
                    System.out.println(String.format("INFO: processing '%s'", optionalExternalRulesFile.getName()));
                    List<String> file1Lines = IOUtils.readLines(is1, Charset.defaultCharset());

                    List<List<String>> ruleLinesList1 = parseSortIntoRuleLinesList(file1Lines);
                    mergedFileLines.add(String.format(BEGIN_INCLUDED_FILE_COMMENT_TEMPLATE, optionalExternalRulesFile.getName()));
                    for (List<String> ruleLines : ruleLinesList1) {
                        mergedFileLines.addAll(ruleLines);
                        mergedFileLines.add("");
                    }
                    mergedFileLines.add(String.format(END_INCLUDED_FILE_COMMENT_TEMPLATE, optionalExternalRulesFile.getName()));
                }
            }

            for (String eachFileName: ourRulesFiles) {
                try(InputStream eachRuleStream = new FileInputStream(new File(ourProjectRulesDir, eachFileName))) {
                    System.out.println(String.format("INFO: processing '%s'", eachFileName));
                    List<String> file2Lines = IOUtils.readLines(eachRuleStream, Charset.defaultCharset());
                    List<List<String>> ruleLinesList2 = parseSortIntoRuleLinesList(file2Lines);
                    mergedFileLines.add(String.format(BEGIN_INCLUDED_FILE_COMMENT_TEMPLATE, eachFileName));
                    for (List<String> ruleLines : ruleLinesList2) {
                        mergedFileLines.addAll(ruleLines);
                        mergedFileLines.add("");
                    }
                    mergedFileLines.add(String.format(END_INCLUDED_FILE_COMMENT_TEMPLATE, eachFileName));
                }
            }

            mergedFileLines.add(RESULT_END_LINE);

            if (IS_ADD_TAG_TO_DESCRIPTION_AND_DOC) {
                mergedFileLines = addTagToDescriptionAndDoc(mergedFileLines, String.format("(%s)%s", resultRulesName, DESCRIPTION_END_TAG));
            }

            IOUtils.writeLines(mergedFileLines, LSEP, new FileOutputStream(resultFile), Charset.defaultCharset());
            System.out.println(String.format("INFO: merged files into '%s'.", resultFile.getPath()));
        }
        catch(IOException e) {
            System.out.println(String.format("Error while trying to merge rules into '%s'.", resultFile.getPath()));
            System.out.println(e);
        }
    }

    private static File getRepositoryBaseDir() {
        File currentWorkingDir = new File(".").getAbsoluteFile().getParentFile();
        File repositoryBaseDirCandidate = currentWorkingDir;
        while ((repositoryBaseDirCandidate != null) && !(new File(repositoryBaseDirCandidate, "README.md").exists())) {
            repositoryBaseDirCandidate = repositoryBaseDirCandidate.getParentFile();
        }
        if (repositoryBaseDirCandidate == null) {
            System.out.println(String.format("WARNING: expected current working directory ('%s') to contain the base of this project", currentWorkingDir.getAbsolutePath()));
            // don't return null!!!
            repositoryBaseDirCandidate = currentWorkingDir;
        }
        return repositoryBaseDirCandidate;
    }

    /**
     * Tag rule descriptions with some clear reference such that it will look like the following:
     * <pre>
     *     - rule ...
     *       - ...
     *       - description
     *           ...
     *           (jpinpoint-rules)
     * </pre>
     * @param mergedFileLines to cleanup
     * @param taggedDescriptionEndTag
     * @return cleanedup lines
     */
    private static List<String> addTagToDescriptionAndDoc(List<String> mergedFileLines, String taggedDescriptionEndTag) {

        List<String> fileLinesWithReplaced = new ArrayList<String>(mergedFileLines.size());
        for(String line : mergedFileLines) {
            Matcher matcher = TAGGED_DESCRIPTION_END_TAG_PATTERN.matcher(line);
            if (!matcher.find()) {
                // not yet tag so add our tag
                line = line.replace(DESCRIPTION_END_TAG, taggedDescriptionEndTag);
            }
            // @TODO replace externalInfoUrl="..."
            line = line.replace("http://www.jpinpoint.com/doc", COMPANY_DOC_ROOT);
            fileLinesWithReplaced.add(line);
        }
        return fileLinesWithReplaced;
    }

    private static List<List<String>> parseSortIntoRuleLinesList(List<String> fileLines) {
        Map<String, List> nameToLines = new TreeMap<>();
        boolean ruleStarted = false;
        List currentRuleLines = Collections.emptyList();
        String currentRuleName = "<none>";
        for(String line : fileLines) {
            if (line.contains("<rule name")) {
                currentRuleLines = new ArrayList();
                currentRuleLines.add(line);
                currentRuleName = StringUtils.substringBetween(line, "\"");
                ruleStarted = true;
            }
            else if (line.contains("</rule>")) {
                currentRuleLines.add(line);
                nameToLines.put(currentRuleName, currentRuleLines);
                ruleStarted = false;
            }
            else if (ruleStarted) {
                currentRuleLines.add(line);
            }
        }
        return new ArrayList(nameToLines.values());
    }
}

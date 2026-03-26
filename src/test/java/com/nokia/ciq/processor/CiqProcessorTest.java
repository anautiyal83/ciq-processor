package com.nokia.ciq.processor;

import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.report.ReportFormat;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class CiqProcessorTest {

    private static final String CIQ_FILE  =
            "C:/Users/DELL/Desktop/SBC-Usecase-96FixedLineconfig/96_Fixed line configuration in SBC_CIQ.xlsx";
    private static final String RULES_FILE =
            "D:/Nokia/workspace/ciq-validator/src/main/resources/SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml";
    private static final String OUTPUT_DIR   = "target/processor-output";
    private static final String MOP_JSON_DIR = "target/processor-mop-json";
    private static final String NODE_TYPE    = "SBC";
    private static final String ACTIVITY     = "FIXED_LINE_CONFIGURATION";

    @BeforeClass
    public static void checkPrerequisites() {
        Assume.assumeTrue("CIQ file not found — skipping integration test",
                new File(CIQ_FILE).exists());
        Assume.assumeTrue("Rules file not found — skipping integration test",
                new File(RULES_FILE).exists());
    }

    @Test
    public void testProcessAllFormats() throws Exception {
        new File(OUTPUT_DIR).mkdirs();

        List<ReportFormat> formats = Arrays.asList(
                ReportFormat.JSON, ReportFormat.HTML, ReportFormat.MSEXCEL);

        ValidationReport report = new CiqProcessorImpl().process(
                CIQ_FILE, NODE_TYPE, ACTIVITY, RULES_FILE,
                OUTPUT_DIR, formats, MOP_JSON_DIR);

        assertNotNull("Report must not be null", report);
        assertNotNull("Status must not be null", report.getStatus());
        assertTrue("Status must be PASSED or FAILED",
                "PASSED".equals(report.getStatus()) || "FAILED".equals(report.getStatus()));

        // All three report files must be written
        String baseName = NODE_TYPE + "_" + ACTIVITY + "_validation-report";
        assertTrue("JSON report missing",  new File(OUTPUT_DIR, baseName + ".json").exists());
        assertTrue("HTML report missing",  new File(OUTPUT_DIR, baseName + ".html").exists());
        assertTrue("Excel report missing", new File(OUTPUT_DIR, baseName + ".xlsx").exists());

        // Report must have at least one sheet result
        assertFalse("Report has no sheet results", report.getSheets().isEmpty());

        System.out.println("=== Validation Result ===");
        System.out.println("Status:       " + report.getStatus());
        System.out.println("Total errors: " + report.getTotalErrors());
        System.out.println("Sheets:       " + report.getSheets().size());
    }

    @Test
    public void testChildOrderSegregation() throws Exception {
        new File(OUTPUT_DIR).mkdirs();
        new File(MOP_JSON_DIR).mkdirs();

        ValidationReport report = new CiqProcessorImpl().process(
                CIQ_FILE, NODE_TYPE, ACTIVITY, RULES_FILE,
                OUTPUT_DIR, Arrays.asList(ReportFormat.JSON), MOP_JSON_DIR);

        if (!"PASSED".equals(report.getStatus())) {
            System.out.println("Validation did not pass — child-order segregation skipped by processor");
            return;
        }

        // At least one child-order subfolder should exist
        File mopRoot = new File(MOP_JSON_DIR);
        String[] subDirs = mopRoot.list((d, n) -> new File(d, n).isDirectory());
        assertNotNull("mop-json-dir should exist", subDirs);
        assertTrue("At least one child-order subfolder expected", subDirs.length > 0);

        // Each subfolder must contain an index JSON
        for (String subDir : subDirs) {
            File folder = new File(mopRoot, subDir);
            String[] jsonFiles = folder.list((d, n) -> n.endsWith(".json"));
            assertNotNull(jsonFiles);
            assertTrue("Child-order folder '" + subDir + "' should contain JSON files",
                    jsonFiles.length > 0);

            boolean hasIndex = false;
            for (String f : jsonFiles) {
                if (f.contains("index")) { hasIndex = true; break; }
            }
            assertTrue("Child-order folder '" + subDir + "' should contain an index JSON", hasIndex);
        }

        System.out.println("Child-order folders: " + Arrays.toString(subDirs));
    }

    @Test
    public void testCliMain() {
        int rc = CiqProcessorMain.run(new String[]{
                "--ciq",        CIQ_FILE,
                "--node-type",  NODE_TYPE,
                "--activity",   ACTIVITY,
                "--rules",      RULES_FILE,
                "--output",     OUTPUT_DIR + "/cli",
                "--format",     "JSON,HTML"
        });
        // 0 = PASSED, 1 = FAILED; both are valid outcomes for the tool
        assertTrue("Expected exit code 0 or 1", rc == 0 || rc == 1);
    }

    @Test
    public void testMissingRequiredArgExitsWithError() {
        int rc = CiqProcessorMain.run(new String[]{
                "--ciq", CIQ_FILE
                // missing --node-type, --activity, --rules, --output
        });
        assertEquals("Missing required args should return exit code 1", 1, rc);
    }
}

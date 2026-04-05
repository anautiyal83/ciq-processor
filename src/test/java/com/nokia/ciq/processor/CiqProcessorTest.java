package com.nokia.ciq.processor;

import com.nokia.ciq.validator.model.ValidationReport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

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

        ValidationReport report = new CiqProcessorImpl().process(
                CIQ_FILE, NODE_TYPE, ACTIVITY, RULES_FILE,
                OUTPUT_DIR, "JSON,HTML,MSEXCEL", MOP_JSON_DIR);

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
                OUTPUT_DIR, "JSON", MOP_JSON_DIR);

        if (!"PASSED".equals(report.getStatus())) {
            System.out.println("Validation did not pass — child-order segregation skipped by processor");
            return;
        }

        // At least one child-order subfolder should exist
        File mopRoot = new File(MOP_JSON_DIR);
        String[] subDirs = mopRoot.list((d, n) -> new File(d, n).isDirectory());
        assertNotNull("mop-json-dir should exist", subDirs);
        assertTrue("At least one child-order subfolder expected", subDirs.length > 0);

        // Each subfolder must contain at least one JSON
        for (String subDir : subDirs) {
            File folder = new File(mopRoot, subDir);
            String[] jsonFiles = folder.list((d, n) -> n.endsWith(".json"));
            assertNotNull(jsonFiles);
            assertTrue("Child-order folder '" + subDir + "' should contain JSON files",
                    jsonFiles.length > 0);
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

    // -------------------------------------------------------------------------
    // MRF — ANNOUNCEMENT_LOADING
    // -------------------------------------------------------------------------

    private static final String MRF_CIQ_FILE  =
            "D:/Nokia/documents/CR_Automation/MRF/MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx";
    private static final String MRF_RULES_FILE =
            "src/main/resources/MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml";
    private static final String MRF_OUTPUT_DIR   = "target/mrf-processor-output";
    private static final String MRF_MOP_JSON_DIR = "../mop-generator-utility/target/mrf-mop-json";
    private static final String MRF_NODE_TYPE    = "MRF";
    private static final String MRF_ACTIVITY     = "ANNOUNCEMENT_LOADING";

    @Test
    public void testMrfValidation() throws Exception {
        Assume.assumeTrue("MRF CIQ file not found — skipping MRF validation test",
                new File(MRF_CIQ_FILE).exists());
        Assume.assumeTrue("MRF rules file not found — skipping MRF validation test",
                new File(MRF_RULES_FILE).exists());

        new File(MRF_OUTPUT_DIR).mkdirs();
        new File(MRF_MOP_JSON_DIR).mkdirs();

        ValidationReport report = new CiqProcessorImpl().process(
                MRF_CIQ_FILE, MRF_NODE_TYPE, MRF_ACTIVITY, MRF_RULES_FILE,
                MRF_OUTPUT_DIR, "JSON,HTML,MSEXCEL", MRF_MOP_JSON_DIR);

        assertNotNull("Report must not be null", report);
        assertNotNull("Status must not be null", report.getStatus());
        assertTrue("Status must be PASSED or FAILED",
                "PASSED".equals(report.getStatus()) || "FAILED".equals(report.getStatus()));

        String baseName = MRF_NODE_TYPE + "_" + MRF_ACTIVITY + "_validation-report";
        assertTrue("JSON report missing",  new File(MRF_OUTPUT_DIR, baseName + ".json").exists());
        assertTrue("HTML report missing",  new File(MRF_OUTPUT_DIR, baseName + ".html").exists());
        assertTrue("Excel report missing", new File(MRF_OUTPUT_DIR, baseName + ".xlsx").exists());

        assertFalse("Report has no sheet results", report.getSheets().isEmpty());

        System.out.println("=== MRF Validation Result ===");
        System.out.println("Status:       " + report.getStatus());
        System.out.println("Total errors: " + report.getTotalErrors());
        System.out.println("Sheets:       " + report.getSheets().size());
        System.out.println("Parameters:   " + report.getParameters());
        report.getSheets().forEach(s -> {
            System.out.println("  Sheet: " + s.getSheetName()
                    + "  rows=" + s.getRowsChecked()
                    + "  errors=" + s.getErrors().size());
            s.getErrors().forEach(e -> System.out.println("    [row " + e.getRowNumber() + ":"
                    + e.getColumn() + "] " + e.getMessage()));
        });

        // GROUP mode verification: if PASSED, verify group folder output
        if ("PASSED".equals(report.getStatus())) {
            File mopRoot = new File(MRF_MOP_JSON_DIR);
            String[] subDirs = mopRoot.list((d, n) -> new File(d, n).isDirectory());
            if (subDirs != null && subDirs.length > 0) {
                System.out.println("GROUP mode folders: " + java.util.Arrays.toString(subDirs));
                for (String subDir : subDirs) {
                    File folder = new File(mopRoot, subDir);
                    String[] jsonFiles = folder.list((d, n) -> n.endsWith(".json"));
                    assertNotNull(jsonFiles);
                    // Each CRGROUP folder contains exactly one JSON (the CRGroupIndex with embedded data)
                    assertEquals("Group folder '" + subDir + "' should contain exactly one JSON",
                            1, jsonFiles.length);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // ciq-generate mode
    // -------------------------------------------------------------------------

    @Test
    public void testCiqGenerate() throws Exception {
        Assume.assumeTrue("MRF rules file not found — skipping generate test",
                new File(MRF_RULES_FILE).exists());

        String outputDir = "target/ciq-generate-test";
        new File(outputDir).mkdirs();
        String expectedFile = outputDir + "/" + MRF_NODE_TYPE + "_" + MRF_ACTIVITY + "_CIQ.xlsx";

        // Generate via CLI entry point
        int rc = CiqProcessorMain.run(new String[]{
                "--mode",      "ciq-generate",
                "--node-type", MRF_NODE_TYPE,
                "--activity",  MRF_ACTIVITY,
                "--rules",     MRF_RULES_FILE,
                "--output",    outputDir
        });
        assertEquals("ciq-generate should return 0", 0, rc);

        File generated = new File(expectedFile);
        assertTrue("Template file must be created", generated.exists());
        assertTrue("Template file must not be empty", generated.length() > 0);

        // Open the workbook and verify sheet names + header rows
        try (FileInputStream fis = new FileInputStream(generated);
             Workbook wb = WorkbookFactory.create(fis)) {

            // Expect at least: Index, ANNOUNCEMENT_FILES, Node_ID/Node_Details, User_ID
            assertTrue("Expected at least 3 sheets", wb.getNumberOfSheets() >= 3);

            // Every sheet must have a header row (row 0) with at least one cell
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                Row header = sheet.getRow(0);
                assertNotNull("Sheet '" + sheet.getSheetName() + "' must have a header row", header);
                assertTrue("Header row must have at least one cell",
                        header.getLastCellNum() > 0);
                System.out.println("  Sheet '" + sheet.getSheetName()
                        + "' headers: " + header.getLastCellNum() + " column(s)");
            }

            // Index sheet must be first
            assertEquals("First sheet must be 'Index'", "Index", wb.getSheetAt(0).getSheetName());
        }

        System.out.println("Template generated: " + generated.getAbsolutePath()
                + " (" + generated.length() + " bytes)");
    }

    @Test
    public void testCiqGenerateMissingArgs() {
        int rc = CiqProcessorMain.run(new String[]{
                "--mode",      "ciq-generate",
                "--node-type", MRF_NODE_TYPE
                // missing --activity and --rules
        });
        assertEquals("Missing required args should return exit code 1", 1, rc);
    }

    @Test
    public void testMrfCliMain() {
        Assume.assumeTrue("MRF CIQ file not found — skipping MRF CLI test",
                new File(MRF_CIQ_FILE).exists());
        Assume.assumeTrue("MRF rules file not found — skipping MRF CLI test",
                new File(MRF_RULES_FILE).exists());

        int rc = CiqProcessorMain.run(new String[]{
                "--ciq",        MRF_CIQ_FILE,
                "--node-type",  MRF_NODE_TYPE,
                "--activity",   MRF_ACTIVITY,
                "--rules",      MRF_RULES_FILE,
                "--output",     MRF_OUTPUT_DIR + "/cli",
                "--format",     "JSON,HTML"
        });
        assertTrue("Expected exit code 0 or 1", rc == 0 || rc == 1);
    }
}

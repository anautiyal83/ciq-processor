package com.nokia.ciq.processor;

import com.nokia.ciq.processor.template.CiqTemplateGenerator;
import com.nokia.ciq.processor.template.CiqTemplateResult;
import com.nokia.ciq.validator.model.ValidationReport;

import java.io.IOException;

/**
 * CLI entry point for the CIQ Processor.
 *
 * <p>Reads a CIQ Excel workbook, validates it against a YAML rules file, writes validation
 * reports, and (when validation passes) exports node/group-specific JSON data for use by the
 * MOP generator.
 *
 * <h2>Operating Modes</h2>
 *
 * <h3>NODE mode (e.g. SBC — FIXED_LINE_CONFIGURATION)</h3>
 * <p>The INDEX sheet has columns {@code Node | CRGroup | Tables}.  Each data sheet has a
 * {@code Node} column that ties every row to a specific node.  When {@code --mop-json-dir} is
 * supplied the processor writes one flat JSON folder per child-order (CRGroup value):
 * <pre>
 *   mop-json/
 *   └── RAJ/
 *       ├── SBC_FIXED_LINE_CONFIGURATION_CRFTargetList_RAJ.json
 *       └── SBC_FIXED_LINE_CONFIGURATION_index_RAJ.json
 * </pre>
 * The validation rules file sets {@code groupByColumnName: NODE} and
 * {@code validateIndexSheets: true}.
 *
 * <h3>GROUP mode (e.g. MRF — ANNOUNCEMENT_LOADING)</h3>
 * <p>The INDEX sheet has columns {@code GROUP | NODE} (no {@code TABLES} column).  Data sheets
 * use a {@code GROUP} column instead of {@code NODE}; every row in a group is identical across
 * all nodes in that group (homogeneous MOP).  The processor detects GROUP mode automatically
 * when {@code groupByColumnName: GROUP} is set in the rules file.  Output is one sub-folder per
 * group containing a {@code GroupIndex} JSON and per-sheet data JSONs:
 * <pre>
 *   mop-json/
 *   ├── A/
 *   │   ├── MRF_ANNOUNCEMENT_LOADING_index_A.json
 *   │   └── MRF_ANNOUNCEMENT_LOADING_ANNOUNCEMENT_FILES_A.json
 *   └── B/
 *       ├── MRF_ANNOUNCEMENT_LOADING_index_B.json
 *       └── MRF_ANNOUNCEMENT_LOADING_ANNOUNCEMENT_FILES_B.json
 * </pre>
 * The {@code GroupIndex} JSON records the group name, the list of nodes, the NIAM mapping for
 * those nodes, and the data sheet names.  The validation rules file sets
 * {@code groupByColumnName: GROUP}, {@code validateIndexSheets: false}, and
 * {@code validateNodeIds: true}.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -jar ciq-processor-1.0.0-cli.jar \
 *     --ciq         &lt;ciq-file.xlsx&gt;                                        \
 *     --node-type   &lt;SBC|MRF&gt;                                               \
 *     --activity    &lt;FIXED_LINE_CONFIGURATION|ANNOUNCEMENT_LOADING&gt;        \
 *     --rules       &lt;NODE_TYPE_ACTIVITY_validation-rules.yaml&gt;              \
 *     --output      &lt;report-output-dir&gt;                                     \
 *     [--format     JSON,HTML,MSEXCEL]                                       \
 *     [--mop-json-dir &lt;mop-json-output-dir&gt;]
 * </pre>
 *
 * <h2>Validation Rules YAML — Key Fields</h2>
 * <pre>
 * # NODE mode (SBC):                   # GROUP mode (MRF):
 * validateIndexSheets: true             validateIndexSheets: false
 * validateNodeIds: true                 validateNodeIds: true
 * groupByColumnName: NODE               groupByColumnName: GROUP
 *
 * indexSheet:                           indexSheet:
 *   columns:                              columns:
 *     Node:    { required: true }           GROUP: { required: true }
 *     CRGroup: { required: true }           NODE:  { required: true }
 *     Tables:  { required: true }
 *
 * nodeIdSheet:                          nodeIdSheet:
 *   columns:                              columns:
 *     Node: { required: true }              NODE:    { required: true }
 *     NIAM: { required: true }              NIAM_ID: { required: true }
 *
 * sheets:
 *   &lt;SheetName&gt;:
 *     columns:
 *       &lt;Column&gt;:
 *         required: true
 *         pattern: "regex"
 *         patternMessage: "..."
 *         allowedValues: [A, B, C]
 *         integer: true
 *         minValue: 1
 *         maxValue: 100
 *         minLength: 1
 *         maxLength: 128
 *         requiredWhen:
 *           column: Action
 *           value:  MODIFY
 *         crossRef:
 *           sheet:  "_index"
 *           column: node
 * </pre>
 *
 * <h2>Output Parameters (printed when validation PASSES)</h2>
 * <p>NODE mode:
 * <pre>
 *   NODE_1=SBC1
 *   NIAM_ID_1=sbc1-neid
 *   NODE_2=SBC2
 *   NIAM_ID_2=sbc2-neid
 *   TOTAL_NODES_COUNT=2
 *   CHILD_ORDERS_COUNT=1
 * </pre>
 * <p>GROUP mode:
 * <pre>
 *   GROUP_1=A
 *   GROUP_1_VALUES=MRF1,MRF2
 *   GROUP_2=B
 *   GROUP_2_VALUES=MRF3,MRF4
 *   TOTAL_NODES_COUNT=4
 * </pre>
 *
 * <p>Exit code: 0 = validation PASSED, 1 = validation FAILED or error.
 */
public class CiqProcessorMain {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String mode          = "ciq-validate";   // default
        String ciqFilePath   = null;
        String nodeType      = null;
        String activity      = null;
        String rulesFilePath = null;
        String output        = null;
        String formatCsv     = null;
        String mopJsonDir    = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode":          if (i + 1 < args.length) mode          = args[++i]; break;
                case "--ciq":           if (i + 1 < args.length) ciqFilePath   = args[++i]; break;
                case "--node-type":     if (i + 1 < args.length) nodeType      = args[++i]; break;
                case "--activity":      if (i + 1 < args.length) activity      = args[++i]; break;
                case "--rules":         if (i + 1 < args.length) rulesFilePath = args[++i]; break;
                case "--output":        if (i + 1 < args.length) output        = args[++i]; break;
                case "--format":        if (i + 1 < args.length) formatCsv     = args[++i]; break;
                case "--mop-json-dir":  if (i + 1 < args.length) mopJsonDir    = args[++i]; break;
                case "--help": case "-h": printUsage(); return 0;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    return 1;
            }
        }

        if ("ciq-generate".equalsIgnoreCase(mode)) {
            return runGenerate(nodeType, activity, rulesFilePath, output);
        } else if ("ciq-validate".equalsIgnoreCase(mode)) {
            return runValidate(ciqFilePath, nodeType, activity, rulesFilePath,
                               output, formatCsv, mopJsonDir);
        } else {
            System.err.println("Error: unknown --mode '" + mode
                    + "'. Use 'ciq-validate' or 'ciq-generate'.");
            printUsage();
            return 1;
        }
    }

    // -------------------------------------------------------------------------
    // ciq-validate mode
    // -------------------------------------------------------------------------

    private static int runValidate(String ciqFilePath, String nodeType, String activity,
                                   String rulesFilePath, String outputDir,
                                   String formatCsv, String mopJsonDir) {
    	
        if (ciqFilePath == null || nodeType == null || activity == null
                || rulesFilePath == null || outputDir == null) {
            System.out.println("STATUS=FAILED");
            System.out.println("ERROR=--ciq, --node-type, --activity, --rules, and --output are required for ciq-validate.");
            printUsage();
            return 1;
        }

        try {
            ValidationReport report = new CiqProcessorImpl().process(
                    ciqFilePath, nodeType, activity,
                    rulesFilePath, outputDir,
                    formatCsv, mopJsonDir);

            System.out.println("STATUS=" + report.getStatus());
            System.out.println("ERRORS=" + report.getTotalErrors());
            report.getParameters().forEach((k, v) -> System.out.println(k + "=" + v));

            return "PASSED".equals(report.getStatus()) ? 0 : 1;

        } catch (IOException e) {
            System.out.println("STATUS=FAILED");
            System.out.println("ERROR=" + e.getMessage());
            return 1;
        }
    }

    // -------------------------------------------------------------------------
    // ciq-generate mode
    // -------------------------------------------------------------------------

    private static int runGenerate(String nodeType, String activity,
                                   String rulesFilePath, String outputDir) {
        if (nodeType == null || activity == null || rulesFilePath == null) {
            System.out.println("STATUS=FAILED");
            System.out.println("ERROR=--node-type, --activity, and --rules are required for ciq-generate.");
            printUsage();
            return 1;
        }

        CiqTemplateResult result = new CiqTemplateGenerator().generate(nodeType, activity, rulesFilePath, outputDir);
        System.out.println("STATUS=" + result.getStatus());
        System.out.println("ERRORS=" + result.getErrors());
        result.getParameters().forEach((k, v) -> System.out.println(k + "=" + v));
        return result.isSuccess() ? 0 : 1;
    }

    // -------------------------------------------------------------------------
    // Usage
    // -------------------------------------------------------------------------

    private static void printUsage() {
        System.out.println("Usage: java -jar ciq-processor-1.0.0-cli.jar --mode <mode> [options]");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  ciq-validate  (default) Validate a CIQ Excel file against rules");
        System.out.println("  ciq-generate            Generate a blank CIQ template from rules");
        System.out.println();
        System.out.println("ciq-validate options:");
        System.out.println("  --ciq           <file>   CIQ Excel workbook (.xlsx)               [required]");
        System.out.println("  --node-type     <type>   Node type, e.g. SBC or MRF               [required]");
        System.out.println("  --activity      <name>   Activity name                             [required]");
        System.out.println("  --rules         <file>   YAML validation-rules file                [required]");
        System.out.println("  --output        <dir>    Output directory for validation reports   [required]");
        System.out.println("  --format        <csv>    JSON,HTML,MSEXCEL (default: all three)    [optional]");
        System.out.println("  --mop-json-dir  <dir>    JSON output dir for MOP generation        [optional]");
        System.out.println("  Report name is auto-generated: <NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT");
        System.out.println();
        System.out.println("ciq-generate options:");
        System.out.println("  --node-type     <type>   Node type, e.g. SBC or MRF               [required]");
        System.out.println("  --activity      <name>   Activity name                             [required]");
        System.out.println("  --rules         <file>   YAML validation-rules file                [required]");
        System.out.println("  --output        <dir>    Output directory for the CIQ template       [optional]");
        System.out.println("                           File: <NODE_TYPE>_<ACTIVITY>_CIQ.xlsx (fixed name)");
        System.out.println();
        System.out.println("Exit code: 0=PASSED/success, 1=FAILED/error");
    }
}

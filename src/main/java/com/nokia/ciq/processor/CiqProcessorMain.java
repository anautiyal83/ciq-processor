package com.nokia.ciq.processor;

import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.report.ReportFormat;

import java.io.IOException;
import java.util.List;

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
 *     [--mop-json-dir &lt;mop-json-output-dir&gt;]                               \
 *     [--report-name  &lt;base-report-file-name&gt;]
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
        String ciqFilePath   = null;
        String nodeType      = null;
        String activity      = null;
        String rulesFilePath = null;
        String outputDir     = null;
        String formatCsv     = null;
        String mopJsonDir    = null;
        String reportFileName = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ciq":           if (i + 1 < args.length) ciqFilePath   = args[++i]; break;
                case "--node-type":     if (i + 1 < args.length) nodeType      = args[++i]; break;
                case "--activity":      if (i + 1 < args.length) activity      = args[++i]; break;
                case "--rules":         if (i + 1 < args.length) rulesFilePath = args[++i]; break;
                case "--output":        if (i + 1 < args.length) outputDir     = args[++i]; break;
                case "--format":        if (i + 1 < args.length) formatCsv     = args[++i]; break;
                case "--mop-json-dir":  if (i + 1 < args.length) mopJsonDir     = args[++i]; break;
                case "--report-name":   if (i + 1 < args.length) reportFileName = args[++i]; break;
                case "--help": case "-h": printUsage(); return 0;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    return 1;
            }
        }

        if (ciqFilePath == null || nodeType == null || activity == null
                || rulesFilePath == null || outputDir == null) {
            System.err.println(
                    "Error: --ciq, --node-type, --activity, --rules, and --output are required.");
            printUsage();
            return 1;
        }

        List<ReportFormat> formats = (formatCsv != null)
                ? ReportFormat.parseList(formatCsv)
                : ReportFormat.parseList("JSON,HTML,MSEXCEL");

        try {
            ValidationReport report = new CiqProcessorImpl().process(
                    ciqFilePath, nodeType, activity,
                    rulesFilePath, outputDir,
                    formats, mopJsonDir, reportFileName);

            System.out.println("Validation " + report.getStatus()
                    + " — " + report.getTotalErrors() + " error(s)");
            System.out.println("Reports written to: " + outputDir);

            if ("PASSED".equals(report.getStatus())) {
                report.getParameters().forEach((k, v) -> System.out.println(k + "=" + v));
            }

            return "PASSED".equals(report.getStatus()) ? 0 : 1;

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar ciq-processor-1.0.0-cli.jar [options]");
        System.out.println();
        System.out.println("Required:");
        System.out.println("  --ciq           <file>   CIQ Excel workbook (.xlsx)");
        System.out.println("  --node-type     <type>   Node type, e.g. SBC or MRF");
        System.out.println("  --activity      <name>   Activity name, e.g. FIXED_LINE_CONFIGURATION");
        System.out.println("  --rules         <file>   YAML validation-rules file");
        System.out.println("                           Naming convention: <NODE_TYPE>_<ACTIVITY>_validation-rules.yaml");
        System.out.println("  --output        <dir>    Output directory for validation reports");
        System.out.println();
        System.out.println("Optional:");
        System.out.println("  --format        <csv>    Report formats: JSON,HTML,MSEXCEL (default: all three)");
        System.out.println("  --mop-json-dir  <dir>    JSON output for MOP generation (written only on PASSED)");
        System.out.println("                           NODE mode: flat per-child-order folders");
        System.out.println("                           GROUP mode: one sub-folder per group containing GroupIndex JSON");
        System.out.println("  --report-name   <name>   Base file name for reports (no extension)");
        System.out.println("                           Default: <node-type>_<activity>_validation-report");
        System.out.println();
        System.out.println("Operating modes (controlled by groupByColumnName in the rules YAML):");
        System.out.println("  NODE    INDEX sheet: Node|CRGroup|Tables      — one MOP JSON folder per CRGroup value");
        System.out.println("  GROUP   INDEX sheet: GROUP|NODE                — one MOP JSON sub-folder per group");
        System.out.println("  CRGROUP INDEX sheet: GROUP|CRGROUP|NODE        — one MOP JSON sub-folder per CRGROUP");
        System.out.println();
        System.out.println("Output parameters printed on PASSED:");
        System.out.println("  NODE mode:    NODE_N, NIAM_ID_N, TOTAL_NODES_COUNT, CHILD_ORDERS_COUNT");
        System.out.println("  GROUP mode:   GROUP_N, GROUP_N_VALUES, TOTAL_GROUPS_COUNT,");
        System.out.println("                NODE_N, NIAM_ID_N, TOTAL_NODES_COUNT, CHILD_ORDERS_COUNT");
        System.out.println("  CRGROUP mode: CRGROUP_N, CRGROUP_N_GROUPS, CRGROUP_N_NODES, CRGROUP_N_NODES_COUNT,");
        System.out.println("                TOTAL_CRGROUPS_COUNT,");
        System.out.println("                GROUP_N, GROUP_N_VALUES, TOTAL_GROUPS_COUNT,");
        System.out.println("                NODE_N, NIAM_ID_N, TOTAL_NODES_COUNT,");
        System.out.println("                CHILD_ORDERS_COUNT (= TOTAL_CRGROUPS_COUNT in CRGROUP mode)");
        System.out.println();
        System.out.println("Exit code: 0=PASSED, 1=FAILED or error");
    }
}

package com.nokia.ciq.validator;

import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.report.ReportFormat;

import java.util.List;

/**
 * CLI entry point for the CIQ Validator.
 *
 * Usage:
 *   java -jar ciq-validator.jar            \
 *     --json-dir     <ciq-json-dir>        \
 *     --node-type    <SBC>                 \
 *     --activity     <FIXED_LINE_CONFIGURATION> \
 *     --rules        <validation-rules.yaml>    \
 *     --output       <report-output-dir>   \
 *     --format       JSON,HTML,MSEXCEL     \
 *     --mop-json-dir <mop-json-output-dir>
 *
 * Exit code: 0 = PASSED, 1 = FAILED or error.
 */
public class CiqValidatorMain {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String jsonDir    = null;
        String nodeType   = null;
        String activity   = null;
        String rulesFile  = null;
        String outputDir  = ".";
        String format     = "JSON,HTML,MSEXCEL";   // default: all three
        String mopJsonDir = null;                  // optional child-order segregation

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--json-dir":      if (i + 1 < args.length) jsonDir    = args[++i]; break;
                case "--node-type":     if (i + 1 < args.length) nodeType   = args[++i]; break;
                case "--activity":      if (i + 1 < args.length) activity   = args[++i]; break;
                case "--rules":         if (i + 1 < args.length) rulesFile  = args[++i]; break;
                case "--output":        if (i + 1 < args.length) outputDir  = args[++i]; break;
                case "--format":        if (i + 1 < args.length) format     = args[++i]; break;
                case "--mop-json-dir":  if (i + 1 < args.length) mopJsonDir = args[++i]; break;
                case "--help": case "-h": printUsage(); return 0;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    return 1;
            }
        }

        if (jsonDir == null || nodeType == null || activity == null || rulesFile == null) {
            System.err.println("Error: --json-dir, --node-type, --activity, and --rules are required.");
            printUsage();
            return 1;
        }

        List<ReportFormat> formats;
        try {
            formats = ReportFormat.parseList(format);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        try {
            ValidationReport result = new CiqValidatorImpl()
                    .validate(jsonDir, nodeType, activity, rulesFile, outputDir, formats, mopJsonDir);

            System.out.println("Validation: " + result.getStatus()
                    + " (" + result.getTotalErrors() + " error(s))");

            String baseName = nodeType + "_" + activity + "_validation-report";
            for (ReportFormat fmt : formats) {
                System.out.println(fmt.name() + " report: "
                        + outputDir + "/" + baseName + "." + fmt.extension());
            }

            if ("PASSED".equals(result.getStatus()) && mopJsonDir != null) {
                System.out.println("MOP JSON output: " + mopJsonDir
                        + "/<NODE>_<CRGroup>/ (one folder per child order)");
            }

            return "PASSED".equals(result.getStatus()) ? 0 : 1;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar ciq-validator.jar [options]");
        System.out.println();
        System.out.println("  --json-dir     <dir>   Directory with CIQ JSON files from ciq-reader");
        System.out.println("  --node-type    <type>  Node type, e.g. SBC");
        System.out.println("  --activity     <name>  Activity name, e.g. FIXED_LINE_CONFIGURATION");
        System.out.println("  --rules        <file>  Path to YAML validation rules file");
        System.out.println("  --output       <dir>   Directory for report output files (default: current dir)");
        System.out.println("  --format       <list>  Comma-separated formats: JSON, HTML, MSEXCEL (default: all three)");
        System.out.println("  --mop-json-dir <dir>   Optional: write per-child-order JSON into <dir>/<NODE>_<CRGroup>/");
        System.out.println("                         Only written when validation PASSES.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  --format JSON");
        System.out.println("  --format HTML,MSEXCEL");
        System.out.println("  --format JSON,HTML,MSEXCEL");
        System.out.println();
        System.out.println("Exit code: 0=PASSED, 1=FAILED");
    }
}

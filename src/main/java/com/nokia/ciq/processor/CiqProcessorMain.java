package com.nokia.ciq.processor;

import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.report.ReportFormat;

import java.io.IOException;
import java.util.List;

/**
 * CLI entry point for the CIQ Processor.
 *
 * <p>Usage:
 * <pre>
 *   java -jar ciq-processor-1.0.0-cli.jar \
 *     --ciq        &lt;ciq-file.xlsx&gt;                    \
 *     --node-type  &lt;SBC&gt;                               \
 *     --activity   &lt;FIXED_LINE_CONFIGURATION&gt;          \
 *     --rules      &lt;SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml&gt; \
 *     --output     &lt;report-output-dir&gt;                 \
 *     [--format    JSON,HTML,MSEXCEL]                   \
 *     [--mop-json-dir &lt;mop-json-output-dir&gt;]
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

            return "PASSED".equals(report.getStatus()) ? 0 : 1;

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar ciq-processor-1.0.0-cli.jar [options]");
        System.out.println();
        System.out.println("  --ciq           <file>   CIQ Excel workbook (.xlsx)");
        System.out.println("  --node-type     <type>   Node type, e.g. SBC");
        System.out.println("  --activity      <name>   Activity name, e.g. FIXED_LINE_CONFIGURATION");
        System.out.println("  --rules         <file>   YAML validation-rules file");
        System.out.println("  --output        <dir>    Output directory for validation reports");
        System.out.println("  --format        <csv>    Report formats: JSON,HTML,MSEXCEL (default: all)");
        System.out.println("  --mop-json-dir  <dir>    Optional: child-order JSON output for MOP generation");
        System.out.println("                           Written only when validation passes.");
        System.out.println("  --report-name   <name>   Optional: base file name for reports (no extension)");
        System.out.println("                           Default: <node-type>_<activity>_validation-report");
        System.out.println();
        System.out.println("Exit code: 0=PASSED, 1=FAILED or error");
    }
}

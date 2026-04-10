package com.nokia.ciq.processor;

import com.nokia.ciq.processor.template.CiqTemplateGenerator;
import com.nokia.ciq.processor.template.CiqTemplateResult;
import com.nokia.ciq.validator.model.ValidationReport;

import java.io.IOException;

/**
 * CLI entry point for the CIQ Processor.
 *
 * <p>Reads a CIQ Excel workbook, validates it against a YAML rules file, writes validation
 * reports, and (when validation passes) exports structured JSON data for use by the MOP generator.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -jar ciq-processor-1.0.0-cli.jar \
 *     --ciq           &lt;ciq-file.xlsx&gt;                                [required] \
 *     --node-type     &lt;e.g. SBC | MRF&gt;                               [required] \
 *     --activity      &lt;e.g. FIXED_LINE_CONFIGURATION&gt;                [required] \
 *     --rules         &lt;NODE_TYPE_ACTIVITY_validation-rules.yaml&gt;     [required] \
 *     --output        &lt;report-output-dir&gt;                             [required] \
 *     [--format                JSON,HTML,MSEXCEL]                     [optional] \
 *     [--json-output-dir       &lt;json-output-dir&gt;]                     [optional] \
 *     [--json-output-config-file &lt;NODE_TYPE_ACTIVITY_json-output.yaml&gt;] [optional] \
 *     [--report-template-name  &lt;template-filename&gt;]                   [optional] \
 *     [--report-template-path  &lt;template-directory&gt;]                  [optional]
 * </pre>
 *
 * <h2>JSON Output (--json-output-dir + --json-output-config-file)</h2>
 * <p>When both {@code --json-output-dir} and {@code --json-output-config-file} are provided the processor
 * writes one or more structured JSON files into {@code --json-output-dir} according to the
 * template defined in the json-output config file.  Two output modes are supported:
 *
 * <h3>output_mode: individual</h3>
 * <p>One JSON file per segregation unit (CR / group / node).  The grouping is auto-detected
 * from the workbook structure.  File name pattern:
 * {@code <NODE_TYPE>_<ACTIVITY>_<unit>.json}
 *
 * <h3>output_mode: single</h3>
 * <p>One JSON file for the entire workbook.  File name:
 * {@code <NODE_TYPE>_<ACTIVITY>.json}
 *
 * <p>The {@code data} block inside {@code json_output} is a free-form YAML template evaluated
 * by {@code JsonTemplateEvaluator}.  Key directives:
 * <pre>
 *   # Iterate distinct values of any column from any sheet
 *   _each: "DISTINCT &lt;Sheet&gt;.&lt;Column&gt; AS $&lt;varname&gt;"
 *
 *   # Iterate all rows of a sheet
 *   _each: &lt;SheetName&gt;
 *
 *   # Iterate filtered rows
 *   _each: "&lt;Sheet&gt; WHERE &lt;Col&gt; = &lt;value&gt;"
 *
 *   # Relational lookup
 *   key: "&lt;Sheet&gt;.&lt;Col&gt; WHERE &lt;Sheet&gt;.&lt;FilterCol&gt; = $var"
 *
 *   # Cross-sheet value (first non-blank, scoped to current DISTINCT value)
 *   key: &lt;Sheet&gt;.&lt;Column&gt;
 * </pre>
 * See {@code Schema.yaml} for the full reference and examples.
 *
 * <h2>Validation Report Files</h2>
 * <p>Written to {@code --output}.  Base name controlled by {@code report_output.filename}
 * in the rules YAML (defaults to {@code <NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT}).
 * One file per requested format; default is all three:
 * <pre>
 *   MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT.json
 *   MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT.html
 *   MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT.xlsx
 * </pre>
 *
 * <h2>HTML Report Template (--report-template-name / --report-template-path)</h2>
 * <p>By default the HTML report uses a built-in hardcoded layout.  Passing both
 * {@code --report-template-name} and {@code --report-template-path} switches to
 * an external template file rendered by {@code HtmlTemplateReportWriter}.
 *
 * <p>The template is a plain HTML file with the following placeholder syntax:
 * <pre>
 *   Scalars  : {{status}} {{nodeType}} {{activity}} {{totalErrors}}
 *              {{sheetsCount}} {{passedSheets}} {{failedSheets}}
 *              {{totalRows}} {{generatedAt}} {{params.KEY}}
 *
 *   Sections : {{#sheets}}...{{/sheets}}
 *                  {{sheetName}} {{sheetStatus}} {{rowsChecked}} {{errorCount}}
 *                  {{sheetHeaderClass}} {{sheetBadgeClass}} {{sheetPassMsg}}
 *                  {{sheetErrorTableStyle}}
 *                  {{#errors}}...{{/errors}}
 *                      {{rowNumber}} {{column}} {{value}} {{message}}
 *
 *              {{#globalErrors}}{{message}}{{/globalErrors}}
 *              {{#parameters}}{{key}} {{value}}{{/parameters}}
 *
 *   Conditionals: {{#if_passed}}...{{/if_passed}}
 *                 {{#if_failed}}...{{/if_failed}}
 * </pre>
 * <p>A ready-to-use default template is provided at
 * {@code src/main/resources/validation-report-template.html}.
 *
 * <h2>Output Parameters (printed to stdout when validation PASSES)</h2>
 * <pre>
 *   STATUS=PASSED
 *   ERRORS=0
 *   REPORT_FILENAME_JSON=MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT.json
 *   REPORT_FILENAME_HTML=MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT.html
 *   REPORT_FILENAME_MSEXCEL=MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT.xlsx
 *   CR_LIST=CR1,CR2
 *   CR_COUNT=2
 *   &lt;any outputs defined in the rules YAML outputs: block&gt;
 * </pre>
 *
 * <p>Exit code: 0 = validation PASSED, 1 = validation FAILED or error.
 */
public class CiqProcessorMain {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String mode                 = "ciq-validate";   // default
        String ciqFilePath          = null;
        String nodeType             = null;
        String activity             = null;
        String rulesFilePath        = null;
        String output               = null;
        String formatCsv            = null;
        String jsonOutputDir        = null;
        String jsonOutputConfig     = null;
        String reportTemplateName   = null;
        String reportTemplatePath   = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode":                   if (i + 1 < args.length) mode               = args[++i]; break;
                case "--ciq":                    if (i + 1 < args.length) ciqFilePath        = args[++i]; break;
                case "--node-type":              if (i + 1 < args.length) nodeType           = args[++i]; break;
                case "--activity":               if (i + 1 < args.length) activity           = args[++i]; break;
                case "--rules":                  if (i + 1 < args.length) rulesFilePath      = args[++i]; break;
                case "--output":                 if (i + 1 < args.length) output             = args[++i]; break;
                case "--format":                 if (i + 1 < args.length) formatCsv          = args[++i]; break;
                case "--json-output-dir":        if (i + 1 < args.length) jsonOutputDir      = args[++i]; break;
                case "--json-output-config-file": if (i + 1 < args.length) jsonOutputConfig  = args[++i]; break;
                case "--report-template-name":   if (i + 1 < args.length) reportTemplateName = args[++i]; break;
                case "--report-template-path":   if (i + 1 < args.length) reportTemplatePath = args[++i]; break;
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
                               output, formatCsv, jsonOutputDir, jsonOutputConfig,
                               reportTemplateName, reportTemplatePath);
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
                                   String formatCsv, String jsonOutputDir, String jsonOutputConfig,
                                   String reportTemplateName, String reportTemplatePath) {

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
                    formatCsv, jsonOutputDir, jsonOutputConfig,
                    reportTemplateName, reportTemplatePath);

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
        System.out.println("  --ciq                    <file>   CIQ Excel workbook (.xlsx)                    [required]");
        System.out.println("  --node-type              <type>   Node type, e.g. SBC or MRF                    [required]");
        System.out.println("  --activity               <name>   Activity name                                  [required]");
        System.out.println("  --rules                  <file>   YAML validation-rules file                     [required]");
        System.out.println("  --output                 <dir>    Output directory for validation reports        [required]");
        System.out.println("  --format                 <csv>    JSON,HTML,MSEXCEL (default: all three)         [optional]");
        System.out.println("  --json-output-dir        <dir>    JSON output dir for MOP generation             [optional]");
        System.out.println("  --json-output-config-file <file>  JSON output config (*_json-output.yaml)        [optional]");
        System.out.println("  --report-template-name   <file>   HTML report template filename                  [optional]");
        System.out.println("  --report-template-path   <dir>    Directory containing the HTML report template  [optional]");
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

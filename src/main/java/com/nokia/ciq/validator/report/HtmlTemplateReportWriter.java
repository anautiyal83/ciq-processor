package com.nokia.ciq.validator.report;

import com.nokia.ciq.validator.model.SheetValidationResult;
import com.nokia.ciq.validator.model.ValidationError;
import com.nokia.ciq.validator.model.ValidationReport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a {@link ValidationReport} by rendering an external HTML template file.
 *
 * <p>The template file is plain HTML with embedded placeholder syntax.  The writer
 * replaces all placeholders with real data from the report and writes the result to
 * the requested output path.
 *
 * <h3>Scalar placeholders</h3>
 * <pre>
 * {{status}}        — PASSED or FAILED
 * {{nodeType}}      — node type string
 * {{activity}}      — activity string
 * {{totalErrors}}   — total number of validation errors
 * {{sheetsCount}}   — number of sheets checked
 * {{passedSheets}}  — number of sheets that passed
 * {{failedSheets}}  — number of sheets that failed
 * {{totalRows}}     — sum of rowsChecked across all sheets
 * {{generatedAt}}   — timestamp (yyyy-MM-dd HH:mm:ss)
 * {{params.KEY}}    — value of parameter KEY from report.getParameters()
 * </pre>
 *
 * <h3>Section blocks (loops)</h3>
 * <pre>
 * {{#sheets}}
 *   {{sheetName}} {{sheetStatus}} {{rowsChecked}} {{errorCount}}
 *   {{#errors}}
 *     {{rowNumber}} {{column}} {{value}} {{message}}
 *   {{/errors}}
 * {{/sheets}}
 *
 * {{#globalErrors}}
 *   {{message}}
 * {{/globalErrors}}
 *
 * {{#parameters}}
 *   {{key}} = {{value}}
 * {{/parameters}}
 * </pre>
 *
 * <h3>Conditional blocks</h3>
 * <pre>
 * {{#if_passed}}  ...content shown only when status=PASSED...  {{/if_passed}}
 * {{#if_failed}}  ...content shown only when status=FAILED...  {{/if_failed}}
 * </pre>
 *
 * <p>All substituted string values are HTML-escaped automatically.
 */
public class HtmlTemplateReportWriter {

    /**
     * Renders {@code report} using the HTML template at {@code templatePath} and
     * writes the result to {@code outputPath}.
     *
     * @param report       the validation report to render
     * @param outputPath   path of the file to write (parent directories are created automatically)
     * @param templatePath path of the HTML template file
     * @throws IOException if the template cannot be read or the output cannot be written
     */
    public void write(ValidationReport report, String outputPath, String templatePath)
            throws IOException {
        byte[] bytes = Files.readAllBytes(new File(templatePath).toPath());
        String template = new String(bytes, StandardCharsets.UTF_8);
        String rendered = render(template, report);

        File out = new File(outputPath);
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.print(rendered);
        }
    }

    // -------------------------------------------------------------------------
    // Core rendering pipeline
    // -------------------------------------------------------------------------

    private String render(String template, ValidationReport report) {
        String ts      = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        boolean passed = "PASSED".equals(report.getStatus());

        long passedSheets = report.getSheets().stream()
                .filter(s -> "PASSED".equals(s.getStatus())).count();
        int totalRows = report.getSheets().stream()
                .mapToInt(SheetValidationResult::getRowsChecked).sum();

        // Step 1: conditional blocks
        template = expandConditional(template, "if_passed", passed);
        template = expandConditional(template, "if_failed",  !passed);

        // Step 2: section loops
        template = expandSheets(template, report);
        template = expandGlobalErrors(template, report);
        template = expandParameters(template, report);

        // Step 3: scalar substitutions
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("status",       esc(report.getStatus()));
        vars.put("nodeType",     esc(report.getNodeType()));
        vars.put("activity",     esc(report.getActivity()));
        vars.put("totalErrors",  String.valueOf(report.getTotalErrors()));
        vars.put("sheetsCount",  String.valueOf(report.getSheets().size()));
        vars.put("passedSheets", String.valueOf(passedSheets));
        vars.put("failedSheets", String.valueOf(report.getSheets().size() - passedSheets));
        vars.put("totalRows",    String.valueOf(totalRows));
        vars.put("generatedAt",  ts);

        for (Map.Entry<String, String> e : vars.entrySet()) {
            template = template.replace("{{" + e.getKey() + "}}", e.getValue());
        }

        // Step 4: params.KEY substitutions
        for (Map.Entry<String, String> e : report.getParameters().entrySet()) {
            template = template.replace("{{params." + e.getKey() + "}}", esc(e.getValue()));
        }

        return template;
    }

    // -------------------------------------------------------------------------
    // Section expanders
    // -------------------------------------------------------------------------

    private String expandSheets(String template, ValidationReport report) {
        String inner = extractSection(template, "sheets");
        if (inner == null) return template;

        StringBuilder sb = new StringBuilder();
        for (SheetValidationResult sheet : report.getSheets()) {
            boolean sheetPassed = "PASSED".equals(sheet.getStatus());
            String item = inner;
            item = expandErrors(item, sheet);                                  // nested loop first
            item = item.replace("{{sheetName}}",        esc(sheet.getSheetName()));
            item = item.replace("{{sheetStatus}}",      esc(sheet.getStatus()));
            item = item.replace("{{rowsChecked}}",      String.valueOf(sheet.getRowsChecked()));
            item = item.replace("{{errorCount}}",       String.valueOf(sheet.getErrors().size()));
            item = item.replace("{{sheetHeaderClass}}",    sheetPassed ? "pass-header"    : "fail-header");
            item = item.replace("{{sheetBadgeClass}}",     sheetPassed ? "badge-pass"     : "badge-fail");
            item = item.replace("{{sheetPassMsg}}",
                    sheetPassed ? "All rows passed validation." : "");
            item = item.replace("{{sheetErrorTableStyle}}",
                    sheet.getErrors().isEmpty() ? "display:none" : "");
            sb.append(item);
        }
        return replaceSection(template, "sheets", sb.toString());
    }

    private String expandErrors(String sheetTemplate, SheetValidationResult sheet) {
        String inner = extractSection(sheetTemplate, "errors");
        if (inner == null) return sheetTemplate;

        StringBuilder sb = new StringBuilder();
        for (ValidationError e : sheet.getErrors()) {
            String item = inner;
            item = item.replace("{{rowNumber}}", String.valueOf(e.getRowNumber()));
            item = item.replace("{{column}}",    esc(e.getColumn()));
            item = item.replace("{{value}}",     e.getValue() != null ? esc(e.getValue()) : "");
            item = item.replace("{{message}}",   esc(e.getMessage()));
            sb.append(item);
        }
        return replaceSection(sheetTemplate, "errors", sb.toString());
    }

    private String expandGlobalErrors(String template, ValidationReport report) {
        String inner = extractSection(template, "globalErrors");
        if (inner == null) return template;

        StringBuilder sb = new StringBuilder();
        for (String msg : report.getGlobalErrors()) {
            sb.append(inner.replace("{{message}}", esc(msg)));
        }
        return replaceSection(template, "globalErrors", sb.toString());
    }

    private String expandParameters(String template, ValidationReport report) {
        String inner = extractSection(template, "parameters");
        if (inner == null) return template;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : report.getParameters().entrySet()) {
            String item = inner;
            item = item.replace("{{key}}",   esc(e.getKey()));
            item = item.replace("{{value}}", esc(e.getValue()));
            sb.append(item);
        }
        return replaceSection(template, "parameters", sb.toString());
    }

    private String expandConditional(String template, String tag, boolean show) {
        String inner = extractSection(template, tag);
        if (inner == null) return template;
        return replaceSection(template, tag, show ? inner : "");
    }

    // -------------------------------------------------------------------------
    // Section helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the raw content between {@code {{#tag}}} and {@code {{/tag}}},
     * or {@code null} when the tag is not present.
     */
    private String extractSection(String template, String tag) {
        String open  = "{{#" + tag + "}}";
        String close = "{{/" + tag + "}}";
        int start = template.indexOf(open);
        if (start < 0) return null;
        int contentStart = start + open.length();
        int end = template.indexOf(close, contentStart);
        if (end < 0) return null;
        return template.substring(contentStart, end);
    }

    /**
     * Replaces the entire {@code {{#tag}}...{{/tag}}} block (delimiters included)
     * with {@code replacement}.
     */
    private String replaceSection(String template, String tag, String replacement) {
        String open  = "{{#" + tag + "}}";
        String close = "{{/" + tag + "}}";
        int start = template.indexOf(open);
        if (start < 0) return template;
        int end = template.indexOf(close, start + open.length());
        if (end < 0) return template;
        return template.substring(0, start)
                + replacement
                + template.substring(end + close.length());
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

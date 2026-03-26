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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Writes a {@link ValidationReport} as a styled, self-contained HTML file.
 */
public class HtmlReportWriter {

    public void write(ValidationReport report, String filePath) throws IOException {
        File file = new File(filePath);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();

        try (PrintWriter out = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            out.println(renderHtml(report));
        }
    }

    private String renderHtml(ValidationReport report) {
        StringBuilder sb = new StringBuilder();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        boolean passed = "PASSED".equals(report.getStatus());

        sb.append("<!DOCTYPE html><html lang='en'><head>");
        sb.append("<meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append("<title>CIQ Validation Report — ").append(esc(report.getNodeType()))
          .append(" / ").append(esc(report.getActivity())).append("</title>");
        sb.append(styles());
        sb.append("</head><body>");

        // ── Header ───────────────────────────────────────────────────────────
        sb.append("<div class='header'>");
        sb.append("<h1>CIQ Validation Report</h1>");
        sb.append("<div class='meta'>");
        sb.append("<span><b>Node Type:</b> ").append(esc(report.getNodeType())).append("</span>");
        sb.append("<span><b>Activity:</b> ").append(esc(report.getActivity())).append("</span>");
        sb.append("<span><b>Generated:</b> ").append(ts).append("</span>");
        sb.append("</div>");
        sb.append("</div>");

        // ── Summary ──────────────────────────────────────────────────────────
        sb.append("<div class='summary ").append(passed ? "pass" : "fail").append("'>");
        sb.append("<div class='status-badge'>").append(report.getStatus()).append("</div>");
        sb.append("<div class='summary-stats'>");
        sb.append("<div class='stat'><span class='stat-num'>").append(report.getSheets().size())
          .append("</span><span class='stat-label'>Sheets Checked</span></div>");

        long passedSheets = report.getSheets().stream()
                .filter(s -> "PASSED".equals(s.getStatus())).count();
        long failedSheets = report.getSheets().size() - passedSheets;
        int totalRows = report.getSheets().stream().mapToInt(SheetValidationResult::getRowsChecked).sum();

        sb.append("<div class='stat'><span class='stat-num pass-num'>").append(passedSheets)
          .append("</span><span class='stat-label'>Passed</span></div>");
        sb.append("<div class='stat'><span class='stat-num fail-num'>").append(failedSheets)
          .append("</span><span class='stat-label'>Failed</span></div>");
        sb.append("<div class='stat'><span class='stat-num'>").append(totalRows)
          .append("</span><span class='stat-label'>Total Rows</span></div>");
        sb.append("<div class='stat'><span class='stat-num ").append(report.getTotalErrors() > 0 ? "fail-num" : "pass-num").append("'>")
          .append(report.getTotalErrors())
          .append("</span><span class='stat-label'>Total Errors</span></div>");
        sb.append("</div></div>");

        // ── Global errors ─────────────────────────────────────────────────────
        if (!report.getGlobalErrors().isEmpty()) {
            sb.append("<div class='section'>");
            sb.append("<div class='section-header fail-header'>Global Errors</div>");
            sb.append("<ul class='global-errors'>");
            for (String e : report.getGlobalErrors()) {
                sb.append("<li>").append(esc(e)).append("</li>");
            }
            sb.append("</ul></div>");
        }

        // ── Per-sheet results ─────────────────────────────────────────────────
        for (SheetValidationResult sheet : report.getSheets()) {
            boolean sheetPassed = "PASSED".equals(sheet.getStatus());
            sb.append("<div class='section'>");
            sb.append("<div class='section-header ").append(sheetPassed ? "pass-header" : "fail-header").append("'>");
            sb.append("<span class='sheet-name'>").append(esc(sheet.getSheetName())).append("</span>");
            sb.append("<span class='sheet-badge ").append(sheetPassed ? "badge-pass" : "badge-fail").append("'>")
              .append(sheet.getStatus()).append("</span>");
            sb.append("<span class='sheet-stats'>").append(sheet.getRowsChecked()).append(" rows");
            if (!sheetPassed) {
                sb.append(" &nbsp;|&nbsp; <b>").append(sheet.getErrors().size()).append(" error(s)</b>");
            }
            sb.append("</span></div>");

            if (sheetPassed) {
                sb.append("<div class='pass-msg'>All rows passed validation.</div>");
            } else {
                renderErrorTable(sb, sheet.getErrors());
            }
            sb.append("</div>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private void renderErrorTable(StringBuilder sb, List<ValidationError> errors) {
        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th>Row #</th><th>Column</th><th>Value</th><th>Error</th>");
        sb.append("</tr></thead><tbody>");
        for (ValidationError e : errors) {
            sb.append("<tr>");
            sb.append("<td class='row-num'>").append(e.getRowNumber()).append("</td>");
            sb.append("<td class='col-name'>").append(esc(e.getColumn())).append("</td>");
            sb.append("<td class='col-value'>").append(e.getValue() != null ? esc(e.getValue()) : "<i>null</i>").append("</td>");
            sb.append("<td class='error-msg'>").append(esc(e.getMessage())).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
    }

    private String styles() {
        return "<style>" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { font-family: 'Segoe UI', Arial, sans-serif; background: #f4f6f9; color: #333; font-size: 14px; }" +

            ".header { background: #1a3a5c; color: #fff; padding: 20px 30px; }" +
            ".header h1 { font-size: 22px; font-weight: 600; margin-bottom: 8px; }" +
            ".meta { display: flex; gap: 30px; font-size: 13px; color: #b0c4d8; }" +

            ".summary { display: flex; align-items: center; gap: 30px; padding: 20px 30px; margin: 20px 30px; " +
            "  border-radius: 8px; border-left: 6px solid; }" +
            ".summary.pass { background: #eafaf1; border-color: #27ae60; }" +
            ".summary.fail { background: #fdf2f2; border-color: #e74c3c; }" +

            ".status-badge { font-size: 28px; font-weight: 700; min-width: 100px; }" +
            ".summary.pass .status-badge { color: #27ae60; }" +
            ".summary.fail .status-badge { color: #e74c3c; }" +

            ".summary-stats { display: flex; gap: 30px; }" +
            ".stat { display: flex; flex-direction: column; align-items: center; }" +
            ".stat-num { font-size: 24px; font-weight: 700; color: #1a3a5c; }" +
            ".stat-num.pass-num { color: #27ae60; }" +
            ".stat-num.fail-num { color: #e74c3c; }" +
            ".stat-label { font-size: 11px; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }" +

            ".section { margin: 0 30px 16px; background: #fff; border-radius: 8px; " +
            "  box-shadow: 0 1px 3px rgba(0,0,0,0.08); overflow: hidden; }" +

            ".section-header { display: flex; align-items: center; gap: 12px; padding: 12px 18px; font-weight: 600; }" +
            ".pass-header { background: #eafaf1; color: #1e8449; }" +
            ".fail-header { background: #fdf2f2; color: #c0392b; }" +

            ".sheet-name { font-size: 15px; flex: 1; }" +
            ".sheet-badge { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 10px; " +
            "  text-transform: uppercase; letter-spacing: 0.5px; }" +
            ".badge-pass { background: #27ae60; color: #fff; }" +
            ".badge-fail { background: #e74c3c; color: #fff; }" +
            ".sheet-stats { font-size: 12px; color: #666; font-weight: 400; }" +

            ".pass-msg { padding: 10px 18px; color: #27ae60; font-style: italic; font-size: 13px; }" +

            "table { width: 100%; border-collapse: collapse; font-size: 13px; }" +
            "thead tr { background: #f0f4f8; }" +
            "th { padding: 9px 14px; text-align: left; font-weight: 600; color: #555; " +
            "  border-bottom: 2px solid #dde3eb; white-space: nowrap; }" +
            "td { padding: 8px 14px; border-bottom: 1px solid #eef0f3; vertical-align: top; }" +
            "tr:last-child td { border-bottom: none; }" +
            "tr:hover td { background: #fafbfd; }" +

            ".row-num { width: 60px; color: #888; font-weight: 600; }" +
            ".col-name { width: 200px; font-family: monospace; color: #2c3e50; font-size: 12px; }" +
            ".col-value { width: 180px; font-family: monospace; color: #7f8c8d; font-size: 12px; }" +
            ".error-msg { color: #c0392b; }" +

            ".global-errors { padding: 12px 18px 12px 36px; color: #c0392b; }" +
            ".global-errors li { margin-bottom: 4px; }" +
            "</style>";
    }

    /** HTML-escape a string. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

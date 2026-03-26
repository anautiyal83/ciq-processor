package com.nokia.ciq.validator.report;

import com.nokia.ciq.validator.model.SheetValidationResult;
import com.nokia.ciq.validator.model.ValidationError;
import com.nokia.ciq.validator.model.ValidationReport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Writes a {@link ValidationReport} as a styled Excel workbook (.xlsx).
 *
 * <p>Structure:
 * <ul>
 *   <li><b>Summary</b>  — overall status, timestamp, per-sheet summary table</li>
 *   <li><b>All Errors</b> — flat table of every error (Sheet, Row#, Column, Value, Message)</li>
 *   <li><b>[SheetName]</b> — one sheet per CIQ table that has errors, listing its errors</li>
 * </ul>
 */
public class ExcelReportWriter {

    public void write(ValidationReport report, String filePath) throws IOException {
        File file = new File(filePath);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();

        try (Workbook wb = new XSSFWorkbook()) {
            Styles s = new Styles(wb);
            writeSummarySheet(wb, report, s);
            writeAllErrorsSheet(wb, report, s);
            for (SheetValidationResult sheet : report.getSheets()) {
                if (!sheet.getErrors().isEmpty()) {
                    writeSheetErrors(wb, sheet, s);
                }
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }
        }
    }

    // ── Summary sheet ────────────────────────────────────────────────────────

    private void writeSummarySheet(Workbook wb, ValidationReport report, Styles s) {
        Sheet sheet = wb.createSheet("Summary");
        sheet.setColumnWidth(0, 36 * 256);
        sheet.setColumnWidth(1, 24 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 12 * 256);
        sheet.setColumnWidth(4, 12 * 256);

        int r = 0;

        // Title
        Row title = sheet.createRow(r++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("CIQ Validation Report");
        titleCell.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        title.setHeightInPoints(28);

        r++; // blank row

        // Metadata
        r = addMeta(sheet, r, "Node Type",  report.getNodeType(), s);
        r = addMeta(sheet, r, "Activity",   report.getActivity(), s);
        r = addMeta(sheet, r, "Generated",  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), s);
        r = addMeta(sheet, r, "Status",     report.getStatus(),
                "PASSED".equals(report.getStatus()) ? s.metaValuePass : s.metaValueFail, s);
        r = addMeta(sheet, r, "Total Errors", String.valueOf(report.getTotalErrors()),
                report.getTotalErrors() == 0 ? s.metaValuePass : s.metaValueFail, s);

        r++; // blank row

        // Global errors (if any)
        if (!report.getGlobalErrors().isEmpty()) {
            cell(sheet.createRow(r++), 0, "Global Errors", s.sectionHeader);
            for (String e : report.getGlobalErrors()) {
                cell(sheet.createRow(r++), 0, e, s.errorText);
            }
            r++;
        }

        // Sheet summary table header
        Row hdr = sheet.createRow(r++);
        cell(hdr, 0, "Sheet",         s.tableHeader);
        cell(hdr, 1, "Status",        s.tableHeader);
        cell(hdr, 2, "Rows Checked",  s.tableHeader);
        cell(hdr, 3, "Errors",        s.tableHeader);

        // Sheet summary rows
        for (SheetValidationResult sr : report.getSheets()) {
            Row row = sheet.createRow(r++);
            cell(row, 0, sr.getSheetName(),               s.normal);
            boolean passed = "PASSED".equals(sr.getStatus());
            cell(row, 1, sr.getStatus(),                   passed ? s.passCell : s.failCell);
            cellNum(row, 2, sr.getRowsChecked(),           s.numCell);
            cellNum(row, 3, sr.getErrors().size(),         sr.getErrors().isEmpty() ? s.numCell : s.failNum);
        }
    }

    // ── All Errors sheet ─────────────────────────────────────────────────────

    private void writeAllErrorsSheet(Workbook wb, ValidationReport report, Styles s) {
        Sheet sheet = wb.createSheet("All Errors");
        sheet.setColumnWidth(0, 38 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 38 * 256);
        sheet.setColumnWidth(3, 28 * 256);
        sheet.setColumnWidth(4, 55 * 256);

        int r = 0;
        Row hdr = sheet.createRow(r++);
        cell(hdr, 0, "Sheet",   s.tableHeader);
        cell(hdr, 1, "Row #",   s.tableHeader);
        cell(hdr, 2, "Column",  s.tableHeader);
        cell(hdr, 3, "Value",   s.tableHeader);
        cell(hdr, 4, "Error",   s.tableHeader);

        for (SheetValidationResult sr : report.getSheets()) {
            for (ValidationError e : sr.getErrors()) {
                Row row = sheet.createRow(r++);
                cell(row, 0, sr.getSheetName(),                   s.normal);
                cellNum(row, 1, e.getRowNumber(),                  s.numCell);
                cell(row, 2, e.getColumn(),                        s.mono);
                cell(row, 3, e.getValue() != null ? e.getValue() : "(null)", s.mono);
                cell(row, 4, e.getMessage(),                       s.errorText);
            }
        }

        if (r == 1) {
            cell(sheet.createRow(r), 0, "No errors found — all validations passed.", s.passCell);
        }
    }

    // ── Per-sheet error detail ────────────────────────────────────────────────

    private void writeSheetErrors(Workbook wb, SheetValidationResult sr, Styles s) {
        // Truncate sheet name to 31 chars (Excel limit)
        String name = sr.getSheetName();
        if (name.length() > 28) name = name.substring(0, 28) + "...";
        Sheet sheet = wb.createSheet(name);
        sheet.setColumnWidth(0, 10 * 256);
        sheet.setColumnWidth(1, 38 * 256);
        sheet.setColumnWidth(2, 28 * 256);
        sheet.setColumnWidth(3, 55 * 256);

        int r = 0;
        Row hdr = sheet.createRow(r++);
        cell(hdr, 0, "Row #",   s.tableHeader);
        cell(hdr, 1, "Column",  s.tableHeader);
        cell(hdr, 2, "Value",   s.tableHeader);
        cell(hdr, 3, "Error",   s.tableHeader);

        for (ValidationError e : sr.getErrors()) {
            Row row = sheet.createRow(r++);
            cellNum(row, 0, e.getRowNumber(),                       s.numCell);
            cell(row, 1, e.getColumn(),                             s.mono);
            cell(row, 2, e.getValue() != null ? e.getValue() : "(null)", s.mono);
            cell(row, 3, e.getMessage(),                            s.errorText);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int addMeta(Sheet sheet, int r, String label, String value, Styles s) {
        return addMeta(sheet, r, label, value, s.metaValue, s);
    }

    private int addMeta(Sheet sheet, int r, String label, String value, CellStyle valueStyle, Styles s) {
        Row row = sheet.createRow(r);
        cell(row, 0, label, s.metaLabel);
        cell(row, 1, value, valueStyle);
        return r + 1;
    }

    private void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private void cellNum(Row row, int col, int value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    // ── Cell styles ───────────────────────────────────────────────────────────

    private static class Styles {
        final CellStyle title, sectionHeader;
        final CellStyle metaLabel, metaValue, metaValuePass, metaValueFail;
        final CellStyle tableHeader;
        final CellStyle normal, mono, numCell;
        final CellStyle passCell, failCell, failNum, errorText;

        Styles(Workbook wb) {
            Font boldLarge = font(wb, 14, true, false);
            Font bold      = font(wb, 11, true, false);
            Font regular   = font(wb, 10, false, false);
            Font monoFont  = font(wb, 10, false, false);
            monoFont.setFontName("Courier New");
            Font boldWhite = font(wb, 10, true, false);
            boldWhite.setColor(IndexedColors.WHITE.getIndex());
            Font passFont  = font(wb, 10, true, false);
            passFont.setColor(IndexedColors.DARK_GREEN.getIndex());
            Font failFont  = font(wb, 10, true, false);
            failFont.setColor(IndexedColors.DARK_RED.getIndex());
            Font redFont   = font(wb, 10, false, false);
            redFont.setColor(IndexedColors.RED.getIndex());

            title = style(wb, boldLarge, IndexedColors.DARK_BLUE, HorizontalAlignment.LEFT);
            title.setFont(boldLarge);

            sectionHeader = style(wb, bold, IndexedColors.GREY_25_PERCENT, HorizontalAlignment.LEFT);

            metaLabel     = style(wb, bold, null, HorizontalAlignment.LEFT);
            metaValue     = style(wb, regular, null, HorizontalAlignment.LEFT);
            metaValuePass = style(wb, passFont, null, HorizontalAlignment.LEFT);
            metaValueFail = style(wb, failFont, null, HorizontalAlignment.LEFT);

            tableHeader   = style(wb, boldWhite, IndexedColors.DARK_BLUE, HorizontalAlignment.LEFT);

            normal   = style(wb, regular, null, HorizontalAlignment.LEFT);
            mono     = style(wb, monoFont, null, HorizontalAlignment.LEFT);
            numCell  = style(wb, regular, null, HorizontalAlignment.CENTER);

            passCell = style(wb, passFont, IndexedColors.LIGHT_GREEN, HorizontalAlignment.CENTER);
            failCell = style(wb, failFont, IndexedColors.ROSE, HorizontalAlignment.CENTER);
            failNum  = style(wb, failFont, null, HorizontalAlignment.CENTER);
            errorText = style(wb, redFont, null, HorizontalAlignment.LEFT);
        }

        private Font font(Workbook wb, int size, boolean bold, boolean italic) {
            Font f = wb.createFont();
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            f.setItalic(italic);
            return f;
        }

        private CellStyle style(Workbook wb, Font font, IndexedColors bg, HorizontalAlignment align) {
            CellStyle cs = wb.createCellStyle();
            cs.setFont(font);
            cs.setAlignment(align);
            cs.setVerticalAlignment(VerticalAlignment.CENTER);
            cs.setWrapText(false);
            if (bg != null) {
                cs.setFillForegroundColor(bg.getIndex());
                cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            cs.setBorderBottom(BorderStyle.THIN);
            cs.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            return cs;
        }
    }
}

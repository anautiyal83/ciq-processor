package com.nokia.ciq.processor.template;

import com.nokia.ciq.validator.config.SheetRules;
import com.nokia.ciq.validator.config.ValidationRulesConfig;
import com.nokia.ciq.validator.config.ValidationRulesLoader;
import com.nokia.ciq.validator.config.ColumnRule;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a blank CIQ Excel template from a {@link ValidationRulesConfig}.
 *
 * <p>The workbook contains one sheet per entry in {@code sheets:} (in YAML order), followed
 * by the generated {@code Column_Guide} sheet.  All sheets — Index, Node-ID, User-ID, and
 * data sheets — are declared under {@code sheets:} in the rules YAML; no special config
 * sections are needed.
 *
 * <p>Columns with {@code type: enum} or {@code allowedValues} get an Excel dropdown validation
 * on rows 2–101 so the user can pick valid values directly in the workbook.
 */
public class CiqTemplateGenerator {

    private static final Logger log = LoggerFactory.getLogger(CiqTemplateGenerator.class);

    /** Number of data rows to cover with dropdown/validation. */
    private static final int VALIDATION_ROWS = 100;

    /**
     * Generates a blank CIQ template Excel file.
     *
     * <p>The output filename is always {@code <NODE_TYPE>_<ACTIVITY>_CIQ.xlsx}.
     *
     * @param nodeType      node type label (e.g. {@code "MRF"})
     * @param activity      activity label (e.g. {@code "ANNOUNCEMENT_LOADING"})
     * @param rulesFilePath path to the YAML validation-rules file
     * @param outputDir     directory where the file will be written; {@code null} writes to CWD
     * @return {@link CiqTemplateResult} — always returned; never throws
     */
    public CiqTemplateResult generate(String nodeType, String activity,
                                      String rulesFilePath, String outputDir) {

        log.info("Generating CIQ template for {} — {}", nodeType, activity);
        String fileName   = nodeType.toUpperCase() + "_" + activity.toUpperCase() + "_CIQ.xlsx";
        String outputPath = (outputDir == null) ? fileName : outputDir + "/" + fileName;

        try {
            ValidationRulesConfig rules = new ValidationRulesLoader().load(rulesFilePath);
            return doGenerate(nodeType, activity, rules, outputPath, fileName);
        } catch (IOException e) {
            log.error("Failed to generate CIQ template: {}", e.getMessage(), e);
            return CiqTemplateResult.failure(e.getMessage());
        }
    }

    private CiqTemplateResult doGenerate(String nodeType, String activity,
                                         ValidationRulesConfig rules,
                                         String outputPath, String fileName) throws IOException {

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFCellStyle headerStyle = buildHeaderStyle(wb);

            // All sheets — in the order they appear in rules.getSheets() (YAML order).
            // Index, Node-ID, User-ID, and data sheets are all declared under sheets: generically.
            if (rules.getSheets() != null) {
                for (Map.Entry<String, SheetRules> entry : rules.getSheets().entrySet()) {
                    List<String> cols = entry.getValue().getColumns() != null
                            ? new ArrayList<>(entry.getValue().getColumns().keySet())
                            : Collections.<String>emptyList();
                    createSheet(wb, entry.getKey(), cols, entry.getValue(), headerStyle);
                    log.info("  Sheet '{}': {}", entry.getKey(), cols);
                }
            }

            // Column_Guide sheet — describes every column across all sheets
            createColumnGuideSheet(wb, rules, headerStyle);
            log.info("  Column_Guide sheet added");

            // Write file
            File outFile = new File(outputPath);
            if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                wb.write(fos);
            }
            log.info("Template written to: {}", outputPath);
        }
        return CiqTemplateResult.success(fileName, outputPath);
    }

    // -------------------------------------------------------------------------
    // Sheet creation
    // -------------------------------------------------------------------------

    private void createSheet(XSSFWorkbook wb, String sheetName, List<String> columns,
                             SheetRules sheetRules, XSSFCellStyle headerStyle) {
        Sheet sheet = wb.createSheet(sheetName);
        if (columns.isEmpty()) return;

        // Header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i));
            cell.setCellStyle(headerStyle);
        }

        // Dropdown validation for enum-type and allowedValues columns
        if (sheetRules != null && sheetRules.getColumns() != null) {
            for (int i = 0; i < columns.size(); i++) {
                ColumnRule rule = sheetRules.getColumns().get(columns.get(i));
                if (rule == null) continue;
                if (rule.isEnum() && rule.getValues() != null && !rule.getValues().isEmpty()) {
                    addDropdown(sheet, i, rule.getValues());
                } else if (rule.getAllowedValues() != null && !rule.getAllowedValues().isEmpty()) {
                    addDropdown(sheet, i, rule.getAllowedValues());
                }
            }
        }

        // Column widths: max of header length and a minimum of 20 chars
        for (int i = 0; i < columns.size(); i++) {
            int width = Math.max(columns.get(i).length() + 6, 20);
            sheet.setColumnWidth(i, width * 256);
        }
    }

    // -------------------------------------------------------------------------
    // Column Guide sheet
    // -------------------------------------------------------------------------

    private void createColumnGuideSheet(XSSFWorkbook wb, ValidationRulesConfig rules,
                                        XSSFCellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Column_Guide");

        // Guide header row
        String[] headers = {"Sheet", "Column", "Type", "Required", "Allowed Values", "Constraints", "Description"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // All sheets come from rules.getSheets() — Index, Node-ID, User-ID and data sheets alike
        Map<String, SheetRules> allSheets = new LinkedHashMap<>();
        if (rules.getSheets() != null)
            allSheets.putAll(rules.getSheets());

        // Alternating row background for readability
        XSSFCellStyle altStyle = wb.createCellStyle();
        XSSFColor lightGrey = new XSSFColor(new byte[]{(byte) 242, (byte) 242, (byte) 242}, null);
        altStyle.setFillForegroundColor(lightGrey);
        altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        altStyle.setWrapText(true);

        XSSFCellStyle plainStyle = wb.createCellStyle();
        plainStyle.setWrapText(true);

        int rowNum = 1;
        for (Map.Entry<String, SheetRules> sheetEntry : allSheets.entrySet()) {
            String sheetName = sheetEntry.getKey();
            SheetRules sheetRules = sheetEntry.getValue();
            if (sheetRules.getColumns() == null) continue;

            for (Map.Entry<String, ColumnRule> colEntry : sheetRules.getColumns().entrySet()) {
                String colName = colEntry.getKey();
                ColumnRule rule = colEntry.getValue();

                XSSFCellStyle rowStyle = (rowNum % 2 == 0) ? altStyle : plainStyle;
                Row row = sheet.createRow(rowNum++);

                createStyledCell(row, 0, sheetName, rowStyle);
                createStyledCell(row, 1, colName, rowStyle);
                createStyledCell(row, 2, inferType(rule), rowStyle);
                createStyledCell(row, 3, requiredLabel(rule), rowStyle);
                createStyledCell(row, 4, allowedValuesLabel(rule), rowStyle);
                createStyledCell(row, 5, buildConstraints(rule), rowStyle);
                createStyledCell(row, 6, rule.getDescription() != null ? rule.getDescription() : "", rowStyle);
            }
        }

        // Column widths
        int[] widths = {18, 30, 14, 22, 40, 45, 50};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String inferType(ColumnRule rule) {
        if (rule == null) return "Text";
        // Explicit type field takes precedence over legacy boolean flags
        String t = rule.getType();
        if (t != null) {
            switch (t.toLowerCase()) {
                case "enum":      return "Enum";
                case "integer":   return "Integer";
                case "decimal":   return "Decimal";
                case "date":      return "Date";
                case "time":      return "Time";
                case "datetime":  return "DateTime";
                case "boolean":   return "Boolean";
                case "email":     return "Email";
                case "phone":     return "Phone";
                case "ip":        return "IP Address";
                case "mac":       return "MAC Address";
                case "cidr":      return "CIDR";
                case "hostname":  return "Hostname";
                case "fqdn":      return "FQDN";
                case "protocol":  return "Protocol";
                case "urlscheme": return "URL Scheme";
                default:          break;
            }
        }
        // Legacy boolean flags
        if (rule.isEmail())   return "Email";
        if (rule.isInteger()) return "Integer";
        if (rule.getAllowedValues() != null && !rule.getAllowedValues().isEmpty()) return "Enum";
        if (rule.getAllowedRanges() != null && !rule.getAllowedRanges().isEmpty()) return "Integer (ranges)";
        if (rule.getPattern() != null) return "Text (pattern)";
        return "Text";
    }

    private String requiredLabel(ColumnRule rule) {
        if (rule == null) return "Optional";
        if (rule.isRequired()) return "Required";
        if (rule.getRequiredWhen() != null)
            return "Required when " + rule.getRequiredWhen().getColumn()
                    + " = " + rule.getRequiredWhen().getValue();
        return "Optional";
    }

    private String allowedValuesLabel(ColumnRule rule) {
        if (rule == null) return "";
        // enum type uses values field
        if (rule.isEnum() && rule.getValues() != null && !rule.getValues().isEmpty())
            return String.join(", ", rule.getValues());
        // string constraint uses allowedValues field
        if (rule.getAllowedValues() != null && !rule.getAllowedValues().isEmpty())
            return String.join(", ", rule.getAllowedValues());
        return "";
    }

    private String buildConstraints(ColumnRule rule) {
        if (rule == null) return "";
        List<String> parts = new ArrayList<>();
        // String constraints
        if (rule.getMinLength() != null || rule.getMaxLength() != null) {
            String len = "Length: ";
            if (rule.getMinLength() != null) len += rule.getMinLength();
            len += "..";
            if (rule.getMaxLength() != null) len += rule.getMaxLength();
            parts.add(len);
        }
        // Integer constraints
        if (rule.getMinValue() != null || rule.getMaxValue() != null) {
            String range = "Range: ";
            if (rule.getMinValue() != null) range += rule.getMinValue();
            range += "..";
            if (rule.getMaxValue() != null) range += rule.getMaxValue();
            parts.add(range);
        }
        if (rule.getAllowedRanges() != null && !rule.getAllowedRanges().isEmpty()) {
            List<String> ranges = new ArrayList<>();
            for (com.nokia.ciq.validator.config.IntRange r : rule.getAllowedRanges())
                ranges.add(r.getMin() + ".." + r.getMax());
            parts.add("Ranges: " + String.join(" | ", ranges));
        }
        // Decimal constraints
        if (rule.getMinDecimal() != null || rule.getMaxDecimal() != null) {
            String range = "Range: ";
            if (rule.getMinDecimal() != null) range += rule.getMinDecimal();
            range += "..";
            if (rule.getMaxDecimal() != null) range += rule.getMaxDecimal();
            parts.add(range);
        }
        if (rule.getPrecision() != null) {
            parts.add("Max decimal places: " + rule.getPrecision());
        }
        // Date/time format
        if (rule.getFormat() != null) {
            parts.add("Format: " + rule.getFormat());
        }
        // Boolean accept
        if (rule.getAccept() != null) {
            parts.add("Accept: " + rule.getAccept());
        }
        // IP accepts
        if (rule.getAccepts() != null) {
            parts.add("Accepts: " + rule.getAccepts());
        }
        // Pattern override
        if (rule.getPattern() != null) {
            String msg = rule.getPatternMessage() != null ? rule.getPatternMessage()
                    : "Pattern: " + rule.getPattern();
            parts.add(msg);
        }
        // Cross-sheet reference
        if (rule.getCrossRef() != null)
            parts.add("Ref: " + rule.getCrossRef().getSheet() + "." + rule.getCrossRef().getColumn());
        return String.join("; ", parts);
    }

    // -------------------------------------------------------------------------
    // Dropdown (data validation)
    // -------------------------------------------------------------------------

    private void addDropdown(Sheet sheet, int colIndex, List<String> allowedValues) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        CellRangeAddressList range   = new CellRangeAddressList(1, VALIDATION_ROWS, colIndex, colIndex);
        DataValidationConstraint constraint = helper.createExplicitListConstraint(
                allowedValues.toArray(new String[0]));
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setSuppressDropDownArrow(false);   // show arrow button
        validation.setShowErrorBox(true);
        validation.createErrorBox("Invalid value",
                "Allowed: " + String.join(", ", allowedValues));
        sheet.addValidationData(validation);
    }

    // -------------------------------------------------------------------------
    // Styles
    // -------------------------------------------------------------------------

    private XSSFCellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();

        // Nokia dark blue background, white bold text
        XSSFColor darkBlue = new XSSFColor(new byte[]{(byte) 31, (byte) 78, (byte) 121}, null);
        style.setFillForegroundColor(darkBlue);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }
}

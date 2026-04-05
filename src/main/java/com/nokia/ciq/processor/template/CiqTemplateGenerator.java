package com.nokia.ciq.processor.template;

import com.nokia.ciq.validator.config.NodeIdSheetConfig;
import com.nokia.ciq.validator.config.SheetRules;
import com.nokia.ciq.validator.config.UserIdSheetConfig;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a blank CIQ Excel template from a {@link ValidationRulesConfig}.
 *
 * <p>The workbook contains:
 * <ol>
 *   <li><b>Index</b> — header row derived from {@code indexSheet.columns}</li>
 *   <li><b>Data sheets</b> — one sheet per entry in {@code sheets:}, columns from YAML</li>
 *   <li><b>Node_ID sheet</b> — header from {@code nodeIdSheet} config</li>
 *   <li><b>User_ID sheet</b> — header from {@code userIdSheet} config (if configured)</li>
 * </ol>
 *
 * <p>Columns that have {@code allowedValues} in the YAML get an Excel dropdown validation
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

            // 1. INDEX sheet
            if (rules.getIndexSheet() != null
                    && rules.getIndexSheet().getColumns() != null
                    && !rules.getIndexSheet().getColumns().isEmpty()) {
                List<String> cols = new ArrayList<>(rules.getIndexSheet().getColumns().keySet());
                createSheet(wb, "Index", cols, rules.getIndexSheet(), headerStyle);
                log.info("  Index sheet: {}", cols);
            }

            // 2. Data sheets (order preserved from YAML LinkedHashMap)
            if (rules.getSheets() != null) {
                for (Map.Entry<String, SheetRules> entry : rules.getSheets().entrySet()) {
                    List<String> cols = entry.getValue().getColumns() != null
                            ? new ArrayList<>(entry.getValue().getColumns().keySet())
                            : Collections.<String>emptyList();
                    createSheet(wb, entry.getKey(), cols, entry.getValue(), headerStyle);
                    log.info("  Data sheet '{}': {}", entry.getKey(), cols);
                }
            }

            // 3. NODE_ID sheet
            NodeIdSheetConfig nodeIdCfg = rules.getNodeIdSheet() != null
                    ? rules.getNodeIdSheet() : new NodeIdSheetConfig();
            List<String> nodeIdCols = (nodeIdCfg.getColumns() != null && !nodeIdCfg.getColumns().isEmpty())
                    ? new ArrayList<>(nodeIdCfg.getColumns().keySet())
                    : Arrays.asList(nodeIdCfg.getNodeColumn(), nodeIdCfg.getNiamColumn());
            createSheet(wb, nodeIdCfg.getName(), nodeIdCols, nodeIdCfg, headerStyle);
            log.info("  '{}' sheet: {}", nodeIdCfg.getName(), nodeIdCols);

            // 4. USER_ID sheet (optional)
            if (rules.getUserIdSheet() != null) {
                UserIdSheetConfig userIdCfg = rules.getUserIdSheet();
                List<String> userIdCols = (userIdCfg.getColumns() != null && !userIdCfg.getColumns().isEmpty())
                        ? new ArrayList<>(userIdCfg.getColumns().keySet())
                        : Arrays.asList(userIdCfg.getCrGroupColumn(), userIdCfg.getEmailColumn());
                createSheet(wb, userIdCfg.getName(), userIdCols, userIdCfg, headerStyle);
                log.info("  '{}' sheet: {}", userIdCfg.getName(), userIdCols);
            }

            // 5. Column_Guide sheet — describes every column across all sheets
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

        // Dropdown validation for columns with allowedValues
        if (sheetRules != null && sheetRules.getColumns() != null) {
            for (int i = 0; i < columns.size(); i++) {
                ColumnRule rule = sheetRules.getColumns().get(columns.get(i));
                if (rule != null && rule.getAllowedValues() != null && !rule.getAllowedValues().isEmpty()) {
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

        // Collect all sheet→column entries in order
        Map<String, SheetRules> allSheets = new LinkedHashMap<>();
        if (rules.getIndexSheet() != null && rules.getIndexSheet().getColumns() != null)
            allSheets.put("Index", rules.getIndexSheet());
        if (rules.getSheets() != null)
            allSheets.putAll(rules.getSheets());
        NodeIdSheetConfig nodeIdCfg = rules.getNodeIdSheet() != null
                ? rules.getNodeIdSheet() : new NodeIdSheetConfig();
        if (nodeIdCfg.getColumns() != null)
            allSheets.put(nodeIdCfg.getName(), nodeIdCfg);
        if (rules.getUserIdSheet() != null && rules.getUserIdSheet().getColumns() != null)
            allSheets.put(rules.getUserIdSheet().getName(), rules.getUserIdSheet());

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
        if (rule == null || rule.getAllowedValues() == null || rule.getAllowedValues().isEmpty())
            return "";
        return String.join(", ", rule.getAllowedValues());
    }

    private String buildConstraints(ColumnRule rule) {
        if (rule == null) return "";
        List<String> parts = new ArrayList<>();
        if (rule.getMinLength() != null || rule.getMaxLength() != null) {
            String len = "Length: ";
            if (rule.getMinLength() != null) len += rule.getMinLength();
            len += "..";
            if (rule.getMaxLength() != null) len += rule.getMaxLength();
            parts.add(len);
        }
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
        if (rule.getPattern() != null) {
            String msg = rule.getPatternMessage() != null ? rule.getPatternMessage()
                    : "Pattern: " + rule.getPattern();
            parts.add(msg);
        }
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

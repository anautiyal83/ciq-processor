package com.nokia.ciq.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.model.NodeEntry;
import com.nokia.ciq.reader.util.FileNamingUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Apache POI-based implementation of {@link CiqReader}.
 *
 * <p>Reads Index and Node_ID sheets first, then processes each table sheet
 * listed in the Index. Writes one JSON file per sheet plus an index JSON.
 *
 * <p>Edge cases handled:
 * <ul>
 *   <li>Blank rows (all-null data cells) are skipped</li>
 *   <li>Null-named header columns are excluded</li>
 *   <li>Header row is located by scanning for "Node" and "Action" columns</li>
 *   <li>Sheet name truncation (Excel 31-char limit): prefix match used as fallback</li>
 *   <li>Numeric cells formatted as integers where value is whole number</li>
 *   <li>Rows before the header row (label/legend rows) are skipped</li>
 * </ul>
 */
public class ExcelCiqReader implements CiqReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelCiqReader.class);

    private static final String SHEET_INDEX   = "Index";
    private static final String SHEET_NODE_ID = "Node_ID";

    private final ObjectMapper mapper;

    public ExcelCiqReader() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public CiqReadResult read(String ciqFilePath, String outputDir, String nodeType, String activity) {
        log.info("Reading CIQ: {}", ciqFilePath);
        log.info("Output dir:  {}", outputDir);
        log.info("Node type:   {}, Activity: {}", nodeType, activity);

        // Validate input file before attempting to open it
        if (ciqFilePath == null || ciqFilePath.trim().isEmpty()) {
            return CiqReadResult.failure("CIQ file path must not be null or empty");
        }
        File ciqFile = new File(ciqFilePath);
        if (!ciqFile.exists()) {
            return CiqReadResult.failure("CIQ file not found: " + ciqFilePath);
        }
        if (!ciqFile.isFile()) {
            return CiqReadResult.failure("CIQ path is not a file: " + ciqFilePath);
        }
        if (!ciqFile.getName().toLowerCase().endsWith(".xlsx")) {
            return CiqReadResult.failure("CIQ file must be a .xlsx file, got: " + ciqFile.getName());
        }
        if (!ciqFile.canRead()) {
            return CiqReadResult.failure("CIQ file is not readable (check permissions): " + ciqFilePath);
        }

        File outDir = new File(outputDir);
        if (!outDir.exists() && !outDir.mkdirs()) {
            return CiqReadResult.failure("Cannot create output directory: " + outputDir);
        }

        try (FileInputStream fis = new FileInputStream(ciqFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // 1. Read Node_ID sheet → niam mapping
            Map<String, String> niamMapping = readNiamMapping(workbook);
            log.info("NIAM mapping: {} entries", niamMapping.size());

            // 2. Read Index sheet → node/crGroup/tables mapping
            CiqIndex index = readIndex(workbook, nodeType, activity, niamMapping);
            List<String> tables = index.getAllTables();
            log.info("Index: {} node entries, {} unique tables", index.getEntries().size(), tables.size());

            // 3. Write index JSON
            File indexFile = new File(outDir, FileNamingUtil.indexFileName(nodeType, activity));
            mapper.writeValue(indexFile, index);
            log.info("Written: {}", indexFile);

            // 4. For each table in the index, read the sheet and write JSON
            int sheetsRead   = 0;
            int sheetsSkipped = 0;
            int totalRows    = 0;
            List<String> skipped = new ArrayList<>();

            for (String tableName : tables) {
                Sheet sheet = findSheet(workbook, tableName);
                if (sheet == null) {
                    log.warn("Sheet not found for table '{}' — skipping", tableName);
                    skipped.add(tableName);
                    sheetsSkipped++;
                    continue;
                }
                CiqSheet ciqSheet = readSheet(sheet, tableName);
                File sheetFile = new File(outDir, FileNamingUtil.sheetFileName(nodeType, activity, tableName));
                mapper.writeValue(sheetFile, ciqSheet);
                log.info("Written: {} ({} rows)", sheetFile, ciqSheet.getRows().size());
                sheetsRead++;
                totalRows += ciqSheet.getRows().size();
            }

            // 5. Build result message
            int jsonFilesCreated = sheetsRead + 1; // +1 for index
            StringBuilder msg = new StringBuilder();
            msg.append("Sheets read: ").append(sheetsRead);
            msg.append(", Total rows: ").append(totalRows);
            msg.append(", JSON files created: ").append(jsonFilesCreated);
            msg.append(" (").append(indexFile.getName());
            for (String t : tables) {
                if (!skipped.contains(t)) {
                    msg.append(", ").append(FileNamingUtil.sheetFileName(nodeType, activity, t));
                }
            }
            msg.append(")");
            if (sheetsSkipped > 0) {
                msg.append(", Sheets not found (skipped): ").append(skipped);
            }
            msg.append(", Nodes: ").append(niamMapping.size());

            String message = msg.toString();
            log.info("CIQ read complete. {}", message);
            return CiqReadResult.success(message);

        } catch (NoSuchMethodError e) {
            // Consuming application has an incompatible Apache POI version on its classpath.
            // ciq-reader requires poi-ooxml 5.x. Exclude the old POI from your project and
            // add poi-ooxml:5.2.5 explicitly.
            String msg = "Apache POI version conflict detected. ciq-reader requires poi-ooxml 5.x "
                    + "but an older version is present on the classpath. "
                    + "Fix: exclude the old poi dependency from your project and add "
                    + "poi-ooxml:5.2.5 explicitly. Technical detail: " + e.getMessage();
            log.error(msg);
            return CiqReadResult.failure(msg);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // POI throws IOException wrapping InvalidFormatException for corrupt/non-xlsx files
            if (msg.toLowerCase().contains("invalid") || msg.toLowerCase().contains("format")
                    || (e.getCause() != null && e.getCause().getClass().getName()
                            .contains("InvalidFormatException"))) {
                log.error("CIQ file is not a valid xlsx workbook: {}", ciqFilePath);
                return CiqReadResult.failure("CIQ file is not a valid xlsx workbook: " + ciqFilePath);
            }
            log.error("CIQ read failed: {}", msg, e);
            return CiqReadResult.failure("Failed to read CIQ file: " + msg);
        }
    }

    // -------------------------------------------------------------------------
    // Index sheet
    // -------------------------------------------------------------------------

    private CiqIndex readIndex(Workbook workbook, String nodeType, String activity,
                               Map<String, String> niamMapping) {
        CiqIndex index = new CiqIndex();
        index.setNodeType(nodeType);
        index.setActivity(activity);
        index.setNiamMapping(niamMapping);

        Sheet sheet = workbook.getSheet(SHEET_INDEX);
        if (sheet == null) {
            log.warn("'{}' sheet not found — index will be empty", SHEET_INDEX);
            return index;
        }

        // Find header row (contains "Node", "CRGroup", "Tables")
        int headerRowIdx = findHeaderRow(sheet, "Node", "CRGroup");
        if (headerRowIdx < 0) {
            log.warn("Header row not found in Index sheet");
            return index;
        }

        Row headerRow = sheet.getRow(headerRowIdx);
        int nodeCol    = findColumnIndex(headerRow, "Node");
        int crGroupCol = findColumnIndex(headerRow, "CRGroup");
        // Use only the FIRST "Tables" column — the user-selection column.
        // A second "Tables" column (separated by blank columns) is a dropdown
        // catalog/reference and must not be read as a selection.
        int tablesCol  = findColumnIndex(headerRow, "Tables");

        if (nodeCol < 0 || crGroupCol < 0 || tablesCol < 0) {
            log.warn("Required columns not found in Index sheet");
            return index;
        }

        // key: "Node|CRGroup" → NodeEntry (to deduplicate rows with the same group)
        Map<String, NodeEntry> entryMap = new LinkedHashMap<>();

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String node    = getCellString(row.getCell(nodeCol));
            String crGroup = getCellString(row.getCell(crGroupCol));
            if (isBlank(node) && isBlank(crGroup)) continue;

            // Read the single selected table from the selection column
            String table = getCellString(row.getCell(tablesCol));
            if (isBlank(table)) continue;   // row has no table selected — skip

            String key = (node == null ? "" : node) + "|" + (crGroup == null ? "" : crGroup);
            NodeEntry entry = entryMap.computeIfAbsent(key, k -> {
                NodeEntry e = new NodeEntry();
                e.setNode(node);
                e.setCrGroup(crGroup);
                return e;
            });

            if (!entry.getTables().contains(table)) {
                entry.getTables().add(table);
            }
        }

        index.setEntries(new ArrayList<>(entryMap.values()));
        return index;
    }

    // -------------------------------------------------------------------------
    // Node_ID sheet
    // -------------------------------------------------------------------------

    private Map<String, String> readNiamMapping(Workbook workbook) {
        Map<String, String> map = new LinkedHashMap<>();
        Sheet sheet = workbook.getSheet(SHEET_NODE_ID);
        if (sheet == null) {
            log.warn("'{}' sheet not found — NIAM mapping will be empty", SHEET_NODE_ID);
            return map;
        }

        int headerRowIdx = findHeaderRow(sheet, "Node", "NIAM");
        if (headerRowIdx < 0) {
            log.warn("Header row not found in Node_ID sheet");
            return map;
        }

        Row headerRow  = sheet.getRow(headerRowIdx);
        int nodeCol    = findColumnIndex(headerRow, "Node");
        int niamCol    = findColumnIndex(headerRow, "NIAM");

        if (nodeCol < 0 || niamCol < 0) return map;

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String node = getCellString(row.getCell(nodeCol));
            String niam = getCellString(row.getCell(niamCol));
            if (!isBlank(node)) {
                map.put(node, niam);
            }
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Data sheet reading
    // -------------------------------------------------------------------------

    private CiqSheet readSheet(Sheet sheet, String tableName) {
        CiqSheet ciqSheet = new CiqSheet();
        ciqSheet.setSheetName(tableName);

        // Find header row: look for a row with "Node" and "Action" columns
        int headerRowIdx = findHeaderRow(sheet, "Node", "Action");
        if (headerRowIdx < 0) {
            log.warn("Header row not found in sheet '{}' — sheet will have no rows", sheet.getSheetName());
            return ciqSheet;
        }

        Row headerRow = sheet.getRow(headerRowIdx);

        // Build ordered list of (columnIndex, columnName), skipping null-named columns
        List<int[]> colMap = new ArrayList<>();  // [0]=colIdx
        List<String> colNames = new ArrayList<>();
        for (int c = 0; c <= headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            String name = getCellString(cell);
            if (!isBlank(name)) {
                colMap.add(new int[]{c});
                colNames.add(name);
            }
        }
        ciqSheet.setColumns(colNames);

        // Read data rows
        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Map<String, String> data = new LinkedHashMap<>();
            boolean hasAnyValue = false;
            for (int i = 0; i < colMap.size(); i++) {
                int colIdx = colMap.get(i)[0];
                String value = getCellString(row.getCell(colIdx));
                data.put(colNames.get(i), value);
                if (value != null) hasAnyValue = true;
            }

            if (!hasAnyValue) continue;  // skip blank rows

            // 1-based Excel row number
            ciqSheet.getRows().add(new CiqRow(r + 1, data));
        }

        return ciqSheet;
    }

    // -------------------------------------------------------------------------
    // Sheet lookup (handles Excel 31-char truncation)
    // -------------------------------------------------------------------------

    /**
     * Find a sheet by table name.
     * Tries exact match first, then prefix match (Excel truncates names to 31 chars).
     */
    private Sheet findSheet(Workbook workbook, String tableName) {
        // Exact match
        Sheet sheet = workbook.getSheet(tableName);
        if (sheet != null) return sheet;

        // Prefix match: sheet name is a prefix of the table name (truncation)
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String sheetName = workbook.getSheetName(i);
            if (tableName.startsWith(sheetName) || sheetName.startsWith(tableName)) {
                log.debug("Matched table '{}' to sheet '{}' (truncation)", tableName, sheetName);
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Header row detection
    // -------------------------------------------------------------------------

    /**
     * Scan rows from the top looking for a row that contains all requiredHeaders as cell values.
     * Returns the row index, or -1 if not found.
     */
    private int findHeaderRow(Sheet sheet, String... requiredHeaders) {
        int maxScan = Math.min(10, sheet.getLastRowNum() + 1);
        for (int r = 0; r <= maxScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Set<String> cellValues = new HashSet<>();
            for (Cell cell : row) {
                String v = getCellString(cell);
                if (v != null) cellValues.add(v);
            }
            boolean allFound = true;
            for (String header : requiredHeaders) {
                if (!cellValues.contains(header)) { allFound = false; break; }
            }
            if (allFound) return r;
        }
        return -1;
    }

    private int findColumnIndex(Row headerRow, String columnName) {
        for (Cell cell : headerRow) {
            if (columnName.equals(getCellString(cell))) return cell.getColumnIndex();
        }
        return -1;
    }

    private List<Integer> findAllColumnIndices(Row headerRow, String columnName) {
        List<Integer> indices = new ArrayList<>();
        for (Cell cell : headerRow) {
            if (columnName.equals(getCellString(cell))) indices.add(cell.getColumnIndex());
        }
        return indices;
    }

    // -------------------------------------------------------------------------
    // Cell value extraction
    // -------------------------------------------------------------------------

    /**
     * Convert a POI cell to a String value.
     * - Numeric whole numbers → "42" (not "42.0")
     * - Numeric decimals     → "3.14"
     * - Boolean              → "true" / "false"
     * - Blank/null           → null
     */
    private String getCellString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                String s = cell.getStringCellValue().trim();
                return s.isEmpty() ? null : s;
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Evaluate cached formula result
                try {
                    CellType resultType = cell.getCachedFormulaResultType();
                    if (resultType == CellType.STRING) {
                        String fs = cell.getStringCellValue().trim();
                        return fs.isEmpty() ? null : fs;
                    }
                    if (resultType == CellType.NUMERIC) {
                        double fd = cell.getNumericCellValue();
                        if (fd == Math.floor(fd) && !Double.isInfinite(fd)) {
                            return String.valueOf((long) fd);
                        }
                        return String.valueOf(fd);
                    }
                    if (resultType == CellType.BOOLEAN) {
                        return String.valueOf(cell.getBooleanCellValue());
                    }
                } catch (Exception e) {
                    // fall through to null
                }
                return null;
            case BLANK:
            case _NONE:
            default:
                return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

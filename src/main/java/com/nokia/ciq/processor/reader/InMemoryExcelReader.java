package com.nokia.ciq.processor.reader;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.model.NodeEntry;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Reads a Nokia CIQ Excel workbook entirely into memory and returns an
 * {@link InMemoryCiqDataStore} — no JSON files are written to disk.
 *
 * <p>Parsing logic mirrors {@link com.nokia.ciq.reader.ExcelCiqReader}:
 * <ol>
 *   <li>Read <em>Node_ID</em> sheet → NIAM mapping (node → login ID)</li>
 *   <li>Read <em>Index</em> sheet  → node / CRGroup / table list</li>
 *   <li>For each table listed in the Index, read the corresponding data sheet</li>
 * </ol>
 *
 * <p>Edge cases handled identically to {@code ExcelCiqReader}:
 * <ul>
 *   <li>Blank rows (all-null data cells) are skipped</li>
 *   <li>Null-named header columns are excluded</li>
 *   <li>Header row located by scanning first 10 rows for required column names</li>
 *   <li>Excel 31-char sheet-name truncation: prefix match used as fallback</li>
 *   <li>Numeric whole numbers formatted as integers (no trailing ".0")</li>
 * </ul>
 */
public class InMemoryExcelReader {

    private static final Logger log = LoggerFactory.getLogger(InMemoryExcelReader.class);

    private static final String SHEET_INDEX   = "Index";
    private static final String SHEET_NODE_ID = "Node_ID";

    /**
     * Populated by {@link #readGroupIndex} when a GROUP-mode INDEX sheet is detected
     * ({@code GROUP | NODE} columns present, no {@code TABLES} column).
     * Empty in all other modes.
     */
    private final Map<String, List<String>> groupToNodes = new LinkedHashMap<>();

    /**
     * Returns the GROUP→Nodes map built from the INDEX sheet during {@link #read}.
     * Non-empty only for GROUP-mode CIQ files.
     */
    public Map<String, List<String>> getGroupToNodes() {
        return groupToNodes;
    }

    /**
     * Read the given CIQ Excel file into an {@link InMemoryCiqDataStore}.
     *
     * @param ciqFilePath absolute path to the .xlsx CIQ workbook
     * @param nodeType    e.g. {@code "SBC"}
     * @param activity    e.g. {@code "FIXED_LINE_CONFIGURATION"}
     * @return populated in-memory data store
     * @throws IOException if the file cannot be opened or is not a valid xlsx workbook
     */
    public InMemoryCiqDataStore read(String ciqFilePath, String nodeType, String activity)
            throws IOException {

        log.info("Reading CIQ into memory: {}", ciqFilePath);
        log.info("Node type: {}, Activity: {}", nodeType, activity);

        validateFile(ciqFilePath);

        try (FileInputStream fis = new FileInputStream(ciqFilePath);
             Workbook wb = new XSSFWorkbook(fis)) {

            Map<String, String> niamMapping = readNiamMapping(wb);
            log.info("NIAM mapping: {} entries", niamMapping.size());

            CiqIndex index = readIndex(wb, nodeType, activity, niamMapping);
            List<String> tables = index.getAllTables();
            log.info("Index: {} node entries, {} unique tables",
                    index.getEntries().size(), tables.size());

            // No proper Index (no Tables column found) — read all non-NIAM sheets as data sheets.
            // The Index sheet itself may contain data (e.g. MRF CIQs use Index for CR grouping)
            // so it is NOT excluded here; only Node_ID (NIAM mapping) is excluded.
            if (tables.isEmpty()) {
                tables = new ArrayList<>();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    String sName = wb.getSheetName(i);
                    if (!sName.equalsIgnoreCase(SHEET_NODE_ID)) {
                        tables.add(sName);
                    }
                }
                if (!tables.isEmpty()) {
                    log.info("No Index — reading {} sheet(s) directly: {}", tables.size(), tables);
                }
            }

            Map<String, CiqSheet> sheets = new LinkedHashMap<>();
            for (String tableName : tables) {
                Sheet sheet = findSheet(wb, tableName);
                if (sheet == null) {
                    log.warn("Sheet not found for table '{}' — skipping", tableName);
                    continue;
                }
                CiqSheet ciqSheet = readSheet(sheet, tableName);
                sheets.put(tableName, ciqSheet);
                log.info("Loaded table '{}': {} rows", tableName, ciqSheet.getRows().size());
            }

            InMemoryCiqDataStore store = new InMemoryCiqDataStore(index, sheets);

            // Capture INDEX and NODE_ID sheets as CiqSheet objects for column-rule validation.
            // These are stored separately so they do not appear in getAvailableSheets() and
            // cannot interfere with the validateIndexSheets cross-check.
            Sheet rawIndex = wb.getSheet(SHEET_INDEX);
            if (rawIndex != null) {
                store.setRawIndexSheet(readSheet(rawIndex, SHEET_INDEX));
                log.debug("Captured raw Index sheet ({} rows)",
                        store.getRawIndexSheet().getRows().size());
            }
            Sheet rawNodeId = wb.getSheet(SHEET_NODE_ID);
            if (rawNodeId != null) {
                store.setRawNodeIdSheet(readSheet(rawNodeId, SHEET_NODE_ID));
                log.debug("Captured raw Node_ID sheet ({} rows)",
                        store.getRawNodeIdSheet().getRows().size());
            }

            return store;
        }
    }

    // -------------------------------------------------------------------------
    // Index sheet
    // -------------------------------------------------------------------------

    private CiqIndex readIndex(Workbook wb, String nodeType, String activity,
                               Map<String, String> niamMapping) {
        CiqIndex index = new CiqIndex();
        index.setNodeType(nodeType);
        index.setActivity(activity);
        index.setNiamMapping(niamMapping);

        Sheet sheet = wb.getSheet(SHEET_INDEX);
        if (sheet == null) {
            log.warn("'{}' sheet not found — index will be empty", SHEET_INDEX);
            return index;
        }

        // --- Attempt 1: proper INDEX mode (Node + CRGroup columns required) ---
        int headerRowIdx = findHeaderRow(sheet, "Node", "CRGroup");
        if (headerRowIdx >= 0) {
            Row headerRow  = sheet.getRow(headerRowIdx);
            int nodeCol    = findColumnIndex(headerRow, "Node");
            int crGroupCol = findColumnIndex(headerRow, "CRGroup");
            // Use only the FIRST "Tables" column — the user-selection column.
            // A second "Tables" column (separated by blank columns) is a dropdown
            // catalog/reference and must not be read as a selection.
            int tablesCol  = findColumnIndex(headerRow, "Tables");

            if (nodeCol < 0 || crGroupCol < 0 || tablesCol < 0) {
                log.warn("Required columns (Node, CRGroup, Tables) not found in Index sheet");
                return index;
            }

            Map<String, NodeEntry> entryMap = new LinkedHashMap<>();

            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String node    = getCellString(row.getCell(nodeCol));
                String crGroup = getCellString(row.getCell(crGroupCol));
                if (isBlank(node) && isBlank(crGroup)) continue;

                String table = getCellString(row.getCell(tablesCol));
                if (isBlank(table)) continue;

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

        // --- Attempt 2: GROUP mode (GROUP + NODE columns, no CRGroup/Tables) ---
        headerRowIdx = findHeaderRow(sheet, "Group", "Node");
        if (headerRowIdx >= 0) {
            readGroupIndex(sheet, headerRowIdx);
            log.info("GROUP mode detected in Index sheet — {} group(s): {}",
                    groupToNodes.size(), groupToNodes.keySet());
            return index;   // CiqIndex intentionally empty for GROUP mode
        }

        log.warn("Header row not found in Index sheet (no Node+CRGroup and no Group+Node columns)");
        return index;
    }

    /**
     * Reads the GROUP-mode INDEX sheet (columns: {@code GROUP | NODE}) into
     * {@link #groupToNodes}.  Called only when GROUP mode is detected.
     */
    private void readGroupIndex(Sheet sheet, int headerRowIdx) {
        Row headerRow = sheet.getRow(headerRowIdx);
        int groupCol  = findColumnIndex(headerRow, "Group");
        int nodeCol   = findColumnIndex(headerRow, "Node");
        if (groupCol < 0 || nodeCol < 0) return;

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String group = getCellString(row.getCell(groupCol));
            String node  = getCellString(row.getCell(nodeCol));
            if (isBlank(group) && isBlank(node)) continue;
            if (!isBlank(group) && !isBlank(node)) {
                groupToNodes.computeIfAbsent(group.trim(), k -> new ArrayList<>())
                            .add(node.trim());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Node_ID sheet
    // -------------------------------------------------------------------------

    private Map<String, String> readNiamMapping(Workbook wb) {
        Map<String, String> map = new LinkedHashMap<>();

        Sheet sheet = wb.getSheet(SHEET_NODE_ID);
        if (sheet == null) {
            log.warn("'{}' sheet not found — NIAM mapping will be empty", SHEET_NODE_ID);
            return map;
        }

        // Accept both "NIAM" and "NIAM_ID" as the second column header
        int headerRowIdx = findHeaderRow(sheet, "Node", "NIAM");
        if (headerRowIdx < 0) {
            headerRowIdx = findHeaderRow(sheet, "Node", "NIAM_ID");
        }
        if (headerRowIdx < 0) {
            log.warn("Header row not found in Node_ID sheet");
            return map;
        }

        Row headerRow = sheet.getRow(headerRowIdx);
        int nodeCol   = findColumnIndex(headerRow, "Node");
        int niamCol   = findColumnIndex(headerRow, "NIAM");
        if (niamCol < 0) niamCol = findColumnIndex(headerRow, "NIAM_ID");

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

        // Try to find header row: Node+Action (normal), then Group+Action (GROUP mode),
        // then just Node, then just Group (for sheets where Action column is absent).
        int headerRowIdx = findHeaderRow(sheet, "Node", "Action");
        if (headerRowIdx < 0) {
            headerRowIdx = findHeaderRow(sheet, "Group", "Action");
        }
        if (headerRowIdx < 0) {
            headerRowIdx = findHeaderRow(sheet, "Node");
        }
        if (headerRowIdx < 0) {
            headerRowIdx = findHeaderRow(sheet, "Group");
        }
        if (headerRowIdx < 0) {
            log.warn("Header row not found in sheet '{}' — sheet will have no rows",
                    sheet.getSheetName());
            return ciqSheet;
        }

        Row headerRow = sheet.getRow(headerRowIdx);

        List<int[]>  colMap   = new ArrayList<>();
        List<String> colNames = new ArrayList<>();
        for (int c = 0; c <= headerRow.getLastCellNum(); c++) {
            String name = getCellString(headerRow.getCell(c));
            if (!isBlank(name)) {
                colMap.add(new int[]{c});
                colNames.add(name);
            }
        }
        ciqSheet.setColumns(colNames);

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Map<String, String> data = new LinkedHashMap<>();
            boolean hasAnyValue = false;
            for (int i = 0; i < colMap.size(); i++) {
                String value = getCellString(row.getCell(colMap.get(i)[0]));
                data.put(colNames.get(i), value);
                if (value != null) hasAnyValue = true;
            }
            if (!hasAnyValue) continue;

            ciqSheet.getRows().add(new CiqRow(r + 1, data));
        }

        return ciqSheet;
    }

    // -------------------------------------------------------------------------
    // Sheet lookup (handles Excel 31-char name truncation)
    // -------------------------------------------------------------------------

    private Sheet findSheet(Workbook wb, String tableName) {
        Sheet sheet = wb.getSheet(tableName);
        if (sheet != null) return sheet;

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String sheetName = wb.getSheetName(i);
            if (tableName.startsWith(sheetName) || sheetName.startsWith(tableName)) {
                log.debug("Matched table '{}' to sheet '{}' (truncation)", tableName, sheetName);
                return wb.getSheetAt(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Header row detection
    // -------------------------------------------------------------------------

    private int findHeaderRow(Sheet sheet, String... requiredHeaders) {
        int maxScan = Math.min(10, sheet.getLastRowNum() + 1);
        for (int r = 0; r <= maxScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Set<String> normalizedCells = new HashSet<>();
            for (Cell cell : row) {
                String v = getCellString(cell);
                if (v != null) normalizedCells.add(normalize(v));
            }
            boolean allFound = true;
            for (String header : requiredHeaders) {
                if (!normalizedCells.contains(normalize(header))) { allFound = false; break; }
            }
            if (allFound) return r;
        }
        return -1;
    }

    /**
     * Finds a column by name. Matching is case-insensitive and underscore-insensitive,
     * so "CRGroup", "CR_GROUP", and "crgroup" all resolve to the same column.
     */
    private int findColumnIndex(Row headerRow, String columnName) {
        String target = normalize(columnName);
        for (Cell cell : headerRow) {
            String v = getCellString(cell);
            if (v != null && normalize(v).equals(target)) return cell.getColumnIndex();
        }
        return -1;
    }

    private static String normalize(String s) {
        return s.replace("_", "").toLowerCase();
    }

    // -------------------------------------------------------------------------
    // Cell value extraction
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    private void validateFile(String ciqFilePath) throws IOException {
        if (ciqFilePath == null || ciqFilePath.trim().isEmpty()) {
            throw new IOException("CIQ file path must not be null or empty");
        }
        File file = new File(ciqFilePath);
        if (!file.exists()) throw new IOException("CIQ file not found: " + ciqFilePath);
        if (!file.isFile()) throw new IOException("CIQ path is not a file: " + ciqFilePath);
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            throw new IOException("CIQ file must be a .xlsx file, got: " + file.getName());
        }
        if (!file.canRead()) {
            throw new IOException("CIQ file is not readable: " + ciqFilePath);
        }
    }
}

package com.nokia.ciq.reader.model;

import java.util.ArrayList;
import java.util.List;

/**
 * All data rows from one CIQ sheet.
 * Rows are data-only (header row excluded, blank rows excluded).
 */
public class CiqSheet {

    /** Logical table name as it appears in the Index sheet (e.g. "CRFTargetList"). */
    private String sheetName;

    /** Ordered list of column header names (null-named columns are excluded). */
    private List<String> columns;

    /** Data rows (blank rows excluded, header row excluded). */
    private List<CiqRow> rows;

    public CiqSheet() {
        this.columns = new ArrayList<>();
        this.rows = new ArrayList<>();
    }

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<CiqRow> getRows() { return rows; }
    public void setRows(List<CiqRow> rows) { this.rows = rows; }
}

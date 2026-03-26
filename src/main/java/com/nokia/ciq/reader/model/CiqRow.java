package com.nokia.ciq.reader.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single data row from a CIQ sheet.
 * Column order is preserved (LinkedHashMap).
 * Null values represent blank/missing cells.
 */
public class CiqRow {

    private int rowNumber;
    private Map<String, String> data;

    public CiqRow() {
        this.data = new LinkedHashMap<>();
    }

    public CiqRow(int rowNumber, Map<String, String> data) {
        this.rowNumber = rowNumber;
        this.data = data;
    }

    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { this.data = data; }

    /** Convenience: get a single column value (null if blank or column absent). */
    public String get(String column) {
        return data.get(column);
    }
}

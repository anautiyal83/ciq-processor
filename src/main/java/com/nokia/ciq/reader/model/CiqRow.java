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

    /**
     * Get a column value by name. Case-insensitive — "Node", "NODE", and "node" all match.
     * Returns null if the column is absent or blank.
     */
    public String get(String column) {
        String v = data.get(column);
        if (v != null) return v;
        // Case-insensitive fallback
        String upper = column.toUpperCase();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getKey().toUpperCase().equals(upper)) return entry.getValue();
        }
        return null;
    }
}

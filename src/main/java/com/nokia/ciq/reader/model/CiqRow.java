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
     * Get a column value by name. Matching is case-insensitive and underscore-insensitive,
     * so "CRGroup", "CR_GROUP", and "crgroup" all resolve to the same column.
     * Returns null if the column is absent or blank.
     */
    public String get(String column) {
        String v = data.get(column);
        if (v != null) return v;
        String target = normalize(column);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (normalize(entry.getKey()).equals(target)) return entry.getValue();
        }
        return null;
    }

    private static String normalize(String s) {
        return s.replace("_", "").toLowerCase();
    }
}

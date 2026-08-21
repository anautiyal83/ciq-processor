package com.nokia.ciq.reader.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A single data row from a CIQ sheet.
 * Column order is preserved (LinkedHashMap).
 * Null values represent blank/missing cells.
 */
public class CiqRow {

    private int rowNumber;
    private Map<String, String> data;

    /**
     * Untrimmed cell text, populated only when the sheet is read with
     * {@code settings.trimCellValues: false}.
     *
     * <p>{@link #data} always holds the trimmed value so every validator keeps comparing
     * against clean text (patterns, allowedValues, maxLength, unique, ...). JSON generation
     * reads this map instead, so the emitted document preserves the spacing exactly as it
     * was typed in the CIQ. {@code null} when raw capture is off - the normal case.
     */
    private Map<String, String> rawData;

    public CiqRow() {
        this.data = new LinkedHashMap<>();
    }

    public CiqRow(int rowNumber, Map<String, String> data) {
        this.rowNumber = rowNumber;
        this.data = data;
    }

    public CiqRow(int rowNumber, Map<String, String> data, Map<String, String> rawData) {
        this.rowNumber = rowNumber;
        this.data = data;
        this.rawData = rawData;
    }

    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { this.data = data; }

    public Map<String, String> getRawData() { return rawData; }
    public void setRawData(Map<String, String> rawData) { this.rawData = rawData; }

    /**
     * Values to emit to JSON: the untrimmed map when raw capture is enabled, otherwise
     * the normal (trimmed) map.
     */
    public Map<String, String> getOutputData() {
        return rawData != null ? rawData : data;
    }

    /**
     * Get a column value by name. Matching is case-insensitive, underscore-insensitive,
     * and space-insensitive, so "CRGroup", "CR_GROUP", "CR GROUP", and "crgroup" all
     * resolve to the same column.
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
        return s.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }
}

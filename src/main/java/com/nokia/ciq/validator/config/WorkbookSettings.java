package com.nokia.ciq.validator.config;

/**
 * Workbook-level (and per-sheet-level) reading settings.
 *
 * <p>Can appear at the top level of the validation-rules YAML under {@code settings:}
 * and/or inside a sheet definition under {@code sheets.&lt;Name&gt;.settings:}.
 * Per-sheet values override the global ones.
 */
public class WorkbookSettings {

    /** 0-based index of the header row. Default: 0. */
    private int headerRow = 0;

    /** 0-based index of the first data row. Default: 1. */
    private int dataStartRow = 1;

    /**
     * Strip leading/trailing whitespace from every data cell value. Default: {@code true}
     * (the historical behaviour - the reader always trimmed).
     *
     * <p>Set {@code trimCellValues: false} to keep cell values exactly as typed, so a value
     * like {@code "Update SPAM "} reaches both the validators and the generated JSON with its
     * trailing space intact.
     *
     * <p>Nullable on purpose: {@code null} means "not declared", which lets a per-sheet
     * {@code settings:} block override the global value in either direction.
     */
    private Boolean trimCellValues;

    /** Skip rows where all cells are blank. Default: true. */
    private boolean ignoreBlankRows = true;

    /** Header matching is case-sensitive. Default: false. */
    private boolean caseSensitiveHeaders = false;

    /** Value comparison is case-sensitive. Default: true. */
    private boolean caseSensitiveValues = true;

    public int getHeaderRow() { return headerRow; }
    public void setHeaderRow(int headerRow) { this.headerRow = headerRow; }

    public int getDataStartRow() { return dataStartRow; }
    public void setDataStartRow(int dataStartRow) { this.dataStartRow = dataStartRow; }

    // NOTE: getter and setter must both use Boolean - SnakeYAML resolves the bean property by
    // matching their types, and a boolean/Boolean mismatch makes the property unwritable.

    /** Raw declared value ({@code null} = not declared) - used when merging per-sheet overrides. */
    public Boolean getTrimCellValues() { return trimCellValues; }

    public void setTrimCellValues(Boolean trimCellValues) { this.trimCellValues = trimCellValues; }

    /** Effective value: trimming is ON unless the YAML explicitly declares {@code false}. */
    public boolean isTrimEnabled() { return trimCellValues == null || trimCellValues; }

    public boolean isIgnoreBlankRows() { return ignoreBlankRows; }
    public void setIgnoreBlankRows(boolean ignoreBlankRows) { this.ignoreBlankRows = ignoreBlankRows; }

    public boolean isCaseSensitiveHeaders() { return caseSensitiveHeaders; }
    public void setCaseSensitiveHeaders(boolean caseSensitiveHeaders) {
        this.caseSensitiveHeaders = caseSensitiveHeaders;
    }

    public boolean isCaseSensitiveValues() { return caseSensitiveValues; }
    public void setCaseSensitiveValues(boolean caseSensitiveValues) {
        this.caseSensitiveValues = caseSensitiveValues;
    }
}

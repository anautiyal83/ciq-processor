package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Uniqueness constraint spanning one or more columns in a sheet.
 *
 * <p>When multiple columns are listed the uniqueness check applies to the
 * composite key (column values joined with {@code "|"}).
 * Example:
 * <pre>
 *   unique:
 *     columns: [Node, Interface]
 * </pre>
 */
public class UniqueRule {

    /** Column names that together form the unique key. */
    private List<String> columns;

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
}

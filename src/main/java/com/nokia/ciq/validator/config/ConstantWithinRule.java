package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Validates that one or more columns hold the same value across all rows
 * that share the same value in a grouping column.
 *
 * <p>Example — GROUP and CRGROUP must be identical for all rows in the same REGION:
 * <pre>
 * workbook_rules:
 *   - constant_within:
 *       sheet:   INDEX
 *       group:   REGION
 *       columns: [GROUP, CRGROUP]
 * </pre>
 */
public class ConstantWithinRule {

    /** Sheet to inspect. */
    private String sheet;

    /** Column whose distinct values define the partitions (e.g. "REGION"). */
    private String group;

    /** Columns that must hold a single constant value within each partition. */
    private List<String> columns;

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
}

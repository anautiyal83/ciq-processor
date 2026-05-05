package com.nokia.ciq.validator.config;

/**
 * Validates that at least one non-blank value exists in this column
 * for each unique value in a grouping column.
 *
 * Example: EMAIL must have at least one value per CRGroup.
 * If CRGroup=CR1 appears on 5 rows, at least one of those rows must
 * have a non-blank EMAIL value.
 *
 * YAML structure:
 * <pre>
 *   columns:
 *     EMAIL:
 *       minOnePerGroup:
 *         groupByColumn: CRGroup
 * </pre>
 */
public class MinOnePerGroup {

    /** The column whose unique values define the groups (e.g. "CRGroup"). */
    private String groupByColumn;

    public String getGroupByColumn() { return groupByColumn; }
    public void setGroupByColumn(String groupByColumn) { this.groupByColumn = groupByColumn; }
}

package com.nokia.ciq.validator.config;

/**
 * Specifies that a column's value must exist in another sheet/column.
 *
 * Special sheet name "_index" refers to the CiqIndex node list (niamMapping keys).
 *
 * Example YAML:
 * <pre>
 *   crossRef:
 *     sheet: "_index"
 *     column: "node"        # checks niamMapping keys
 * </pre>
 *
 * <pre>
 *   crossRef:
 *     sheet: "IbcfVirtualNetworkTable"
 *     column: "Record.VN_ID"
 * </pre>
 */
public class CrossRef {

    /** Sheet name to look up, or "_index" for the index node list. */
    private String sheet;

    /** Column in that sheet whose values form the valid set. */
    private String column;

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }
}

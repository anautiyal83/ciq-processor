package com.nokia.ciq.validator.config;

/**
 * Cross-sheet subset/superset/match reference.
 *
 * <p>Both {@code from} and {@code to} are in the form {@code "SheetName.ColumnName"}.
 *
 * <p>An optional {@code where} clause filters the rows of the {@code from} sheet
 * before collecting values.  Format: {@code "ColumnName = value"}.
 *
 * <p>Examples:
 * <pre>
 *   subset:
 *     from: "DataSheet.Node"
 *     to:   "Node_ID.Node"
 *
 *   # Only validate Index.GROUP rows where TABLES = CRFTargetList
 *   match:
 *     from: Index.GROUP
 *     where: "TABLES = CRFTargetList"
 *     to: CRFTargetList.Group
 * </pre>
 */
public class SubsetRule {

    /** Source reference: "Sheet.Column" */
    private String from;

    /** Target reference: "Sheet.Column" */
    private String to;

    /**
     * Optional row filter applied to the {@code from} sheet before collecting values.
     * Format: {@code "ColumnName = value"} (single equality condition).
     * When set, only rows where the specified column equals the given value are included.
     */
    private String where;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getWhere() { return where; }
    public void setWhere(String where) { this.where = where; }
}

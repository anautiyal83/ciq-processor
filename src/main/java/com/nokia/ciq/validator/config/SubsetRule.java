package com.nokia.ciq.validator.config;

/**
 * Cross-sheet subset/superset/match reference.
 *
 * <p>Both {@code from} and {@code to} are in the form {@code "SheetName.ColumnName"}.
 * Example:
 * <pre>
 *   subset:
 *     from: "DataSheet.Node"
 *     to:   "Node_ID.Node"
 * </pre>
 */
public class SubsetRule {

    /** Source reference: "Sheet.Column" */
    private String from;

    /** Target reference: "Sheet.Column" */
    private String to;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
}

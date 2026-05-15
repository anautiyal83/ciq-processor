package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Cross-sheet OR-subset rule.
 *
 * <p>Every value in {@code from} must appear in at least one of the {@code to} columns.
 *
 * <p>Example — each INDEX.NODE must exist in Node_Details.NODE1 or Node_Details.NODE2:
 * <pre>
 * workbook_rules:
 *   - subset_any:
 *       from: INDEX.NODE
 *       to:
 *         - Node_Details.NODE1
 *         - Node_Details.NODE2
 * </pre>
 */
public class SubsetAnyRule {

    /** Source reference: "Sheet.Column" */
    private String from;

    /** List of target references: "Sheet.Column" — value must exist in at least one. */
    private List<String> to;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public List<String> getTo() { return to; }
    public void setTo(List<String> to) { this.to = to; }
}

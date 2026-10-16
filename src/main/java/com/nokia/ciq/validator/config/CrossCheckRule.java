package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Streamlined cross-sheet comparison rule — the developer-friendly successor to
 * {@link CrossSheetCompareRule}.
 *
 * <p>Compares columns across two or more sheets for every row that shares the
 * same composite key.  Each entry in {@link #columns} is a {@code [Sheet, Column]}
 * pair; the key columns are listed in {@link #on}.
 *
 * <p>Example YAML:
 * <pre>
 * workbook_rules:
 *   - cross_check:
 *       on: [ZONE, "Test IMSI"]
 *       columns:
 *         - [Call Barring, Action]
 *         - [Call Forwarding, Action]
 *       relation: not_both
 *       values: [ENABLE]
 * </pre>
 *
 * <p>Optional fields:
 * <ul>
 *   <li>{@code from} — driver sheet that scopes which keys are checked</li>
 *   <li>{@code filter} — single-equality row filter on the driver sheet, e.g.
 *       {@code "Status = Active"}</li>
 *   <li>{@code missing} — {@code skip} (default) or {@code error}</li>
 *   <li>{@code pairs} — required for {@code relation: opposite},
 *       e.g. {@code [[ENABLE, DISABLE]]}</li>
 *   <li>{@code message} — custom message template; {@code {KeyCol}},
 *       {@code {SheetName}} (value from that sheet's column) are substituted</li>
 * </ul>
 */
public class CrossCheckRule {

    /** Composite key columns used to join rows across sheets. */
    private List<String> on;

    /**
     * Columns to compare — each entry is a {@code [SheetName, ColumnName]} pair.
     * At least two entries are required.
     */
    private List<List<String>> columns;

    /**
     * {@code equals} | {@code not_equals} | {@code opposite} | {@code not_both}.
     * Defaults to {@code not_both}.
     */
    private String relation;

    /**
     * Values counted as "active" for {@code relation: not_both} — e.g. {@code [ENABLE]}.
     * Defaults to {@code [ENABLE]}.
     */
    private List<String> values;

    /**
     * Value pairs forming the closed domain for {@code relation: opposite}.
     * E.g. {@code [[ENABLE, DISABLE]]}.
     */
    private List<List<String>> pairs;

    /** Optional driver sheet that scopes which keys are checked. */
    private String from;

    /** Optional single-equality row filter on the driver sheet, e.g. {@code "Status = Active"}. */
    private String filter;

    /** {@code skip} (default) | {@code error} — behaviour when a key exists on one side only. */
    private String missing;

    /** Optional custom message template. */
    private String message;

    // -- Getters / Setters --

    public List<String> getOn() { return on; }
    public void setOn(List<String> on) { this.on = on; }

    public List<List<String>> getColumns() { return columns; }
    public void setColumns(List<List<String>> columns) { this.columns = columns; }

    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }

    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }

    public List<List<String>> getPairs() { return pairs; }
    public void setPairs(List<List<String>> pairs) { this.pairs = pairs; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public String getMissing() { return missing; }
    public void setMissing(String missing) { this.missing = missing; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    // -- Convenience resolvers --

    /** Parsed relation, defaulting to {@code NOT_BOTH}. */
    public CrossSheetCompareRule.Relation resolveRelation() {
        if (relation == null) return CrossSheetCompareRule.Relation.NOT_BOTH;
        String r = relation.trim().toUpperCase(java.util.Locale.ROOT);
        if ("EQUALS".equals(r))     return CrossSheetCompareRule.Relation.EQUALS;
        if ("NOT_EQUALS".equals(r)) return CrossSheetCompareRule.Relation.NOT_EQUALS;
        if ("OPPOSITE".equals(r))   return CrossSheetCompareRule.Relation.OPPOSITE;
        return CrossSheetCompareRule.Relation.NOT_BOTH;
    }

    /** Parsed missing-key behaviour, defaulting to {@code SKIP}. */
    public CrossSheetCompareRule.OnMissing resolveMissing() {
        return missing != null && "ERROR".equalsIgnoreCase(missing.trim())
                ? CrossSheetCompareRule.OnMissing.ERROR
                : CrossSheetCompareRule.OnMissing.SKIP;
    }

    /** Effective active values — defaults to {@code ["ENABLE"]} when not specified. */
    public List<String> resolveValues() {
        if (values != null && !values.isEmpty()) return values;
        return java.util.Collections.singletonList("ENABLE");
    }
}

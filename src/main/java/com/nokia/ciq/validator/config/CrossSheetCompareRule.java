package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Compares a column on one sheet against a column on another sheet for every row
 * pair that shares the same composite key.
 *
 * <p>Where {@link SetRule} and {@link SetMatchRule} compare value <em>sets</em> per
 * partition, this rule compares the paired cell <em>values</em> themselves, so it can
 * express relations such as "these two services must never both be enabled for the
 * same subscriber".
 *
 * <p>Both sides are addressed purely by sheet name and column name, so the same rule
 * serves any node type: repeat the block with different sheets to cover another pair.
 *
 * <p>Example — for each (ZONE, Test IMSI) listed in Index, Call Barring and Call
 * Forwarding must never both be enabled (both disabled is fine):
 * <pre>
 * workbook_rules:
 *   - cross_sheet_compare:
 *       driver:
 *         sheet: "Index"
 *         keys:  ["ZONE", "Test IMSI"]
 *       left:
 *         sheet:  "Call Barring"
 *         column: "Action"
 *       right:
 *         sheet:  "Call Forwarding"
 *         column: "Action"
 *       relation: not_both
 *       active_values: ["ENABLE"]
 *       on_missing: skip
 *       message: "ZONE '{ZONE}' / Test IMSI '{Test IMSI}': Call Barring '{left}' and Call Forwarding '{right}' must be opposite"
 * </pre>
 */
public class CrossSheetCompareRule {

    /** Relation checked between the paired left and right values. */
    public enum Relation { EQUALS, NOT_EQUALS, OPPOSITE, NOT_BOTH }

    /** What to do when a key is present on one side only. */
    public enum OnMissing { SKIP, ERROR }

    /** Optional: restricts the keys checked to those present on this sheet. */
    private Driver driver;

    /** Left-hand sheet + column. */
    private Side left;

    /** Right-hand sheet + column. */
    private Side right;

    /**
     * {@code equals} | {@code not_equals} | {@code opposite} | {@code not_both}.
     * Defaults to {@code opposite}.
     *
     * <p>{@code opposite} is strict: the two values must differ and both must come from
     * one declared {@code value_pairs} entry, so DISABLE/DISABLE fails. {@code not_both}
     * is mutual exclusion: it fails only when both sides hold an {@code active_values}
     * entry, so DISABLE/DISABLE and ENABLE/DISABLE both pass.
     */
    private String relation;

    /**
     * Value pairs forming the closed domain for {@code relation: opposite} — e.g.
     * {@code [[ENABLE, DISABLE]]}. Both compared values must belong to the same pair
     * and must differ. Ignored by the other relations.
     */
    private List<List<String>> valuePairs;

    /**
     * Values counted as "active" by {@code relation: not_both} — e.g. {@code [ENABLE]}.
     * The rule fails only when the left and right cells both hold one of these. Ignored
     * by the other relations.
     */
    private List<String> activeValues;

    /** {@code skip} (default) | {@code error} — behaviour when a key exists on one side only. */
    private String onMissing;

    /**
     * Optional message template. {@code {ColumnName}} substitutes that key column's value,
     * {@code {left}} and {@code {right}} substitute the compared cells. A generated message
     * is used when absent.
     */
    private String message;

    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }

    public Side getLeft() { return left; }
    public void setLeft(Side left) { this.left = left; }

    public Side getRight() { return right; }
    public void setRight(Side right) { this.right = right; }

    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }

    public List<List<String>> getValuePairs() { return valuePairs; }
    public void setValuePairs(List<List<String>> valuePairs) { this.valuePairs = valuePairs; }

    public List<String> getActiveValues() { return activeValues; }
    public void setActiveValues(List<String> activeValues) { this.activeValues = activeValues; }

    public String getOnMissing() { return onMissing; }
    public void setOnMissing(String onMissing) { this.onMissing = onMissing; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /** Parsed {@link #relation}, defaulting to {@link Relation#OPPOSITE}. */
    public Relation resolveRelation() {
        if (relation == null) return Relation.OPPOSITE;
        String r = relation.trim().toUpperCase(java.util.Locale.ROOT);
        if ("EQUALS".equals(r))     return Relation.EQUALS;
        if ("NOT_EQUALS".equals(r)) return Relation.NOT_EQUALS;
        if ("NOT_BOTH".equals(r))   return Relation.NOT_BOTH;
        return Relation.OPPOSITE;
    }

    /** Parsed {@link #onMissing}, defaulting to {@link OnMissing#SKIP}. */
    public OnMissing resolveOnMissing() {
        return onMissing != null && "ERROR".equalsIgnoreCase(onMissing.trim())
                ? OnMissing.ERROR : OnMissing.SKIP;
    }

    // -------------------------------------------------------------------------

    /** One side of the comparison. */
    public static class Side {
        /** Sheet holding the compared column. */
        private String sheet;
        /** Column whose value is compared. */
        private String column;
        /**
         * Join key columns on this sheet. Optional — falls back to {@code driver.keys},
         * which is the usual case. Set it when this sheet names the key columns
         * differently (e.g. {@code IMSI} where Index says {@code Test IMSI}); the list
         * must then be positionally aligned with {@code driver.keys}.
         */
        private List<String> keys;

        public String getSheet() { return sheet; }
        public void setSheet(String sheet) { this.sheet = sheet; }

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }

        public List<String> getKeys() { return keys; }
        public void setKeys(List<String> keys) { this.keys = keys; }
    }

    /** Optional driver sheet limiting which keys are compared. */
    public static class Driver {
        /** Sheet whose rows enumerate the keys to check (e.g. "Index"). */
        private String sheet;
        /** Key columns on the driver sheet; the canonical key names used in messages. */
        private List<String> keys;
        /**
         * Optional row filter applied to the driver sheet before keys are collected.
         * Format: {@code "ColumnName = value"} (single equality condition), matching
         * the {@code where} supported by {@link SetRule}.
         */
        private String where;

        public String getSheet() { return sheet; }
        public void setSheet(String sheet) { this.sheet = sheet; }

        public List<String> getKeys() { return keys; }
        public void setKeys(List<String> keys) { this.keys = keys; }

        public String getWhere() { return where; }
        public void setWhere(String where) { this.where = where; }
    }
}

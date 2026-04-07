package com.nokia.ciq.validator.validator;

/**
 * Canonical string operators used across all row-rule validators.
 *
 * <h3>Canonical names (use these in YAML)</h3>
 * <table border="1">
 * <tr><th>Canonical</th><th>Symbol alias</th><th>Meaning</th></tr>
 * <tr><td>equals</td><td>==</td><td>a == b (case-insensitive for strings)</td></tr>
 * <tr><td>notEquals</td><td>!=</td><td>a != b</td></tr>
 * <tr><td>greaterThan</td><td>&gt;</td><td>a &gt; b</td></tr>
 * <tr><td>greaterThanOrEquals</td><td>&gt;=</td><td>a &gt;= b</td></tr>
 * <tr><td>lessThan</td><td>&lt;</td><td>a &lt; b</td></tr>
 * <tr><td>lessThanOrEquals</td><td>&lt;=</td><td>a &lt;= b</td></tr>
 * <tr><td>blank</td><td>—</td><td>value is null or empty</td></tr>
 * <tr><td>notBlank</td><td>—</td><td>value is not null/empty</td></tr>
 * <tr><td>contains</td><td>—</td><td>case-insensitive substring</td></tr>
 * <tr><td>exists</td><td>—</td><td>cross-sheet: value found in sheet.column</td></tr>
 * <tr><td>notExists</td><td>—</td><td>cross-sheet: value absent from sheet.column</td></tr>
 * </table>
 *
 * <p>Symbol aliases ({@code ==}, {@code !=}, {@code >}, {@code >=}, {@code <}, {@code <=})
 * are accepted wherever an operator is parsed, but the canonical string form is preferred.
 */
public final class Operator {

    // Canonical names
    public static final String EQUALS                  = "equals";
    public static final String NOT_EQUALS              = "notequals";
    public static final String GREATER_THAN            = "greaterthan";
    public static final String GREATER_THAN_OR_EQUALS  = "greaterthanorequals";
    public static final String LESS_THAN               = "lessthan";
    public static final String LESS_THAN_OR_EQUALS     = "lessthanorequals";
    public static final String BLANK                   = "blank";
    public static final String NOT_BLANK               = "notblank";
    public static final String CONTAINS                = "contains";
    public static final String EXISTS                  = "exists";
    public static final String NOT_EXISTS              = "notexists";

    private Operator() {}

    /**
     * Normalises an operator token to its lower-case canonical form, mapping
     * symbol aliases to their string equivalents.
     *
     * <pre>
     *   "=="  → "equals"
     *   "!="  → "notequals"
     *   ">="  → "greaterthanorequals"
     *   "<="  → "lessthanorequals"
     *   ">"   → "greaterthan"
     *   "<"   → "lessthan"
     *   any other string → lower-cased as-is
     * </pre>
     */
    public static String normalize(String op) {
        if (op == null) return "";
        switch (op.trim()) {
            case "==": return EQUALS;
            case "!=": return NOT_EQUALS;
            case ">=": return GREATER_THAN_OR_EQUALS;
            case "<=": return LESS_THAN_OR_EQUALS;
            case ">":  return GREATER_THAN;
            case "<":  return LESS_THAN;
            default:   return op.trim().toLowerCase();
        }
    }

    /**
     * Applies a normalised comparison operator to a pre-computed {@code compare} result
     * (negative = a&lt;b, zero = a==b, positive = a&gt;b).
     *
     * @param normalizedOp result of {@link #normalize(String)}
     * @param cmp          result of a comparator (sign convention same as {@link Comparable})
     * @return {@code true} if the comparison holds
     */
    public static boolean evaluate(String normalizedOp, int cmp) {
        switch (normalizedOp) {
            case EQUALS:                 return cmp == 0;
            case NOT_EQUALS:             return cmp != 0;
            case GREATER_THAN:           return cmp >  0;
            case GREATER_THAN_OR_EQUALS: return cmp >= 0;
            case LESS_THAN:              return cmp <  0;
            case LESS_THAN_OR_EQUALS:    return cmp <= 0;
            default:                     return false;
        }
    }
}

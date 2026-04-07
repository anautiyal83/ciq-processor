package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * A conditional predicate that can be attached to a {@link SheetRowRule} via {@code when:}.
 *
 * <p>Supports single-cell conditions, compound conditions, and cross-sheet conditions:
 * <pre>
 * # same-sheet condition
 * when:
 *   column: Action
 *   operator: equals
 *   value: MODIFY
 *
 * # compound condition
 * when:
 *   all:
 *     - column: Action
 *       operator: equals
 *       value: MODIFY
 *     - column: Type
 *       operator: notBlank
 *
 * # cross-sheet condition — true when current row's Node_Name value exists in Node_Details
 * when:
 *   sheet: Node_Details
 *   column: Node_Name
 *   operator: exists
 * </pre>
 *
 * <p>Supported operators:
 * <ul>
 *   <li>{@code equals}, {@code notEquals} — case-insensitive string comparison</li>
 *   <li>{@code blank}, {@code notBlank} — presence check</li>
 *   <li>{@code greaterThan}, {@code lessThan} — numeric or lexicographic comparison</li>
 *   <li>{@code contains} — case-insensitive substring check</li>
 *   <li>{@code exists}, {@code notExists} — cross-sheet: current row's {@code column} value
 *       must (or must not) exist in {@code sheet.column}</li>
 * </ul>
 */
public class RowCondition {

    /** Column name to evaluate (same-sheet) or the column to look up cross-sheet. */
    private String column;

    /** Comparison operator. See class javadoc for supported values. */
    private String operator;

    /** Value to compare against (not used for blank/notBlank/exists/notExists). */
    private String value;

    /**
     * Sheet name for cross-sheet operators ({@code exists}, {@code notExists}).
     * When set, the current row's value for {@code column} is looked up in
     * this sheet's {@code column} to evaluate the condition.
     */
    private String sheet;

    /** All of these sub-conditions must be true (AND logic). */
    private List<RowCondition> all;

    /** At least one of these sub-conditions must be true (OR logic). */
    private List<RowCondition> any;

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public List<RowCondition> getAll() { return all; }
    public void setAll(List<RowCondition> all) { this.all = all; }

    public List<RowCondition> getAny() { return any; }
    public void setAny(List<RowCondition> any) { this.any = any; }
}

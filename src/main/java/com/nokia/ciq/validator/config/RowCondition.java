package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * A conditional predicate that can be attached to a {@link SheetRowRule} via {@code when:}.
 *
 * <p>Supports single-cell conditions and compound conditions:
 * <pre>
 * when:
 *   column: Action
 *   operator: equals
 *   value: MODIFY
 *
 * when:
 *   all:
 *     - column: Action
 *       operator: equals
 *       value: MODIFY
 *     - column: Type
 *       operator: notBlank
 * </pre>
 *
 * <p>Supported operators: {@code equals}, {@code notEquals}, {@code blank},
 * {@code notBlank}, {@code greaterThan}, {@code lessThan}, {@code contains}.
 * String comparisons are case-insensitive.
 */
public class RowCondition {

    /** Column name to evaluate. Used for single-cell conditions. */
    private String column;

    /** Comparison operator. See class javadoc for supported values. */
    private String operator;

    /** Value to compare against (not used for blank/notBlank). */
    private String value;

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

    public List<RowCondition> getAll() { return all; }
    public void setAll(List<RowCondition> all) { this.all = all; }

    public List<RowCondition> getAny() { return any; }
    public void setAny(List<RowCondition> any) { this.any = any; }
}

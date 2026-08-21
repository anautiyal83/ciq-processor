package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * One entry in an {@code allowedValuesWhen} list.
 *
 * <p>When the trigger condition is satisfied, the validated column's value is constrained:
 * <ul>
 *   <li>Empty {@code allowedValues} list → the column <em>must be blank</em>.</li>
 *   <li>Non-empty {@code allowedValues} list → the column value must match one of the
 *       listed values (case-insensitive).</li>
 * </ul>
 *
 * <p>The {@code operator} field controls how the trigger column is compared against
 * {@code value}.  Defaults to {@code equals} when omitted (backward-compatible).
 * Supported operators mirror those used in row rules:
 * {@code equals}, {@code notEquals}, {@code contains}, {@code blank}, {@code notBlank},
 * {@code greaterThan}, {@code greaterThanOrEquals}, {@code lessThan}, {@code lessThanOrEquals}.
 * Symbol aliases ({@code ==}, {@code !=}, {@code >}, {@code >=}, {@code <}, {@code <=})
 * are also accepted.
 *
 * <p>YAML usage:
 * <pre>
 * SomeColumn:
 *   allowedValuesWhen:
 *     # equals (default) — must be blank when Action=MODIFY
 *     - column: Action
 *       value: MODIFY
 *       allowedValues: []
 *
 *     # notEquals — must be Y when ColumnA != X
 *     - column: ColumnA
 *       operator: notEquals
 *       value: X
 *       allowedValues: [Y]
 *
 *     # blank — must be Z when TriggerCol is empty
 *     - column: TriggerCol
 *       operator: blank
 *       allowedValues: [Z]
 *
 *     # notBlank — must be blank when TriggerCol has any value
 *     - column: TriggerCol
 *       operator: notBlank
 *       allowedValues: []
 *
 *     # greaterThan — numeric comparison
 *     - column: COUNT
 *       operator: greaterThan
 *       value: "0"
 *       allowedValues: [ACTIVE]
 * </pre>
 */
public class ConditionalAllowedValues {

    /** The trigger column in the same row to check. */
    private String column;

    /**
     * Comparison operator applied to the trigger column value.
     * Defaults to {@code equals} when omitted.
     * Accepted: {@code equals}, {@code notEquals}, {@code contains},
     * {@code blank}, {@code notBlank},
     * {@code greaterThan}, {@code greaterThanOrEquals},
     * {@code lessThan}, {@code lessThanOrEquals}.
     * Symbol aliases {@code ==}, {@code !=}, {@code >}, {@code >=}, {@code <}, {@code <=}
     * are also accepted.
     */
    private String operator;

    /**
     * The value to compare against (case-insensitive for string operators).
     * Not used for {@code blank} and {@code notBlank} operators.
     */
    private String value;

    /**
     * Allowed values when the condition is met.
     * Empty list means the column must be blank.
     */
    private List<String> allowedValues;

    /**
     * Compound trigger expression (alternative to {@code column}/{@code operator}/{@code value}).
     * When set, the trigger fires if this expression evaluates true for the current row.
     * Supports {@code &&} / {@code ||} and per-term comparisons
     * {@code ==}, {@code !=}, {@code >}, {@code >=}, {@code <}, {@code <=}, with string
     * literals in single or double quotes. Column names may be the full header or the
     * leaf (last dotted segment). Example:
     * <pre>
     * allowedValuesWhen:
     *   - when: "INVITE_DLG == 'No' && ROAMING_CALLS == 'No' && INVITE_DLG_FOR_EMERGENCY_CALLS == 'No'"
     *     allowedValues: ["No"]
     * </pre>
     */
    private String when;

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public String getWhen() { return when; }
    public void setWhen(String when) { this.when = when; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public List<String> getAllowedValues() { return allowedValues; }
    public void setAllowedValues(List<String> allowedValues) { this.allowedValues = allowedValues; }
}

package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * One entry in an {@code allowedValuesWhen} list.
 *
 * <p>When the trigger column equals the trigger value, the validated column's value
 * is constrained:
 * <ul>
 *   <li>Empty {@code allowedValues} list → the column <em>must be blank</em>.</li>
 *   <li>Non-empty {@code allowedValues} list → the column value must match one of the
 *       listed values (case-insensitive).</li>
 * </ul>
 *
 * <p>YAML usage:
 * <pre>
 * SomeColumn:
 *   allowedValuesWhen:
 *     - column: Action
 *       value: MODIFY
 *       allowedValues: []        # must be blank when Action=MODIFY
 *     - column: Action
 *       value: CREATE
 *       allowedValues: [A, B]   # must be A or B when Action=CREATE
 * </pre>
 */
public class ConditionalAllowedValues {

    /** The trigger column in the same row to check. */
    private String column;

    /** The value that activates this constraint (case-insensitive match). */
    private String value;

    /**
     * Allowed values when the condition is met.
     * Empty list means the column must be blank.
     */
    private List<String> allowedValues;

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public List<String> getAllowedValues() { return allowedValues; }
    public void setAllowedValues(List<String> allowedValues) { this.allowedValues = allowedValues; }
}

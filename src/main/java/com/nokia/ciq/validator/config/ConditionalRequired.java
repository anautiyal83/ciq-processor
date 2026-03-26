package com.nokia.ciq.validator.config;

/**
 * Specifies that a column is required only when another column in the same row
 * matches a given value.
 *
 * Example YAML:
 * <pre>
 *   requiredWhen:
 *     column: Action
 *     value: MODIFY
 * </pre>
 */
public class ConditionalRequired {

    /** The column in the same row to check. */
    private String column;

    /** The value that triggers the requirement (case-insensitive). */
    private String value;

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}

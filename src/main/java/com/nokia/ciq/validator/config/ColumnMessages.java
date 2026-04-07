package com.nokia.ciq.validator.config;

/**
 * Custom error messages for individual validation constraints of a column.
 *
 * <p>Any field left {@code null} will fall back to the default engine message.
 * Appears under {@code sheets.&lt;Sheet&gt;.columns.&lt;Column&gt;.messages:} in the YAML.
 */
public class ColumnMessages {

    /** Message when a required column is blank. */
    private String required;

    /** Message when value is shorter than minLength. */
    private String minLength;

    /** Message when value is longer than maxLength. */
    private String maxLength;

    /** Message when value does not match the pattern. */
    private String pattern;

    /** Message when numeric value is less than min. */
    private String min;

    /** Message when numeric value exceeds max. */
    private String max;

    /** Message when date/time value does not match the format. */
    private String format;

    /** Message when decimal precision exceeds the configured limit. */
    private String precision;

    public String getRequired() { return required; }
    public void setRequired(String required) { this.required = required; }

    public String getMinLength() { return minLength; }
    public void setMinLength(String minLength) { this.minLength = minLength; }

    public String getMaxLength() { return maxLength; }
    public void setMaxLength(String maxLength) { this.maxLength = maxLength; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getMin() { return min; }
    public void setMin(String min) { this.min = min; }

    public String getMax() { return max; }
    public void setMax(String max) { this.max = max; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getPrecision() { return precision; }
    public void setPrecision(String precision) { this.precision = precision; }
}

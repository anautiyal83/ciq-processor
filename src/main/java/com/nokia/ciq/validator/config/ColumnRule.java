package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Validation rules for a single column in a sheet.
 *
 * Corresponds to one entry under sheets.<SheetName>.columns.<ColumnName> in the YAML.
 */
public class ColumnRule {

    /** Column must have a non-null, non-blank value. */
    private boolean required;

    /** Value must be one of these (case-insensitive). Null/blank always passes unless required=true. */
    private List<String> allowedValues;

    /** Value must match this regex pattern. */
    private String pattern;

    /** Custom message shown when pattern validation fails. Defaults to a generic message if null. */
    private String patternMessage;

    /** Minimum string length (inclusive). */
    private Long minLength;

    /** Maximum string length (inclusive). */
    private Long maxLength;

    /** Value must be parseable as an integer. */
    private boolean integer;

    /** Minimum value (inclusive). Only checked when integer=true. */
    private Long minValue;

    /** Maximum value (inclusive). Only checked when integer=true. */
    private Long maxValue;

    /**
     * Value must fall within at least one of these integer ranges.
     * Used for union types where a value can belong to one of several valid ranges.
     * Example: TARGET_ID is valid if 1..1024, 5001..7048, or exactly 0.
     */
    private List<IntRange> allowedRanges;

    /**
     * Column is required only when another column equals a specific value.
     * Example: ActionKey is required when Action=MODIFY.
     */
    private ConditionalRequired requiredWhen;

    /**
     * Value must exist in a specified column of another (or the same) sheet.
     * Example: Node must exist in the index niamMapping keys.
     */
    private CrossRef crossRef;

    // Getters and setters

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public List<String> getAllowedValues() { return allowedValues; }
    public void setAllowedValues(List<String> allowedValues) { this.allowedValues = allowedValues; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getPatternMessage() { return patternMessage; }
    public void setPatternMessage(String patternMessage) { this.patternMessage = patternMessage; }

    public Long getMinLength() { return minLength; }
    public void setMinLength(Long minLength) { this.minLength = minLength; }

    public Long getMaxLength() { return maxLength; }
    public void setMaxLength(Long maxLength) { this.maxLength = maxLength; }

    public boolean isInteger() { return integer; }
    public void setInteger(boolean integer) { this.integer = integer; }

    public Long getMinValue() { return minValue; }
    public void setMinValue(Long minValue) { this.minValue = minValue; }

    public Long getMaxValue() { return maxValue; }
    public void setMaxValue(Long maxValue) { this.maxValue = maxValue; }

    public List<IntRange> getAllowedRanges() { return allowedRanges; }
    public void setAllowedRanges(List<IntRange> allowedRanges) { this.allowedRanges = allowedRanges; }

    public ConditionalRequired getRequiredWhen() { return requiredWhen; }
    public void setRequiredWhen(ConditionalRequired requiredWhen) { this.requiredWhen = requiredWhen; }

    public CrossRef getCrossRef() { return crossRef; }
    public void setCrossRef(CrossRef crossRef) { this.crossRef = crossRef; }
}

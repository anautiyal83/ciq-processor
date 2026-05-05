package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Validation rules for a single column in a sheet.
 *
 * Corresponds to one entry under sheets.&lt;SheetName&gt;.columns.&lt;ColumnName&gt; in the YAML.
 *
 * <h3>Column types</h3>
 * <table border="1">
 * <tr><th>type</th><th>Description</th><th>Extra config fields</th></tr>
 * <tr><td>string</td><td>Free-form text (default)</td><td>minLength, maxLength, pattern, allowedValues</td></tr>
 * <tr><td>integer</td><td>Whole number</td><td>minValue, maxValue, allowedRanges</td></tr>
 * <tr><td>decimal</td><td>Decimal number</td><td>minDecimal, maxDecimal, precision</td></tr>
 * <tr><td>date</td><td>Calendar date</td><td>format (default: yyyy-MM-dd)</td></tr>
 * <tr><td>time</td><td>Time of day</td><td>format (default: HH:mm:ss)</td></tr>
 * <tr><td>datetime</td><td>Date and time</td><td>format (default: yyyy-MM-dd'T'HH:mm:ss)</td></tr>
 * <tr><td>boolean</td><td>True/false value</td><td>accept: true/false | yes/no | 1/0</td></tr>
 * <tr><td>email</td><td>E-mail address</td><td>—</td></tr>
 * <tr><td>phone</td><td>Phone number (E.164)</td><td>pattern override</td></tr>
 * <tr><td>ip</td><td>IP address</td><td>accepts: ipv4 | ipv6 | both</td></tr>
 * <tr><td>mac</td><td>MAC address</td><td>pattern override</td></tr>
 * <tr><td>cidr</td><td>CIDR notation</td><td>pattern override</td></tr>
 * <tr><td>hostname</td><td>RFC 1123 hostname</td><td>pattern override</td></tr>
 * <tr><td>fqdn</td><td>Fully qualified domain name</td><td>pattern override</td></tr>
 * <tr><td>protocol</td><td>Network protocol from allowed list</td><td>values</td></tr>
 * <tr><td>urlScheme</td><td>URL with allowed scheme</td><td>values (allowed schemes)</td></tr>
 * <tr><td>enum</td><td>Closed vocabulary</td><td>values</td></tr>
 * </table>
 *
 * <p>The boolean flags {@code integer} and {@code email} remain supported for backward
 * compatibility with existing YAML files.
 */
public class ColumnRule {

    /**
     * Column data type. See class javadoc for the full list of accepted values.
     * Defaults to {@code string} when omitted.
     */
    private String type;

    /** Column must have a non-null, non-blank value. */
    private boolean required;

    /**
     * Closed set of valid values for {@code type: enum} columns.
     * Validation is case-insensitive.  When this field is set, {@code allowedValues} is ignored.
     */
    private List<String> values;

    /**
     * Allowed-values constraint for {@code type: string} columns (case-insensitive).
     * Use {@code type: enum} + {@code values} instead when the column is a pure controlled vocabulary.
     * Null/blank always passes unless {@code required: true}.
     */
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

    /** Value must be a valid email address (RFC 5322 local@domain format). */
    private boolean email;

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

    /**
     * Value must match a sheet name that exists in the workbook.
     * Useful for Index / routing sheets where a column holds table names.
     *
     * <p>YAML usage:
     * <pre>
     * Tables:
     *   required: true
     *   sheetRef: true
     * </pre>
     */
    private boolean sheetRef;

    /**
     * When {@code sheetRef: true}, controls whether the sheet-name comparison
     * is case-insensitive.  Defaults to {@code false} (exact-case match).
     *
     * <p>Kept separate from {@code ignoreCase} (which applies to column-value
     * comparisons such as {@code allowedValues}) to avoid ambiguity.
     *
     * <p>YAML usage:
     * <pre>
     * Tables:
     *   required: true
     *   sheetRef: true
     *   sheetRefIgnoreCase: true    # "announcement_files" matches "ANNOUNCEMENT_FILES"
     * </pre>
     */
    private boolean sheetRefIgnoreCase;

    /**
     * Date/time format pattern used by {@code type: date}, {@code type: time},
     * and {@code type: datetime}.  Uses {@code java.text.SimpleDateFormat} patterns.
     * Defaults: date → {@code yyyy-MM-dd}, time → {@code HH:mm:ss},
     * datetime → {@code yyyy-MM-dd'T'HH:mm:ss}.
     */
    private String format;

    /**
     * Boolean representation for {@code type: boolean}.
     * Accepted values: {@code true/false} (default), {@code yes/no}, {@code 1/0}.
     * Matching is always case-insensitive.
     */
    private String accept;

    /**
     * IP version acceptance for {@code type: ip}.
     * Accepted values: {@code ipv4} (default), {@code ipv6}, {@code both}.
     */
    private String accepts;

    /** Minimum value (inclusive) for {@code type: decimal}. */
    private Double minDecimal;

    /** Maximum value (inclusive) for {@code type: decimal}. */
    private Double maxDecimal;

    /**
     * Maximum number of decimal places allowed for {@code type: decimal}.
     * Validation fails if the value has more decimal places than {@code precision}.
     */
    private Integer precision;

    /** Optional human-readable description of this column, shown in the Column_Guide sheet. */
    private String description;

    // -------------------------------------------------------------------------
    // Schema.yaml extension fields
    // -------------------------------------------------------------------------

    /** Alternative column name(s) that are treated as equivalent to this column. */
    private List<String> aliases;

    /** Values in this column must be unique within the sheet. */
    private boolean unique;

    /** Value comparison for this column ignores case. */
    private boolean ignoreCase;

    /** Cell may contain multiple comma-separated values. */
    private boolean multi;

    /** Name of a custom validator (registered under {@code validators:} at root level). */
    private String validator;

    /** Custom error messages overriding the default engine messages. */
    private ColumnMessages messages;

    /**
     * At least one non-blank value must exist in this column per unique value
     * of the groupByColumn. Evaluated across all rows after per-row checks.
     * Example: at least one EMAIL required per CRGroup.
     *
     * <p>YAML usage:
     * <pre>
     * EMAIL:
     *   minOnePerGroup:
     *     groupByColumn: CRGroup
     * </pre>
     */
    private MinOnePerGroup minOnePerGroup;

    // Getters and setters

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    /** Returns true if {@code type} matches the given type name (case-insensitive). */
    public boolean isType(String typeName) {
        return typeName != null && typeName.equalsIgnoreCase(type);
    }

    public boolean isEnum()         { return "enum".equalsIgnoreCase(type); }
    public boolean isIntegerType()  { return "integer".equalsIgnoreCase(type); }
    public boolean isDecimalType()  { return "decimal".equalsIgnoreCase(type); }
    public boolean isDateType()     { return "date".equalsIgnoreCase(type); }
    public boolean isTimeType()     { return "time".equalsIgnoreCase(type); }
    public boolean isDatetimeType() { return "datetime".equalsIgnoreCase(type); }
    public boolean isBooleanType()  { return "boolean".equalsIgnoreCase(type); }
    public boolean isEmailType()    { return "email".equalsIgnoreCase(type); }
    public boolean isPhoneType()    { return "phone".equalsIgnoreCase(type); }
    public boolean isIpType()       { return "ip".equalsIgnoreCase(type); }
    public boolean isMacType()      { return "mac".equalsIgnoreCase(type); }
    public boolean isCidrType()     { return "cidr".equalsIgnoreCase(type); }
    public boolean isHostnameType() { return "hostname".equalsIgnoreCase(type); }
    public boolean isFqdnType()     { return "fqdn".equalsIgnoreCase(type); }
    public boolean isProtocolType() { return "protocol".equalsIgnoreCase(type); }
    public boolean isUrlSchemeType(){ return "urlScheme".equalsIgnoreCase(type); }

    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }

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

    public boolean isEmail() { return email; }
    public void setEmail(boolean email) { this.email = email; }

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

    public boolean isSheetRef() { return sheetRef; }
    public void setSheetRef(boolean sheetRef) { this.sheetRef = sheetRef; }

    public boolean isSheetRefIgnoreCase() { return sheetRefIgnoreCase; }
    public void setSheetRefIgnoreCase(boolean sheetRefIgnoreCase) { this.sheetRefIgnoreCase = sheetRefIgnoreCase; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getAccept() { return accept; }
    public void setAccept(String accept) { this.accept = accept; }

    public String getAccepts() { return accepts; }
    public void setAccepts(String accepts) { this.accepts = accepts; }

    public Double getMinDecimal() { return minDecimal; }
    public void setMinDecimal(Double minDecimal) { this.minDecimal = minDecimal; }

    public Double getMaxDecimal() { return maxDecimal; }
    public void setMaxDecimal(Double maxDecimal) { this.maxDecimal = maxDecimal; }

    public Integer getPrecision() { return precision; }
    public void setPrecision(Integer precision) { this.precision = precision; }

    // Schema.yaml extension getters/setters

    public List<String> getAliases() { return aliases; }
    public void setAliases(List<String> aliases) { this.aliases = aliases; }

    public boolean isUnique() { return unique; }
    public void setUnique(boolean unique) { this.unique = unique; }

    public boolean isIgnoreCase() { return ignoreCase; }
    public void setIgnoreCase(boolean ignoreCase) { this.ignoreCase = ignoreCase; }

    public boolean isMulti() { return multi; }
    public void setMulti(boolean multi) { this.multi = multi; }

    public String getValidator() { return validator; }
    public void setValidator(String validator) { this.validator = validator; }

    public ColumnMessages getMessages() { return messages; }
    public void setMessages(ColumnMessages messages) { this.messages = messages; }

    public MinOnePerGroup getMinOnePerGroup() { return minOnePerGroup; }
    public void setMinOnePerGroup(MinOnePerGroup minOnePerGroup) { this.minOnePerGroup = minOnePerGroup; }
}

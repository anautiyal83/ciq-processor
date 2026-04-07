package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * A row-level validation rule applied to every row of a sheet.
 *
 * <p>Appears under {@code sheets.&lt;Name&gt;.rules:} in the YAML.
 * Each rule object should contain exactly one directive field.
 *
 * <p>Example:
 * <pre>
 * rules:
 *   - require: ActionKey
 *     when:
 *       column: Action
 *       operator: equals
 *       value: MODIFY
 *   - one_of: [IPv4, IPv6]
 *   - compare: "StartPort <= EndPort"
 *   - sum: [Col1, Col2]
 *     equals: Total
 * </pre>
 */
public class SheetRowRule {

    /** Column that must be non-blank (optionally conditioned by {@code when}). */
    private String require;

    /** Column that must be blank (optionally conditioned by {@code when}). */
    private String forbid;

    /**
     * Comparison expression of the form {@code "ColA op ColB"} where op is
     * one of {@code >=, <=, >, <, ==, !=}.
     */
    private String compare;

    /** At least one of these columns must be non-blank. */
    private List<String> one_of;

    /** Exactly one of these columns must be non-blank. */
    private List<String> only_one;

    /** Either all columns are non-blank or all are blank. */
    private List<String> all_or_none;

    /** Columns whose numeric values are summed. Must be used together with {@code equals}. */
    private List<String> sum;

    /** Column whose value must equal the sum of the {@code sum} columns. */
    private String equals;

    /** Condition that must hold for this rule to be applied. Null means always apply. */
    private RowCondition when;

    public String getRequire() { return require; }
    public void setRequire(String require) { this.require = require; }

    public String getForbid() { return forbid; }
    public void setForbid(String forbid) { this.forbid = forbid; }

    public String getCompare() { return compare; }
    public void setCompare(String compare) { this.compare = compare; }

    public List<String> getOne_of() { return one_of; }
    public void setOne_of(List<String> one_of) { this.one_of = one_of; }

    public List<String> getOnly_one() { return only_one; }
    public void setOnly_one(List<String> only_one) { this.only_one = only_one; }

    public List<String> getAll_or_none() { return all_or_none; }
    public void setAll_or_none(List<String> all_or_none) { this.all_or_none = all_or_none; }

    public List<String> getSum() { return sum; }
    public void setSum(List<String> sum) { this.sum = sum; }

    public String getEquals() { return equals; }
    public void setEquals(String equals) { this.equals = equals; }

    public RowCondition getWhen() { return when; }
    public void setWhen(RowCondition when) { this.when = when; }
}

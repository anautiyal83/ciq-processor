package com.nokia.ciq.validator.config;

/**
 * Defines a single post-validation output value to be computed from a sheet column
 * and stored in {@code ValidationReport.parameters} when validation passes.
 *
 * <p>YAML path: {@code outputs.<KEY>}
 *
 * <h3>Supported aggregates</h3>
 * <ul>
 *   <li>{@code distinct} — sorted, deduplicated non-blank values from {@code column},
 *       joined by {@code separator} (default {@code ,})</li>
 *   <li>{@code count} — if {@code column} is set: count of rows where that column is
 *       non-blank; if {@code column} is omitted: total row count for the sheet</li>
 *   <li>{@code sum} — numeric sum of {@code column} values (non-numeric cells ignored)</li>
 *   <li>{@code group} — groups distinct values of {@code column} by each distinct value of
 *       {@code groupBy}; produces one parameter per group value named
 *       {@code <paramName>_<groupValue>} containing the joined {@code column} values.
 *       Requires both {@code column} and {@code groupBy}.</li>
 * </ul>
 *
 * <h3>YAML example</h3>
 * <pre>
 * outputs:
 *   UNIQUE_CRGROUPS:
 *     sheet: Index
 *     column: CRGroup
 *     aggregate: distinct
 *     separator: ","
 *
 *   ANNOUNCEMENT_COUNT:
 *     sheet: ANNOUNCEMENT_FILES
 *     aggregate: count
 *
 *   TOTAL_FILE_SIZE_KB:
 *     sheet: ANNOUNCEMENT_FILES
 *     column: FILE_SIZE_KB
 *     aggregate: sum
 *
 *   # Produces NODES_BY_CR_CR001=MRF1,MRF2  NODES_BY_CR_CR002=MRF3
 *   NODES_BY_CR:
 *     sheet: Index
 *     groupBy: CRGroup
 *     column: Node
 *     aggregate: group
 *     separator: ","
 * </pre>
 */
public class OutputRule {

    /**
     * Optional override for the parameter name written to {@code ValidationReport.parameters}.
     * When absent the YAML key is used as the parameter name.
     *
     * <p>Useful when the desired parameter name contains characters that are awkward as
     * a YAML map key, or when the same logical output is reused under different names.
     *
     * <pre>
     * outputs:
     *   crgroup_output:           # YAML key (used as name when 'name' is absent)
     *     name: UNIQUE_CRGROUPS   # overrides the key → parameter is stored as UNIQUE_CRGROUPS
     *     sheet: Index
     *     column: CRGroup
     *     aggregate: distinct
     * </pre>
     */
    private String name;

    /** Sheet name to read from. Supports all sheets accessible via the store (including Index). */
    private String sheet;

    /**
     * Column name within the sheet.
     * Required for {@code distinct}, {@code sum}, and {@code group}.
     * Optional for {@code count} — when omitted, total row count is returned.
     */
    private String column;

    /** Aggregate function: {@code distinct}, {@code count}, {@code sum}, or {@code group}. */
    private String aggregate;

    /**
     * Value separator used when joining multiple values in {@code distinct} and {@code group}.
     * Defaults to {@code ","} when not specified.
     */
    private String separator;

    /**
     * Column to group by when {@code aggregate} is {@code group}.
     * Each distinct value of this column becomes a separate output parameter suffixed
     * with the group value: {@code <paramName>_<groupValue>}.
     */
    private String groupBy;

    public String getName()      { return name; }
    public void setName(String name) { this.name = name; }

    public String getSheet()     { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getColumn()    { return column; }
    public void setColumn(String column) { this.column = column; }

    public String getAggregate() { return aggregate; }
    public void setAggregate(String aggregate) { this.aggregate = aggregate; }

    public String getSeparator() { return separator; }
    public void setSeparator(String separator) { this.separator = separator; }

    public String getGroupBy()   { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }
}

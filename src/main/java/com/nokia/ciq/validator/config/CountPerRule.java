package com.nokia.ciq.validator.config;

/**
 * Validates that each distinct value of a grouping column appears exactly
 * {@code count} times in the sheet.
 *
 * <p>Example — each REGION must have exactly 2 rows in INDEX:
 * <pre>
 * workbook_rules:
 *   - count_per:
 *       sheet: INDEX
 *       group: REGION
 *       count: 2
 * </pre>
 */
public class CountPerRule {

    /** Sheet to inspect. */
    private String sheet;

    /** Column whose distinct values define the partitions (e.g. "REGION"). */
    private String group;

    /** Exact number of rows expected per distinct value of {@code group}. */
    private int count;

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}

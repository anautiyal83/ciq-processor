package com.nokia.ciq.validator.config;

import java.util.List;

/**
 * Validates that every group's collected column values in a source sheet
 * match exactly one row's column set in a target sheet, and vice versa.
 *
 * <p>The rule is fully generic — the source group can collect any number of
 * values and the target row can span any number of columns.
 *
 * <p>Example — for each REGION, the set of NODEs in INDEX must match
 * {NODE1, NODE2} in exactly one Node_Details row:
 * <pre>
 * workbook_rules:
 *   - set_match:
 *       source:
 *         sheet:  INDEX
 *         group:  REGION
 *         column: NODE
 *       target:
 *         sheet:   Node_Details
 *         columns: [NODE1, NODE2]
 * </pre>
 *
 * <p>Errors reported when:
 * <ul>
 *   <li>A source group's value set has no matching target row</li>
 *   <li>A target row's value set has no matching source group</li>
 * </ul>
 */
public class SetMatchRule {

    private Source source;
    private Target target;

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public Target getTarget() { return target; }
    public void setTarget(Target target) { this.target = target; }

    // -------------------------------------------------------------------------

    public static class Source {
        /** Sheet containing the grouped rows (e.g. "INDEX"). */
        private String sheet;
        /** Column that defines the groups (e.g. "REGION"). */
        private String group;
        /** Column whose values are collected per group to form the value set (e.g. "NODE"). */
        private String column;

        public String getSheet()  { return sheet; }
        public void setSheet(String sheet)   { this.sheet = sheet; }

        public String getGroup()  { return group; }
        public void setGroup(String group)   { this.group = group; }

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }
    }

    public static class Target {
        /** Sheet containing the rows whose column values are matched (e.g. "Node_Details"). */
        private String sheet;
        /** Columns whose combined non-blank values form the set to match against (e.g. [NODE1, NODE2]). */
        private List<String> columns;

        public String getSheet()         { return sheet; }
        public void setSheet(String sheet)        { this.sheet = sheet; }

        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> cols) { this.columns = cols; }
    }
}

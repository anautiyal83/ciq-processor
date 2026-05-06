package com.nokia.ciq.validator.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for one array field in the {@code json_output.rows} section.
 *
 * <p>Supports flat arrays, grouped arrays, and recursive nesting:
 *
 * <pre>
 * # Flat array — one element per filtered row
 * files:
 *   sheet: ANNOUNCEMENT_FILES
 *   fields:
 *     inputFile:   INPUT_FILE
 *     destination: MRF_DESTINATION_PATH
 *
 * # Grouped array — one element per distinct groupBy value
 * # with nested arrays filtered by linkOn == groupBy value
 * groups:
 *   sheet: Index
 *   groupBy: GROUP
 *   fields:
 *     groupName: GROUP
 *   nested:
 *     nodes:
 *       sheet: Index
 *       linkOn: GROUP
 *       fields:
 *         node:   NODE
 *         niamId: NIAM_NAME
 *     files:
 *       sheet: ANNOUNCEMENT_FILES
 *       linkOn: GROUP
 *       fields:
 *         inputFile:   INPUT_FILE
 *         destination: MRF_DESTINATION_PATH
 * </pre>
 */
public class RowArrayConfig {

    /** Source sheet name. */
    private String sheet;

    /**
     * Column to group rows by.  When set, the array contains one element per distinct
     * value of this column (in encounter order).  Each element's {@code fields} are
     * resolved from the rows belonging to that group, and each {@code nested} array
     * is filtered to rows whose {@code linkOn} column matches the group value.
     *
     * <p>When absent, all filtered rows produce one element each (flat array).
     */
    private String groupBy;

    /**
     * Column in this sheet whose value must match the parent group's {@code groupBy}
     * value.  Only meaningful when this entry is inside a {@code nested:} block.
     * If absent, all filtered rows of this sheet are included.
     */
    private String linkOn;

    /**
     * Scalar field mapping for each array element.
     * Key = JSON field name; value = column name in {@code sheet}.
     * For grouped arrays, the first non-blank value from the group's rows is used.
     * For flat arrays, the current row's value is used.
     */
    private Map<String, String> fields = new LinkedHashMap<>();

    /**
     * Nested array fields within each element.
     * Key = JSON array field name; value = nested {@link RowArrayConfig}.
     * Nested rows are filtered by {@code linkOn == parentGroupValue}.
     */
    private Map<String, RowArrayConfig> nested = new LinkedHashMap<>();

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getGroupBy() { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }

    public String getLinkOn() { return linkOn; }
    public void setLinkOn(String linkOn) { this.linkOn = linkOn; }

    public Map<String, String> getFields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }

    public Map<String, RowArrayConfig> getNested() { return nested; }
    public void setNested(Map<String, RowArrayConfig> nested) { this.nested = nested; }
}

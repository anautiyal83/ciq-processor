package com.nokia.ciq.validator.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root configuration for the {@code json_output:} YAML section.
 *
 * <p>Controls the structure of the single MOP JSON file written per segregation unit
 * (one file per CR / Group / Node) when validation PASSES and {@code --mop-json-dir}
 * is provided.
 *
 * <p>When this section is absent, existing default behaviour is preserved (CiqSheet files
 * + index files per the segregation mode).
 *
 * <pre>
 * json_output:
 *   fields:                                  # scalar fields → first value from SheetName.ColumnName
 *     crGroup: Index.CRGroup
 *     email:   USER_ID.EMAIL
 *
 *   rows:                                    # array fields → all filtered rows from a sheet
 *     announcementFiles:
 *       sheet: ANNOUNCEMENT_FILES
 *       fields:
 *         inputFile:   INPUT_FILE
 *         destination: MRF_DESTINATION_PATH
 * </pre>
 *
 * <p>Output JSON for CR1 / GRP1:
 * <pre>
 * {
 *   "crGroup": "CR1",
 *   "email": "engineer@nokia.com",
 *   "announcementFiles": [
 *     { "inputFile": "audio.tar", "destination": "/var/opt/clips/" }
 *   ]
 * }
 * </pre>
 */
public class JsonOutputConfig {

    /**
     * Scalar fields in the output JSON.
     * Key = JSON field name; value = {@code SheetName.ColumnName} reference.
     * The first non-blank value from the filtered rows of that sheet+column is used.
     */
    private Map<String, String> fields = new LinkedHashMap<>();

    /**
     * Array fields in the output JSON.
     * Key = JSON array field name; value = row-array configuration (sheet + field mapping).
     */
    private Map<String, RowArrayConfig> rows = new LinkedHashMap<>();

    public Map<String, String> getFields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }

    public Map<String, RowArrayConfig> getRows() { return rows; }
    public void setRows(Map<String, RowArrayConfig> rows) { this.rows = rows; }

    /** Returns {@code true} when this config has at least one field or row definition. */
    public boolean isDefined() {
        return (fields != null && !fields.isEmpty())
                || (rows != null && !rows.isEmpty());
    }
}

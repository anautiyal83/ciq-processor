package com.nokia.ciq.validator.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All column rules for one sheet.
 *
 * YAML structure:
 * <pre>
 *   sheets:
 *     CRFTargetList:
 *       columns:
 *         Node:
 *           required: true
 *         Action:
 *           required: true
 *           allowedValues: [CREATE, DELETE, MODIFY]
 * </pre>
 */
public class SheetRules {

    /** Map of column name → its validation rules. */
    private Map<String, ColumnRule> columns = new LinkedHashMap<>();

    public Map<String, ColumnRule> getColumns() { return columns; }
    public void setColumns(Map<String, ColumnRule> columns) { this.columns = columns; }
}

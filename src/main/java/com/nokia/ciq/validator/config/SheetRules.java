package com.nokia.ciq.validator.config;

import java.util.LinkedHashMap;
import java.util.List;
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
 *           type: enum
 *           values: [CREATE, DELETE, MODIFY]
 * </pre>
 */
public class SheetRules {

    /** Map of column name → its validation rules. */
    private Map<String, ColumnRule> columns = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Schema.yaml extension fields
    // -------------------------------------------------------------------------

    /** Whether this sheet is required to be present in the workbook. */
    private boolean required;

    /** Alternative sheet name(s) that are treated as equivalent to this sheet. */
    private List<String> aliases;

    /** Per-sheet overrides for reading settings (header row, trimming, etc.). */
    private WorkbookSettings settings;

    /** Row-level validation rules applied to every data row in this sheet. */
    private List<SheetRowRule> rules;

    public Map<String, ColumnRule> getColumns() { return columns; }
    public void setColumns(Map<String, ColumnRule> columns) { this.columns = columns; }

    // Schema.yaml extension getters/setters

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public List<String> getAliases() { return aliases; }
    public void setAliases(List<String> aliases) { this.aliases = aliases; }

    public WorkbookSettings getSettings() { return settings; }
    public void setSettings(WorkbookSettings settings) { this.settings = settings; }

    public List<SheetRowRule> getRules() { return rules; }
    public void setRules(List<SheetRowRule> rules) { this.rules = rules; }
}

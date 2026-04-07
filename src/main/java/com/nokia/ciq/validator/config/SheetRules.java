package com.nokia.ciq.validator.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All column rules for one sheet.
 *
 * <p>For the Node-ID sheet, set {@code nodeColumn} and {@code niamColumn} to identify
 * which columns hold the node name and NIAM / login ID respectively.
 *
 * <p>For the User-ID sheet, set {@code crGroupColumn} and {@code emailColumn} to identify
 * which columns hold the CR-group name and user e-mail respectively.
 *
 * YAML structure:
 * <pre>
 *   sheets:
 *     Node_Details:
 *       nodeColumn: Node_Name
 *       niamColumn: "NIAM NAME"
 *       columns:
 *         Node_Name:
 *           required: true
 *         "NIAM NAME":
 *           required: true
 *
 *     User_ID:
 *       crGroupColumn: CRGROUP
 *       emailColumn: EMAIL
 *       columns:
 *         CRGROUP:
 *           required: true
 *         EMAIL:
 *           required: true
 *           type: email
 *
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

    /**
     * For the Node-ID sheet: column that holds the node / hostname.
     * Default (when null): {@code "Node"}.
     */
    private String nodeColumn;

    /**
     * For the Node-ID sheet: column that holds the NIAM / login ID.
     * Default (when null): {@code "NIAM_ID"}.
     */
    private String niamColumn;

    /**
     * For the User-ID sheet: column that holds the CR-group name.
     * Default (when null): {@code "CRGROUP"}.
     */
    private String crGroupColumn;

    /**
     * For the User-ID sheet: column that holds the user e-mail address.
     * Default (when null): {@code "EMAIL"}.
     */
    private String emailColumn;

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

    public String getNodeColumn()   { return nodeColumn; }
    public void setNodeColumn(String nodeColumn) { this.nodeColumn = nodeColumn; }

    public String getNiamColumn()   { return niamColumn; }
    public void setNiamColumn(String niamColumn) { this.niamColumn = niamColumn; }

    public String getCrGroupColumn() { return crGroupColumn; }
    public void setCrGroupColumn(String crGroupColumn) { this.crGroupColumn = crGroupColumn; }

    public String getEmailColumn()  { return emailColumn; }
    public void setEmailColumn(String emailColumn) { this.emailColumn = emailColumn; }

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

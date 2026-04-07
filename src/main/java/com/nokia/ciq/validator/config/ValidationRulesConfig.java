package com.nokia.ciq.validator.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root YAML configuration for validation rules.
 *
 * File naming: {NODE_TYPE}_{ACTIVITY}_validation-rules.yaml
 * Example:     SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml
 *
 * <h3>YAML structure</h3>
 * <pre>
 * validateIndexSheets: true
 * validateNodeIds:     true
 * groupByColumnName:   NODE
 *
 * # Special sheet identity — column rules (including nodeColumn/niamColumn and
 * # crGroupColumn/emailColumn) live inside the sheet definition under sheets:
 * nodeIdSheetName:  Node_ID    # identifies which sheet is the Node-ID sheet (default: Node_ID)
 * userIdSheetName:  User_ID    # identifies which sheet is the User-ID sheet (omit if none)
 *
 * sheets:
 *   Index:                     # Index sheet — generic like any other sheet
 *     columns:
 *       Node:
 *         required: true
 *       CRGroup:
 *         required: true
 *       Tables:
 *         required: true
 *
 *   Node_ID:                   # Node-ID sheet
 *     nodeColumn: Node         # column that holds the node/hostname (default: Node)
 *     niamColumn: NIAM_ID      # column that holds the NIAM/login ID (default: NIAM_ID)
 *     columns:
 *       Node:
 *         required: true
 *       NIAM_ID:
 *         required: true
 *
 *   User_ID:                   # User-ID sheet (omit entirely if no User-ID sheet needed)
 *     crGroupColumn: CRGROUP   # column that holds the CR-group name (default: CRGROUP)
 *     emailColumn: EMAIL       # column that holds the user e-mail (default: EMAIL)
 *     columns:
 *       CRGROUP:
 *         required: true
 *       EMAIL:
 *         required: true
 *         type: email
 *
 *   CRFTargetList:
 *     columns:
 *       Node:
 *         required: true
 *         crossRef:
 *           sheet: "_index"
 *           column: node
 *       Action:
 *         required: true
 *         type: enum
 *         values: [CREATE, DELETE, MODIFY]
 * </pre>
 */
public class ValidationRulesConfig {

    /** Whether to validate that every table in the Index has a corresponding sheet. */
    private boolean validateIndexSheets = true;

    /** Whether to validate that every Node value in data sheets exists in the Node_ID sheet. */
    private boolean validateNodeIds = true;

    /**
     * Column name that drives grouping for MOP generation.
     * <ul>
     *   <li>{@code "GROUP"} — GROUP mode: INDEX sheet has GROUP|NODE columns.</li>
     *   <li>{@code "NODE"}  — node-wise mode: INDEX sheet has Node|CRGroup|Tables columns.</li>
     *   <li>{@code null}    — auto-detect from INDEX sheet column layout (default).</li>
     * </ul>
     */
    private String groupByColumnName;

    // -------------------------------------------------------------------------
    // Special sheet identity — column rules (and role-specific field names)
    // live inside the sheet definition under sheets:
    // -------------------------------------------------------------------------

    /** Name of the Node-ID sheet. Default: {@code "Node_ID"}. */
    private String nodeIdSheetName = "Node_ID";

    /**
     * Name of the User-ID sheet. {@code null} (default) means no User-ID sheet is expected
     * for this activity.
     */
    private String userIdSheetName;

    // -------------------------------------------------------------------------
    // Per-sheet column rules (all sheets — Index, Node-ID, User-ID, data sheets)
    // -------------------------------------------------------------------------

    /** Column validation rules for every sheet, keyed by sheet name. */
    private Map<String, SheetRules> sheets = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Schema.yaml extension fields
    // -------------------------------------------------------------------------

    /** Schema version string (informational only). */
    private String version;

    /** Global workbook reading settings (can be overridden per sheet). */
    private WorkbookSettings settings;

    /**
     * List of workbook-level cross-sheet validation rules.
     * YAML key: {@code workbook_rules} (mapped via property alias).
     */
    private List<WorkbookRule> workbookRules;

    /**
     * Named custom validators that can be referenced from column rules via
     * {@code validator: &lt;name&gt;}.
     */
    private Map<String, ValidatorDefinition> validators;

    // Getters and setters

    public boolean isValidateIndexSheets() { return validateIndexSheets; }
    public void setValidateIndexSheets(boolean v) { this.validateIndexSheets = v; }

    public boolean isValidateNodeIds() { return validateNodeIds; }
    public void setValidateNodeIds(boolean v) { this.validateNodeIds = v; }

    public String getGroupByColumnName() { return groupByColumnName; }
    public void setGroupByColumnName(String v) { this.groupByColumnName = v; }

    public String getNodeIdSheetName() { return nodeIdSheetName; }
    public void setNodeIdSheetName(String v) { this.nodeIdSheetName = v; }

    public String getUserIdSheetName() { return userIdSheetName; }
    public void setUserIdSheetName(String v) { this.userIdSheetName = v; }

    public Map<String, SheetRules> getSheets() { return sheets; }
    public void setSheets(Map<String, SheetRules> sheets) { this.sheets = sheets; }

    // Schema.yaml extension getters/setters

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public WorkbookSettings getSettings() { return settings; }
    public void setSettings(WorkbookSettings settings) { this.settings = settings; }

    public List<WorkbookRule> getWorkbookRules() { return workbookRules; }
    public void setWorkbookRules(List<WorkbookRule> workbookRules) { this.workbookRules = workbookRules; }

    public Map<String, ValidatorDefinition> getValidators() { return validators; }
    public void setValidators(Map<String, ValidatorDefinition> validators) { this.validators = validators; }
}

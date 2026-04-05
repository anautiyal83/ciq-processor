package com.nokia.ciq.validator.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root YAML configuration for validation rules.
 *
 * File naming: {NODE_TYPE}_{ACTIVITY}_validation-rules.yaml
 * Example:     SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml
 *
 * Full YAML structure:
 * <pre>
 * # Validate that Index sheet tables all have matching sheets in the CIQ JSON output
 * validateIndexSheets: true
 *
 * # Validate that all nodes in data sheets exist in Node_ID sheet
 * validateNodeIds: true
 *
 * # Column name that drives grouping: GROUP (group-based MOP generation) or NODE (node-wise).
 * # Omit to auto-detect from the INDEX sheet column layout.
 * groupByColumnName: NODE
 *
 * sheets:
 *   CRFTargetList:
 *     columns:
 *       Node:
 *         required: true
 *         crossRef:
 *           sheet: "_index"
 *           column: node
 *       Action:
 *         required: true
 *         allowedValues: [CREATE, DELETE, MODIFY]
 *       ActionKey:
 *         requiredWhen:
 *           column: Action
 *           value: MODIFY
 *       SubAction:
 *         requiredWhen:
 *           column: Action
 *           value: MODIFY
 *         allowedValues: [ADD, DEL, MOD]
 *       ID:
 *         required: true
 *         integer: true
 *         minValue: 1
 * </pre>
 */
public class ValidationRulesConfig {

    /** Whether to validate that every table in the Index has a corresponding JSON file. */
    private boolean validateIndexSheets = true;

    /** Whether to validate that every Node value in data sheets exists in niamMapping. */
    private boolean validateNodeIds = true;

    /**
     * Column name that drives grouping for MOP generation.
     * <ul>
     *   <li>{@code "GROUP"} — GROUP mode: INDEX sheet has GROUP|NODE columns; one MOP folder per group.</li>
     *   <li>{@code "NODE"}  — node-wise mode: INDEX sheet has Node|CRGroup|Tables columns.</li>
     *   <li>{@code null}    — auto-detect from INDEX sheet column layout (default).</li>
     * </ul>
     */
    private String groupByColumnName;

    /**
     * Optional column-level validation rules for the INDEX sheet.
     * When present, every row in the Index sheet is validated against these rules
     * using the same cell-validator chain as data sheets.
     */
    private SheetRules indexSheet;

    /**
     * Configuration for the NODE_ID sheet: sheet name, key column names, and
     * optional column-level validation rules.
     * When present, every row in the Node_ID sheet is validated against the column
     * rules using the same cell-validator chain as data sheets.
     */
    private NodeIdSheetConfig nodeIdSheet;

    /**
     * Configuration for the USER_ID sheet: sheet name, CRGROUP column name, EMAIL column name,
     * and optional column-level validation rules.
     * Maps each CRGROUP to the responsible user's email address.
     * When present, every row is validated and the EMAIL values are used for CR_EMAIL_ID_LIST.
     */
    private UserIdSheetConfig userIdSheet;

    /** Per-sheet column rules. Key = sheet name (e.g. "CRFTargetList"). */
    private Map<String, SheetRules> sheets = new LinkedHashMap<>();

    public boolean isValidateIndexSheets() { return validateIndexSheets; }
    public void setValidateIndexSheets(boolean validateIndexSheets) { this.validateIndexSheets = validateIndexSheets; }

    public boolean isValidateNodeIds() { return validateNodeIds; }
    public void setValidateNodeIds(boolean validateNodeIds) { this.validateNodeIds = validateNodeIds; }

    public String getGroupByColumnName() { return groupByColumnName; }
    public void setGroupByColumnName(String groupByColumnName) { this.groupByColumnName = groupByColumnName; }

    public SheetRules getIndexSheet() { return indexSheet; }
    public void setIndexSheet(SheetRules indexSheet) { this.indexSheet = indexSheet; }

    public NodeIdSheetConfig getNodeIdSheet() { return nodeIdSheet; }
    public void setNodeIdSheet(NodeIdSheetConfig nodeIdSheet) { this.nodeIdSheet = nodeIdSheet; }

    public UserIdSheetConfig getUserIdSheet() { return userIdSheet; }
    public void setUserIdSheet(UserIdSheetConfig userIdSheet) { this.userIdSheet = userIdSheet; }

    public Map<String, SheetRules> getSheets() { return sheets; }
    public void setSheets(Map<String, SheetRules> sheets) { this.sheets = sheets; }
}

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

    /** Per-sheet column rules. Key = sheet name (e.g. "CRFTargetList"). */
    private Map<String, SheetRules> sheets = new LinkedHashMap<>();

    public boolean isValidateIndexSheets() { return validateIndexSheets; }
    public void setValidateIndexSheets(boolean validateIndexSheets) { this.validateIndexSheets = validateIndexSheets; }

    public boolean isValidateNodeIds() { return validateNodeIds; }
    public void setValidateNodeIds(boolean validateNodeIds) { this.validateNodeIds = validateNodeIds; }

    public Map<String, SheetRules> getSheets() { return sheets; }
    public void setSheets(Map<String, SheetRules> sheets) { this.sheets = sheets; }
}

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
 * sheets:
 *   Index:
 *     columns:
 *       Node:
 *         required: true
 *       CRGroup:
 *         required: true
 *       Tables:
 *         required: true
 *         sheetRef: true
 *
 *   Node_ID:
 *     columns:
 *       Node:
 *         required: true
 *       NIAM_ID:
 *         required: true
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

    // -------------------------------------------------------------------------
    // Per-sheet column rules (all sheets — Index, Node-ID, data sheets)
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

    /** When true, validates that Index sheet entries reference existing data sheets. */
    private boolean validateIndexSheets = false;

    /** When true, validates that Node column values exist in the Node_ID sheet. */
    private boolean validateNodeIds = false;

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

    /**
     * Post-validation output extractions, keyed by output parameter name.
     * Computed only when validation passes; each entry is added to
     * {@link com.nokia.ciq.validator.model.ValidationReport#getParameters()}.
     *
     * <p>YAML key: {@code outputs}
     */
    private Map<String, OutputRule> outputs;

    /**
     * Identifies the Node-ID sheet and its key columns for NIAM mapping.
     * When absent the reader defaults to sheet {@code Node_ID}, columns {@code Node}
     * and {@code NIAM_ID}.
     *
     * <p>YAML key: {@code node_id_config}
     */
    private NodeIdConfig nodeIdConfig;

    /**
     * JSON output template for MOP segregation files.
     * The YAML subtree under {@code json_output:} is loaded as a raw map and evaluated
     * by {@link com.nokia.ciq.processor.JsonTemplateEvaluator} at segregation time.
     *
     * <p>YAML key: {@code json_output}
     */
    private Map<String, Object> jsonOutput;

    /**
     * Validation report output configuration: formats, filename template, and optional
     * HTML template path.
     *
     * <p>YAML key: {@code report_output}
     */
    private ReportOutputConfig reportOutput;

    // Getters and setters

    public Map<String, SheetRules> getSheets() { return sheets; }
    public void setSheets(Map<String, SheetRules> sheets) { this.sheets = sheets; }

    // Schema.yaml extension getters/setters

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public WorkbookSettings getSettings() { return settings; }
    public void setSettings(WorkbookSettings settings) { this.settings = settings; }

    public boolean isValidateIndexSheets() { return validateIndexSheets; }
    public void setValidateIndexSheets(boolean validateIndexSheets) { this.validateIndexSheets = validateIndexSheets; }

    public boolean isValidateNodeIds() { return validateNodeIds; }
    public void setValidateNodeIds(boolean validateNodeIds) { this.validateNodeIds = validateNodeIds; }

    public List<WorkbookRule> getWorkbookRules() { return workbookRules; }
    public void setWorkbookRules(List<WorkbookRule> workbookRules) { this.workbookRules = workbookRules; }

    public Map<String, ValidatorDefinition> getValidators() { return validators; }
    public void setValidators(Map<String, ValidatorDefinition> validators) { this.validators = validators; }

    public Map<String, OutputRule> getOutputs() { return outputs; }
    public void setOutputs(Map<String, OutputRule> outputs) { this.outputs = outputs; }

    public NodeIdConfig getNodeIdConfig()                        { return nodeIdConfig; }
    public void         setNodeIdConfig(NodeIdConfig nodeIdConfig) { this.nodeIdConfig = nodeIdConfig; }

    public Map<String, Object> getJsonOutput() { return jsonOutput; }
    public void setJsonOutput(Map<String, Object> jsonOutput) { this.jsonOutput = jsonOutput; }

    public ReportOutputConfig getReportOutput()                      { return reportOutput; }
    public void               setReportOutput(ReportOutputConfig v) { this.reportOutput = v; }
}

package com.nokia.ciq.validator.config;

/**
 * Configuration for the NODE_ID sheet — extends column validation rules
 * with the sheet name and the key column names used for NIAM mapping.
 *
 * <p>YAML example:
 * <pre>
 * nodeIdSheet:
 *   name:       Node_ID       # sheet name (default: Node_ID)
 *   nodeColumn: Node          # column that holds the node name (default: Node)
 *   niamColumn: NIAM_ID       # column that holds the NIAM / login ID (default: NIAM_ID)
 *   columns:
 *     Node:
 *       required: true
 *     NIAM_ID:
 *       required: true
 * </pre>
 */
public class NodeIdSheetConfig extends SheetRules {

    /** Sheet name. Defaults to {@code "Node_ID"} when not specified in YAML. */
    private String name = "Node_ID";

    /** Column that contains the node name. Defaults to {@code "Node"}. */
    private String nodeColumn = "Node";

    /** Column that contains the NIAM / login ID. Defaults to {@code "NIAM_ID"}. */
    private String niamColumn = "NIAM_ID";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNodeColumn() { return nodeColumn; }
    public void setNodeColumn(String nodeColumn) { this.nodeColumn = nodeColumn; }

    public String getNiamColumn() { return niamColumn; }
    public void setNiamColumn(String niamColumn) { this.niamColumn = niamColumn; }
}

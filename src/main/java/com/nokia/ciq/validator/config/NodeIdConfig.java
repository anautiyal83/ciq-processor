package com.nokia.ciq.validator.config;

/**
 * Identifies the Node-ID sheet and its two key columns.
 *
 * <p>When absent from the YAML the reader falls back to the defaults:
 * sheet {@code Node_ID}, node column {@code Node}, NIAM column {@code NIAM_ID}.
 *
 * <p>YAML key: {@code node_id_config}
 * <pre>
 * node_id_config:
 *   sheet:       Node_Details
 *   nodeColumn:  Node_Name
 *   niamColumn:  "NIAM NAME"
 * </pre>
 */
public class NodeIdConfig {

    private String sheet      = "Node_ID";
    private String nodeColumn = "Node";
    private String niamColumn = "NIAM_ID";

    public String getSheet()                  { return sheet; }
    public void   setSheet(String sheet)      { this.sheet = sheet; }

    public String getNodeColumn()                      { return nodeColumn; }
    public void   setNodeColumn(String nodeColumn)     { this.nodeColumn = nodeColumn; }

    public String getNiamColumn()                      { return niamColumn; }
    public void   setNiamColumn(String niamColumn)     { this.niamColumn = niamColumn; }
}

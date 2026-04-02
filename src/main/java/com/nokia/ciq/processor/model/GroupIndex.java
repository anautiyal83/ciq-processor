package com.nokia.ciq.processor.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON model written to each group folder during GROUP-mode CIQ processing.
 *
 * <p>GROUP mode is triggered when the CIQ INDEX sheet has {@code GROUP | NODE} columns
 * but no {@code TABLES} column.  One {@code GroupIndex} is written per group letter
 * (e.g. "A", "B") and contains the nodes that belong to that group, the NIAM
 * sub-mapping for those nodes, and the data-sheet names.
 */
public class GroupIndex {

    private String nodeType;
    private String activity;
    private String group;
    private List<String> nodes        = new ArrayList<>();
    private List<String> tables       = new ArrayList<>();
    private Map<String, String> niamMapping = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getNodeType()                       { return nodeType; }
    public void   setNodeType(String nodeType)        { this.nodeType = nodeType; }

    public String getActivity()                       { return activity; }
    public void   setActivity(String activity)        { this.activity = activity; }

    public String getGroup()                          { return group; }
    public void   setGroup(String group)              { this.group = group; }

    public List<String> getNodes()                    { return nodes; }
    public void         setNodes(List<String> nodes)  { this.nodes = nodes; }

    public List<String> getTables()                     { return tables; }
    public void         setTables(List<String> tables)  { this.tables = tables; }

    public Map<String, String> getNiamMapping()                         { return niamMapping; }
    public void                setNiamMapping(Map<String, String> m)    { this.niamMapping = m; }
}

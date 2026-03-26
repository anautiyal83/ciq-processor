package com.nokia.ciq.reader.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed contents of the Index and Node_ID sheets.
 *
 * <p>Index sheet: maps each (Node, CRGroup) to the list of tables to configure.
 * Node_ID sheet: maps each Node to its NIAM login ID.
 */
public class CiqIndex {

    private String nodeType;
    private String activity;

    /** One entry per unique (Node, CRGroup) combination. */
    private List<NodeEntry> entries;

    /** Node name → NIAM login ID (from Node_ID sheet). */
    private Map<String, String> niamMapping;

    public CiqIndex() {
        this.entries = new ArrayList<>();
        this.niamMapping = new LinkedHashMap<>();
    }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }

    public List<NodeEntry> getEntries() { return entries; }
    public void setEntries(List<NodeEntry> entries) { this.entries = entries; }

    public Map<String, String> getNiamMapping() { return niamMapping; }
    public void setNiamMapping(Map<String, String> niamMapping) { this.niamMapping = niamMapping; }

    /** All unique table names across all node/crGroup entries. */
    @JsonIgnore
    public List<String> getAllTables() {
        List<String> all = new ArrayList<>();
        for (NodeEntry entry : entries) {
            for (String table : entry.getTables()) {
                if (!all.contains(table)) {
                    all.add(table);
                }
            }
        }
        return all;
    }
}

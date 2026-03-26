package com.nokia.ciq.reader.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single entry from the Index sheet: one Node + CRGroup combination
 * with the list of tables to configure.
 */
public class NodeEntry {

    private String node;
    private String crGroup;
    private List<String> tables;

    public NodeEntry() {
        this.tables = new ArrayList<>();
    }

    public NodeEntry(String node, String crGroup, List<String> tables) {
        this.node = node;
        this.crGroup = crGroup;
        this.tables = tables;
    }

    public String getNode() { return node; }
    public void setNode(String node) { this.node = node; }

    public String getCrGroup() { return crGroup; }
    public void setCrGroup(String crGroup) { this.crGroup = crGroup; }

    public List<String> getTables() { return tables; }
    public void setTables(List<String> tables) { this.tables = tables; }
}

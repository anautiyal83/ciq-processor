package com.nokia.ciq.validator.model;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.NodeEntry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Top-level validation report for a full CIQ run.
 * Written to {NODE_TYPE}_{ACTIVITY}_validation-report.json.
 *
 * <p>When validation passes (status=PASSED) the following grouping summary
 * fields are also populated, one per MOP that will be generated downstream:
 * <ul>
 *   <li>{@code nodeNames}        — distinct node names (e.g. SBC1,SBC2,SBC3)</li>
 *   <li>{@code totalNodesCount}  — number of distinct nodes</li>
 *   <li>{@code childOrders}      — all Node-CRGroup combinations (one MOP each)</li>
 *   <li>{@code childOrdersCount} — total number of MOPs to be generated</li>
 * </ul>
 */
public class ValidationReport {

    private String nodeType;
    private String activity;
    private String status;          // PASSED | FAILED
    private int totalErrors;
    private List<String> globalErrors = new ArrayList<>();   // index-level errors
    private List<SheetValidationResult> sheets = new ArrayList<>();

    // Grouping summary — populated only when status=PASSED
    private List<String> nodeNames        = new ArrayList<>();
    private int          totalNodesCount  = 0;
    private List<String> childOrders      = new ArrayList<>();
    private int          childOrdersCount = 0;

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalErrors() { return totalErrors; }
    public void setTotalErrors(int totalErrors) { this.totalErrors = totalErrors; }

    public List<String> getGlobalErrors() { return globalErrors; }
    public void setGlobalErrors(List<String> globalErrors) { this.globalErrors = globalErrors; }

    public List<SheetValidationResult> getSheets() { return sheets; }
    public void setSheets(List<SheetValidationResult> sheets) { this.sheets = sheets; }

    public List<String> getNodeNames()        { return nodeNames; }
    public void setNodeNames(List<String> v)  { this.nodeNames = v; }

    public int getTotalNodesCount()            { return totalNodesCount; }
    public void setTotalNodesCount(int v)      { this.totalNodesCount = v; }

    public List<String> getChildOrders()       { return childOrders; }
    public void setChildOrders(List<String> v) { this.childOrders = v; }

    public int getChildOrdersCount()           { return childOrdersCount; }
    public void setChildOrdersCount(int v)     { this.childOrdersCount = v; }

    /** Recompute status and totalErrors from sheet results. */
    public void finalise() {
        int count = globalErrors.size();
        for (SheetValidationResult s : sheets) {
            count += s.getErrors().size();
            if ("PASSED".equals(s.getStatus()) && s.getErrors().isEmpty()) {
                s.setStatus("PASSED");
            }
        }
        this.totalErrors = count;
        this.status = (count == 0) ? "PASSED" : "FAILED";
    }

    /**
     * Populate grouping summary fields from the CIQ index.
     * Must be called only after {@link #finalise()} confirms status=PASSED.
     *
     * <p>NODE_NAMES        — distinct node names in index order
     * <p>TOTAL_NODES_COUNT — count of distinct nodes
     * <p>CHILD_ORDERS      — each entry as "{node}-{crGroup}" (one per MOP)
     * <p>CHILD_ORDERS_COUNT — total number of MOPs
     */
    public void populateGroupingSummary(CiqIndex index) {
        if (index == null || index.getEntries() == null) return;

        Set<String> nodes = new LinkedHashSet<>();
        List<String> orders = new ArrayList<>();

        for (NodeEntry entry : index.getEntries()) {
            String node    = entry.getNode()    != null ? entry.getNode().trim()    : "";
            String crGroup = entry.getCrGroup() != null ? entry.getCrGroup().trim() : "";

            if (!node.isEmpty()) nodes.add(node);

            String childOrder = crGroup.isEmpty() ? node : node + "_" + crGroup;
            if (!childOrder.isEmpty()) orders.add(childOrder);
        }

        this.nodeNames        = new ArrayList<>(nodes);
        this.totalNodesCount  = nodes.size();
        this.childOrders      = orders;
        this.childOrdersCount = orders.size();
    }
}

package com.nokia.ciq.validator.model;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.NodeEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-level validation report for a full CIQ run.
 * Written to {NODE_TYPE}_{ACTIVITY}_validation-report.json.
 *
 * <p>When validation passes (status=PASSED), {@code parameters} is populated with
 * all output key-value pairs:
 * <ul>
 *   <li>{@code TOTAL_NODES_COUNT}   — total number of nodes</li>
 *   <li>{@code CHILD_ORDERS_COUNT}  — total number of MOPs to be generated (= TOTAL_NODES_COUNT)</li>
 *   <li>{@code NODE_N}              — node name at position N (1-based)</li>
 *   <li>{@code NIAM_ID_N}           — NIAM ID for node N (1-based, omitted when not present)</li>
 *   <li>{@code GROUP_N}             — group name at position N (GROUP mode only)</li>
 *   <li>{@code GROUP_N_VALUES}      — comma-separated nodes in group N (GROUP mode only)</li>
 * </ul>
 */
public class ValidationReport {

    private String nodeType;
    private String activity;
    private String status;          // PASSED | FAILED
    private int totalErrors;
    private List<String> globalErrors = new ArrayList<>();   // index-level errors
    private List<SheetValidationResult> sheets = new ArrayList<>();

    /**
     * All output parameters — populated only when status=PASSED.
     * Contains NODE_N, NIAM_ID_N, TOTAL_NODES_COUNT, CHILD_ORDERS_COUNT,
     * and (in GROUP mode) GROUP_N, GROUP_N_VALUES.
     */
    private Map<String, String> parameters = new LinkedHashMap<>();

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

    public Map<String, String> getParameters()              { return parameters; }
    public void setParameters(Map<String, String> v)        { this.parameters = v; }

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
     * Populate {@code parameters} from a proper CIQ index (Node+CRGroup+Tables format).
     * Must be called only after {@link #finalise()} confirms status=PASSED.
     *
     * <p>Populates: NODE_N, NIAM_ID_N, TOTAL_NODES_COUNT, CHILD_ORDERS_COUNT.
     */
    public void populateGroupingSummary(CiqIndex index) {
        if (index == null || index.getEntries() == null) return;

        Set<String> nodes  = new LinkedHashSet<>();
        List<String> orders = new ArrayList<>();

        for (NodeEntry entry : index.getEntries()) {
            String node    = entry.getNode()    != null ? entry.getNode().trim()    : "";
            String crGroup = entry.getCrGroup() != null ? entry.getCrGroup().trim() : "";
            if (!node.isEmpty()) nodes.add(node);
            String childOrder = crGroup.isEmpty() ? node : node + "_" + crGroup;
            if (!childOrder.isEmpty()) orders.add(childOrder);
        }

        if (nodes.isEmpty()) return;   // GROUP mode or empty index — let fallback handle it

        Map<String, String> params = new LinkedHashMap<>();
        int i = 1;
        for (String node : nodes) {
            params.put("NODE_" + i, node);
            String niamId = index.getNiamMapping().get(node);
            if (niamId != null) params.put("NIAM_ID_" + i, niamId);
            i++;
        }
        params.put("TOTAL_NODES_COUNT",  String.valueOf(nodes.size()));
        params.put("CHILD_ORDERS_COUNT", String.valueOf(orders.size()));
        this.parameters = params;
    }
}

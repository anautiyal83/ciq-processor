package com.nokia.ciq.validator;

import com.nokia.ciq.processor.reader.InMemoryCiqDataStore;
import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.SheetRules;
import com.nokia.ciq.validator.config.ValidationRulesConfig;
import com.nokia.ciq.validator.model.SheetValidationResult;
import com.nokia.ciq.validator.model.ValidationError;
import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.validator.AllowedRangesValidator;
import com.nokia.ciq.validator.validator.AllowedValuesValidator;
import com.nokia.ciq.validator.validator.CellValidator;
import com.nokia.ciq.validator.validator.CrossRefValidator;
import com.nokia.ciq.validator.validator.IntegerValidator;
import com.nokia.ciq.validator.validator.LengthValidator;
import com.nokia.ciq.validator.validator.PatternValidator;
import com.nokia.ciq.validator.validator.RequiredValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Validates CIQ JSON data (via {@link CiqDataStore}) against a {@link ValidationRulesConfig}.
 *
 * <p>Validation order per sheet:
 * <ol>
 *   <li>Global checks: index sheet completeness, node ID presence</li>
 *   <li>Per-row, per-column checks delegated to the ordered {@link CellValidator} chain</li>
 * </ol>
 *
 * <p>To add a new rule type: implement {@link CellValidator} and register it in
 * {@link #buildValidatorChain(CiqDataStore)}.
 */
public class CiqValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(CiqValidationEngine.class);

    private final CiqDataStore store;
    private final ValidationRulesConfig rules;
    private final List<CellValidator> validators;

    public CiqValidationEngine(CiqDataStore store, ValidationRulesConfig rules) {
        this.store      = store;
        this.rules      = rules;
        this.validators = buildValidatorChain(store);
    }

    /**
     * Ordered chain of cell validators. Add new validators here to extend rule support.
     * {@link RequiredValidator} must remain first (it is the gatekeeper).
     */
    private static List<CellValidator> buildValidatorChain(CiqDataStore store) {
        return Arrays.asList(
                new RequiredValidator(),
                new AllowedValuesValidator(),
                new LengthValidator(),
                new IntegerValidator(),
                new AllowedRangesValidator(),
                new PatternValidator(),
                new CrossRefValidator(store)
        );
    }

    public ValidationReport validate(String nodeType, String activity) throws IOException {
        ValidationReport report = new ValidationReport();
        report.setNodeType(nodeType);
        report.setActivity(activity);

        CiqIndex index = store.getIndex();

        // --- GROUP / CRGROUP mode detection ---
        // groupByColumnName in the rules config overrides auto-detection:
        //   "NODE"    → force node-wise mode (ignore GROUP/CRGROUP columns in INDEX)
        //   "GROUP"   → honour GROUP columns (auto-detect still applies)
        //   "CRGROUP" → force CRGROUP mode; emit CRGROUP parameters
        //   null      → auto-detect
        String groupByCol = rules.getGroupByColumnName();
        boolean forceCRGroup = groupByCol != null && groupByCol.equalsIgnoreCase("CRGROUP");
        boolean forceGroup   = groupByCol != null && groupByCol.equalsIgnoreCase("GROUP");
        Map<String, Map<String, List<String>>> crGroupToGroupNodes;
        Map<String, List<String>> groupToNodes;
        if (groupByCol != null && groupByCol.equalsIgnoreCase("NODE")) {
            crGroupToGroupNodes = new LinkedHashMap<>();
            groupToNodes        = new LinkedHashMap<>();   // force node-wise
        } else {
            crGroupToGroupNodes = buildCRGroupMap();
            groupToNodes        = crGroupToGroupNodes.isEmpty() ? buildGroupToNodesMap()
                                                                : flattenCRGroupToGroupNodes(crGroupToGroupNodes);
        }
        // forceGroup suppresses CRGROUP auto-detection (mirrors CiqProcessorImpl dispatch logic)
        boolean isCRGroupMode = !forceGroup && (forceCRGroup || !crGroupToGroupNodes.isEmpty());
        boolean isGroupMode   = !groupToNodes.isEmpty();

        // --- Global checks ---
        if (rules.isValidateIndexSheets()) {
            checkIndexSheets(index, report);
        }

        // Build valid node set for node-ID validation
        Set<String> validNodes = index.getNiamMapping().keySet();

        // In GROUP mode: validate all group nodes against NODE_ID sheet (global check)
        if (isGroupMode && rules.isValidateNodeIds()) {
            for (Map.Entry<String, List<String>> ge : groupToNodes.entrySet()) {
                for (String node : ge.getValue()) {
                    if (!validNodes.isEmpty() && !validNodes.contains(node)) {
                        report.getGlobalErrors().add(
                                "Node '" + node + "' in group '" + ge.getKey() +
                                "' not found in Node_ID sheet. Valid nodes: " + validNodes);
                    }
                }
            }
            log.info("GROUP_MAP={}", formatGroupMap(groupToNodes));
        }

        // --- Determine which tables to validate ---
        // When the index has entries, validate exactly the tables listed there.
        // When the index is empty (e.g. no Index sheet), fall back to the sheets
        // defined in the rules config so that index-free CIQs are still validated.
        List<String> tableNames = index.getAllTables();
        if (tableNames.isEmpty() && rules.getSheets() != null) {
            tableNames = new ArrayList<>(rules.getSheets().keySet());
            if (!tableNames.isEmpty()) {
                log.info("Index is empty — validating {} sheet(s) from rules config: {}",
                        tableNames.size(), tableNames);
            }
        }

        // --- Per-sheet validation ---
        for (String tableName : tableNames) {
            CiqSheet sheet = store.getSheet(tableName);
            SheetValidationResult result = new SheetValidationResult();
            result.setSheetName(tableName);
            result.setStatus("PASSED");

            if (sheet == null) {
                result.setStatus("FAILED");
                result.addError(new ValidationError(0, "-", null,
                        "JSON file for sheet '" + tableName + "' not found"));
                report.getSheets().add(result);
                continue;
            }

            result.setRowsChecked(sheet.getRows().size());

            SheetRules sheetRules = rules.getSheets() != null
                    ? rules.getSheets().get(tableName) : null;

            for (CiqRow row : sheet.getRows()) {
                // Node-ID check (global, no YAML rule needed)
                if (rules.isValidateNodeIds()) {
                    String node = row.get("Node");
                    if (node != null && !validNodes.isEmpty() && !validNodes.contains(node)) {
                        result.addError(new ValidationError(row.getRowNumber(), "Node", node,
                                "Node '" + node + "' not found in Node_ID sheet. Valid nodes: " + validNodes));
                    }
                }

                // Column rules — delegated to the validator chain
                if (sheetRules != null) {
                    for (Map.Entry<String, ColumnRule> entry : sheetRules.getColumns().entrySet()) {
                        validateCell(row, entry.getKey(), entry.getValue(), index, result);
                    }
                }
            }

            log.info("Sheet '{}': {} rows, {} error(s)", tableName,
                    result.getRowsChecked(), result.getErrors().size());
            report.getSheets().add(result);
        }

        // --- INDEX and NODE_ID sheet validation (optional, driven by rules config) ---
        if (store instanceof InMemoryCiqDataStore) {
            InMemoryCiqDataStore imStore = (InMemoryCiqDataStore) store;
            if (rules.getIndexSheet() != null) {
                validateSpecialSheet(imStore.getRawIndexSheet(), "Index",
                        rules.getIndexSheet(), index, report);
            }
            if (rules.getNodeIdSheet() != null) {
                validateSpecialSheet(imStore.getRawNodeIdSheet(), "Node_ID",
                        rules.getNodeIdSheet(), index, report);
            }
        }

        report.finalise();
        if ("PASSED".equals(report.getStatus())) {
            // populateGroupingSummary handles proper INDEX mode (Node+CRGroup+Tables).
            // For GROUP mode and no-Index mode the parameters map will be empty after this call
            // and the fallback block below takes over.
            report.populateGroupingSummary(index);

            if (report.getParameters().isEmpty()) {
                Map<String, String> params    = new LinkedHashMap<>();
                Map<String, String> niamMap   = index.getNiamMapping();

                if (isCRGroupMode) {
                    // CRGROUP mode: emit CRGROUP_N, GROUP_N, and NODE_N parameters
                    log.info("CRGROUP_MAP={}", formatCRGroupMap(crGroupToGroupNodes));

                    // CRGROUP-level parameters
                    int ci = 1;
                    for (Map.Entry<String, Map<String, List<String>>> cre : crGroupToGroupNodes.entrySet()) {
                        String crgName = cre.getKey();
                        List<String> crGroups = new ArrayList<>(cre.getValue().keySet());
                        List<String> crNodes  = new ArrayList<>();
                        for (List<String> ns : cre.getValue().values()) crNodes.addAll(ns);

                        params.put("CRGROUP_" + ci,              crgName);
                        params.put("CRGROUP_" + ci + "_GROUPS",  String.join(",", crGroups));
                        params.put("CRGROUP_" + ci + "_NODES",   String.join(",", crNodes));
                        params.put("CRGROUP_" + ci + "_NODES_COUNT", String.valueOf(crNodes.size()));
                        ci++;
                    }
                    params.put("TOTAL_CRGROUPS_COUNT", String.valueOf(crGroupToGroupNodes.size()));

                    // GROUP-level parameters (across all CRGROUPs — unique groups)
                    int gi = 1;
                    for (Map.Entry<String, List<String>> ge : groupToNodes.entrySet()) {
                        params.put("GROUP_" + gi,             ge.getKey());
                        params.put("GROUP_" + gi + "_VALUES", String.join(",", ge.getValue()));
                        gi++;
                    }
                    params.put("TOTAL_GROUPS_COUNT", String.valueOf(groupToNodes.size()));

                    // NODE-level parameters (all nodes across all groups)
                    List<String> allNodes = new ArrayList<>();
                    for (List<String> ns : groupToNodes.values()) allNodes.addAll(ns);
                    for (int i = 0; i < allNodes.size(); i++) {
                        String node   = allNodes.get(i);
                        String niamId = niamMap.get(node);
                        params.put("NODE_" + (i + 1), node);
                        if (niamId != null) params.put("NIAM_ID_" + (i + 1), niamId);
                    }
                    params.put("TOTAL_NODES_COUNT",  String.valueOf(allNodes.size()));
                    params.put("CHILD_ORDERS_COUNT", String.valueOf(crGroupToGroupNodes.size()));

                } else if (isGroupMode) {
                    // GROUP mode (no CRGROUP): one entry per node across all groups
                    log.info("GROUP_MAP={}", formatGroupMap(groupToNodes));
                    List<String> allNodes = new ArrayList<>();
                    for (List<String> ns : groupToNodes.values()) allNodes.addAll(ns);

                    for (int i = 0; i < allNodes.size(); i++) {
                        String node   = allNodes.get(i);
                        String niamId = niamMap.get(node);
                        params.put("NODE_" + (i + 1), node);
                        if (niamId != null) params.put("NIAM_ID_" + (i + 1), niamId);
                    }
                    params.put("TOTAL_NODES_COUNT",  String.valueOf(allNodes.size()));
                    params.put("CHILD_ORDERS_COUNT", String.valueOf(allNodes.size()));

                    int gi = 1;
                    for (Map.Entry<String, List<String>> ge : groupToNodes.entrySet()) {
                        params.put("GROUP_" + gi,            ge.getKey());
                        params.put("GROUP_" + gi + "_VALUES", String.join(",", ge.getValue()));
                        gi++;
                    }
                    params.put("TOTAL_GROUPS_COUNT", String.valueOf(groupToNodes.size()));
                } else {
                    // No-Index NODE mode: derive nodes from INDEX data sheet or NODE_ID sheet
                    Map<String, String> nodeToCrGroup = new LinkedHashMap<>();
                    for (String sheetName : store.getAvailableSheets()) {
                        if (sheetName.replace("_", "").equalsIgnoreCase("index")) {
                            com.nokia.ciq.reader.model.CiqSheet idxSheet = store.getSheet(sheetName);
                            if (idxSheet != null) {
                                for (com.nokia.ciq.reader.model.CiqRow r : idxSheet.getRows()) {
                                    String n  = r.get("Node");
                                    String cg = r.get("CRGroup");
                                    if (n != null && !n.trim().isEmpty()) {
                                        nodeToCrGroup.put(n.trim(), cg != null ? cg.trim() : "");
                                    }
                                }
                            }
                            break;
                        }
                    }

                    Set<String> nodes = !nodeToCrGroup.isEmpty()
                            ? new java.util.LinkedHashSet<>(nodeToCrGroup.keySet())
                            : new java.util.LinkedHashSet<>(niamMap.keySet());

                    int ni = 1;
                    int childCount = 0;
                    for (String node : nodes) {
                        String niamId = niamMap.get(node);
                        params.put("NODE_" + ni, node);
                        if (niamId != null) params.put("NIAM_ID_" + ni, niamId);
                        ni++;
                        childCount++;
                    }
                    params.put("TOTAL_NODES_COUNT",  String.valueOf(nodes.size()));
                    params.put("CHILD_ORDERS_COUNT", String.valueOf(childCount));
                }
                report.setParameters(params);
            }

            // Log every parameter
            for (Map.Entry<String, String> e : report.getParameters().entrySet()) {
                log.info("{}={}", e.getKey(), e.getValue());
            }
        }
        log.info("Validation complete: {} — {} error(s)", report.getStatus(), report.getTotalErrors());
        return report;
    }

    // -------------------------------------------------------------------------
    // Special-sheet validation (Index / Node_ID)
    // -------------------------------------------------------------------------

    /**
     * Validates the rows of a special sheet (Index or Node_ID) against the given
     * {@link SheetRules} using the standard cell-validator chain, and appends a
     * {@link SheetValidationResult} to the report.
     *
     * @param sheet      the raw sheet captured by {@link com.nokia.ciq.processor.reader.InMemoryExcelReader};
     *                   if {@code null} the sheet is reported as missing
     * @param sheetLabel display name used in error messages and the result (e.g. {@code "Index"})
     */
    private void validateSpecialSheet(CiqSheet sheet, String sheetLabel,
                                      SheetRules sheetRules, CiqIndex index,
                                      ValidationReport report) {
        SheetValidationResult result = new SheetValidationResult();
        result.setSheetName(sheetLabel);
        result.setStatus("PASSED");

        if (sheet == null) {
            result.setStatus("FAILED");
            result.addError(new ValidationError(0, "-", null,
                    "Sheet '" + sheetLabel + "' not found in workbook"));
            report.getSheets().add(result);
            log.warn("Sheet '{}' not found — cannot validate", sheetLabel);
            return;
        }

        result.setRowsChecked(sheet.getRows().size());

        for (CiqRow row : sheet.getRows()) {
            for (Map.Entry<String, ColumnRule> entry : sheetRules.getColumns().entrySet()) {
                validateCell(row, entry.getKey(), entry.getValue(), index, result);
            }
        }

        log.info("Sheet '{}' (special): {} rows, {} error(s)", sheetLabel,
                result.getRowsChecked(), result.getErrors().size());
        report.getSheets().add(result);
    }

    // -------------------------------------------------------------------------
    // Per-cell validation — delegates to the ordered validator chain
    // -------------------------------------------------------------------------

    private void validateCell(CiqRow row, String colName, ColumnRule rule,
                               CiqIndex index, SheetValidationResult result) {
        String value = row.get(colName);
        for (CellValidator validator : validators) {
            List<ValidationError> errors = validator.validate(row, colName, value, rule, index);
            for (ValidationError e : errors) result.addError(e);
            if (!errors.isEmpty() && validator.isGatekeeper()) return;
        }
    }

    // -------------------------------------------------------------------------
    // Global checks
    // -------------------------------------------------------------------------

    /**
     * Reads the INDEX sheet from the store and builds a GROUP→Nodes map.
     * Returns a non-empty map only when the INDEX sheet has {@code Group | Node} columns
     * (GROUP mode).  Returns an empty map for all other modes.
     */
    private Map<String, List<String>> buildGroupToNodesMap() throws IOException {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String sheetName : store.getAvailableSheets()) {
            if (!sheetName.replace("_", "").equalsIgnoreCase("index")) continue;
            com.nokia.ciq.reader.model.CiqSheet idxSheet = store.getSheet(sheetName);
            if (idxSheet == null) break;

            // Require "Group" column and "Node" column; must NOT have "CRGroup"
            boolean hasGroup   = false;
            boolean hasNode    = false;
            boolean hasCrGroup = false;
            for (String col : idxSheet.getColumns()) {
                String norm = col.replace("_", "").toLowerCase();
                if (norm.equals("group"))   hasGroup   = true;
                if (norm.equals("node"))    hasNode    = true;
                if (norm.equals("crgroup")) hasCrGroup = true;
            }
            if (!hasGroup || !hasNode || hasCrGroup) break;

            for (com.nokia.ciq.reader.model.CiqRow r : idxSheet.getRows()) {
                String g = r.get("Group");
                String n = r.get("Node");
                if (g != null && !g.trim().isEmpty() && n != null && !n.trim().isEmpty()) {
                    map.computeIfAbsent(g.trim(), k -> new ArrayList<>()).add(n.trim());
                }
            }
            break;
        }
        return map;
    }

    /**
     * Reads the INDEX sheet when it has {@code GROUP | CRGROUP | NODE} columns and
     * builds the CRGROUP → (GROUP → nodes) map.  Returns empty if the sheet is absent
     * or lacks the CRGROUP column.
     */
    private Map<String, Map<String, List<String>>> buildCRGroupMap() {
        Map<String, Map<String, List<String>>> map = new LinkedHashMap<>();
        if (!(store instanceof InMemoryCiqDataStore)) return map;
        InMemoryCiqDataStore imStore = (InMemoryCiqDataStore) store;
        com.nokia.ciq.reader.model.CiqSheet rawIndex = imStore.getRawIndexSheet();
        if (rawIndex == null) return map;

        boolean hasGroup = false, hasNode = false, hasCrGroup = false;
        for (String col : rawIndex.getColumns()) {
            String norm = col.replace("_", "").toLowerCase();
            if (norm.equals("group"))   hasGroup   = true;
            if (norm.equals("node"))    hasNode    = true;
            if (norm.equals("crgroup")) hasCrGroup = true;
        }
        if (!hasGroup || !hasNode || !hasCrGroup) return map;

        for (com.nokia.ciq.reader.model.CiqRow r : rawIndex.getRows()) {
            String group   = r.get("Group");
            String node    = r.get("Node");
            String crGroup = r.get("CRGroup");
            if (isBlank(group) || isBlank(node) || isBlank(crGroup)) continue;
            map.computeIfAbsent(crGroup.trim(), k -> new LinkedHashMap<>())
               .computeIfAbsent(group.trim(),   k -> new ArrayList<>())
               .add(node.trim());
        }
        return map;
    }

    /** Flattens CRGROUP → (GROUP → nodes) into GROUP → nodes (unique groups only). */
    private static Map<String, List<String>> flattenCRGroupToGroupNodes(
            Map<String, Map<String, List<String>>> crGroupToGroupNodes) {
        Map<String, List<String>> flat = new LinkedHashMap<>();
        for (Map<String, List<String>> groupMap : crGroupToGroupNodes.values()) {
            for (Map.Entry<String, List<String>> ge : groupMap.entrySet()) {
                flat.computeIfAbsent(ge.getKey(), k -> new ArrayList<>());
                for (String node : ge.getValue()) {
                    if (!flat.get(ge.getKey()).contains(node)) {
                        flat.get(ge.getKey()).add(node);
                    }
                }
            }
        }
        return flat;
    }

    private static String formatGroupMap(Map<String, List<String>> groupToNodes) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : groupToNodes.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        return sb.toString();
    }

    private static String formatCRGroupMap(Map<String, Map<String, List<String>>> crGroupToGroupNodes) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, List<String>>> ce : crGroupToGroupNodes.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(ce.getKey()).append("=").append(formatGroupMap(ce.getValue()));
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void checkIndexSheets(CiqIndex index, ValidationReport report) throws IOException {
        List<String> indexTables     = index.getAllTables();
        List<String> availableSheets = store.getAvailableSheets();

        for (String table : indexTables) {
            if (!availableSheets.contains(table)) {
                report.getGlobalErrors().add(
                        "Table '" + table + "' listed in Index but no JSON file found");
            }
        }

        for (String sheet : availableSheets) {
            if (!indexTables.contains(sheet)) {
                report.getGlobalErrors().add(
                        "JSON file found for sheet '" + sheet + "' but it is not listed in the Index");
            }
        }

        if (indexTables.size() != availableSheets.size()) {
            report.getGlobalErrors().add(
                    "Sheet count mismatch: Index lists " + indexTables.size() +
                    " table(s) but " + availableSheets.size() + " JSON file(s) found");
        }
    }
}

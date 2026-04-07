package com.nokia.ciq.validator;

import com.nokia.ciq.processor.reader.InMemoryCiqDataStore;
import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.config.SheetRules;
import com.nokia.ciq.validator.config.ValidatorDefinition;
import com.nokia.ciq.validator.config.ValidationRulesConfig;
import com.nokia.ciq.validator.config.WorkbookRule;
import com.nokia.ciq.validator.model.SheetValidationResult;
import com.nokia.ciq.validator.model.ValidationError;
import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.validator.AllowedRangesValidator;
import com.nokia.ciq.validator.validator.AllowedValuesValidator;
import com.nokia.ciq.validator.validator.BooleanValidator;
import com.nokia.ciq.validator.validator.CellValidator;
import com.nokia.ciq.validator.validator.CidrValidator;
import com.nokia.ciq.validator.validator.CompareColumnsValidator;
import com.nokia.ciq.validator.validator.ConditionalRowRuleValidator;
import com.nokia.ciq.validator.validator.CrossRefValidator;
import com.nokia.ciq.validator.validator.DateTimeValidator;
import com.nokia.ciq.validator.validator.DateValidator;
import com.nokia.ciq.validator.validator.DecimalValidator;
import com.nokia.ciq.validator.validator.EmailValidator;
import com.nokia.ciq.validator.validator.FqdnValidator;
import com.nokia.ciq.validator.validator.GroupPresenceValidator;
import com.nokia.ciq.validator.validator.HostnameValidator;
import com.nokia.ciq.validator.validator.IntegerValidator;
import com.nokia.ciq.validator.validator.IpAddressValidator;
import com.nokia.ciq.validator.validator.LengthValidator;
import com.nokia.ciq.validator.validator.MacAddressValidator;
import com.nokia.ciq.validator.validator.PatternValidator;
import com.nokia.ciq.validator.validator.PhoneValidator;
import com.nokia.ciq.validator.validator.RequiredValidator;
import com.nokia.ciq.validator.validator.SumEqualsValidator;
import com.nokia.ciq.validator.validator.TimeValidator;
import com.nokia.ciq.validator.validator.UrlSchemeValidator;
import com.nokia.ciq.validator.validator.WorkbookCrossRefValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

    // Schema.yaml framework: custom validators and row/workbook rule validators
    private final Map<String, CellValidator> customValidators;
    private final ConditionalRowRuleValidator conditionalRowRuleValidator = new ConditionalRowRuleValidator();
    private final CompareColumnsValidator compareColumnsValidator = new CompareColumnsValidator();
    private final GroupPresenceValidator groupPresenceValidator = new GroupPresenceValidator();
    private final SumEqualsValidator sumEqualsValidator = new SumEqualsValidator();
    private final WorkbookCrossRefValidator workbookCrossRefValidator = new WorkbookCrossRefValidator();

    public CiqValidationEngine(CiqDataStore store, ValidationRulesConfig rules) {
        this.store            = store;
        this.rules            = rules;
        this.validators       = buildValidatorChain(store);
        this.customValidators = loadCustomValidators(rules);
    }

    /**
     * Loads custom {@link CellValidator} instances declared under {@code validators:} in
     * the rules YAML.  Validators that cannot be instantiated are logged and skipped.
     */
    private Map<String, CellValidator> loadCustomValidators(ValidationRulesConfig rules) {
        Map<String, CellValidator> map = new LinkedHashMap<>();
        if (rules.getValidators() == null) return map;
        for (Map.Entry<String, ValidatorDefinition> e : rules.getValidators().entrySet()) {
            try {
                Class<?> cls = Class.forName(e.getValue().getClazz());
                CellValidator v = (CellValidator) cls.getDeclaredConstructor().newInstance();
                map.put(e.getKey(), v);
                log.info("Loaded custom validator '{}' → {}", e.getKey(), e.getValue().getClazz());
            } catch (Exception ex) {
                log.error("Failed to load custom validator '{}': {}", e.getKey(), ex.getMessage());
            }
        }
        return map;
    }

    /**
     * Ordered chain of cell validators. Add new validators here to extend rule support.
     *
     * <p>Ordering rules:
     * <ol>
     *   <li>{@link RequiredValidator} — gatekeeper; must remain first</li>
     *   <li>Vocabulary validators: {@link AllowedValuesValidator} (enum / protocol / allowedValues)</li>
     *   <li>String constraints: {@link LengthValidator}</li>
     *   <li>Numeric type validators: {@link IntegerValidator}, {@link AllowedRangesValidator},
     *       {@link DecimalValidator}</li>
     *   <li>Temporal validators: {@link DateValidator}, {@link TimeValidator},
     *       {@link DateTimeValidator}</li>
     *   <li>Value-set validators: {@link BooleanValidator}</li>
     *   <li>Network type validators: {@link IpAddressValidator}, {@link MacAddressValidator},
     *       {@link CidrValidator}, {@link HostnameValidator}, {@link FqdnValidator}</li>
     *   <li>Contact / URL validators: {@link EmailValidator}, {@link PhoneValidator},
     *       {@link UrlSchemeValidator}</li>
     *   <li>Pattern override — {@link PatternValidator} runs after type validators so it can
     *       override built-in format checks for mac/cidr/hostname/fqdn/phone</li>
     *   <li>{@link CrossRefValidator} — last, as it requires an external lookup</li>
     * </ol>
     */
    private static List<CellValidator> buildValidatorChain(CiqDataStore store) {
        return Arrays.asList(
                // 1. Gatekeeper
                new RequiredValidator(),
                // 2. Vocabulary
                new AllowedValuesValidator(),
                // 3. String constraints
                new LengthValidator(),
                // 4. Numeric types
                new IntegerValidator(),
                new AllowedRangesValidator(),
                new DecimalValidator(),
                // 5. Temporal types
                new DateValidator(),
                new TimeValidator(),
                new DateTimeValidator(),
                // 6. Boolean
                new BooleanValidator(),
                // 7. Network types
                new IpAddressValidator(),
                new MacAddressValidator(),
                new CidrValidator(),
                new HostnameValidator(),
                new FqdnValidator(),
                // 8. Contact / URL
                new EmailValidator(),
                new PhoneValidator(),
                new UrlSchemeValidator(),
                // 9. Pattern override (after type validators — overrides built-in format checks)
                new PatternValidator(),
                // 10. Cross-sheet reference
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

        // Build valid node set for node-ID validation (case-insensitive lookup)
        Set<String> validNodes = index.getNiamMapping().keySet();
        Set<String> validNodesLower = new java.util.HashSet<>();
        for (String n : validNodes) validNodesLower.add(n.toLowerCase());

        // In GROUP mode: validate all group nodes against NODE_ID sheet (global check)
        if (isGroupMode && rules.isValidateNodeIds()) {
            for (Map.Entry<String, List<String>> ge : groupToNodes.entrySet()) {
                for (String node : ge.getValue()) {
                    if (!validNodes.isEmpty() && !validNodesLower.contains(node.toLowerCase())) {
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
            // Exclude special sheets — they are validated separately below
            Set<String> specialNames = specialSheetNames(rules);
            tableNames = new ArrayList<>();
            for (String name : rules.getSheets().keySet()) {
                if (!specialNames.contains(name)) tableNames.add(name);
            }
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

            SheetRules sheetRules = rules.getSheets() != null
                    ? rules.getSheets().get(tableName) : null;

            // Sheet-presence check with alias fallback
            if (sheet == null && sheetRules != null && sheetRules.getAliases() != null) {
                for (String alias : sheetRules.getAliases()) {
                    CiqSheet aliasSheet = store.getSheet(alias);
                    if (aliasSheet != null) {
                        sheet = aliasSheet;
                        log.info("Sheet '{}' resolved via alias '{}'", tableName, alias);
                        break;
                    }
                }
            }

            if (sheet == null) {
                result.setStatus("FAILED");
                result.addError(new ValidationError(0, "-", null,
                        "JSON file for sheet '" + tableName + "' not found"));
                report.getSheets().add(result);
                continue;
            }

            result.setRowsChecked(sheet.getRows().size());

            // All-rows-blank check: fail if every data row has all-null values
            if (!sheet.getRows().isEmpty()
                    && sheet.getRows().stream().allMatch(r ->
                            r.getData().values().stream().allMatch(v -> v == null))) {
                result.setStatus("FAILED");
                result.addError(new ValidationError(0, "-", null,
                        "Sheet '" + tableName + "' contains only blank rows — no valid data found"));
                report.getSheets().add(result);
                log.warn("Sheet '{}': all rows are blank", tableName);
                continue;
            }

            // Uniqueness pre-pass: collect duplicate errors for columns marked unique: true
            if (sheetRules != null && sheetRules.getColumns() != null) {
                for (Map.Entry<String, ColumnRule> entry : sheetRules.getColumns().entrySet()) {
                    if (entry.getValue().isUnique()) {
                        String colName = entry.getKey();
                        Map<String, Integer> seen = new LinkedHashMap<>();
                        for (CiqRow row : sheet.getRows()) {
                            String val = row.get(colName);
                            if (val == null || val.trim().isEmpty()) continue;
                            if (seen.containsKey(val)) {
                                result.addError(new ValidationError(row.getRowNumber(), colName, val,
                                        "Duplicate value '" + val + "' in column '" + colName
                                        + "' (first seen at row " + seen.get(val) + ")"));
                            } else {
                                seen.put(val, row.getRowNumber());
                            }
                        }
                    }
                }
            }

            // Check that every configured column is actually present in the sheet header.
            // Report one error per missing column; exclude it from per-row validation.
            Set<String> missingColumns = checkMissingColumns(sheet, sheetRules, result);

            for (CiqRow row : sheet.getRows()) {
                // Node-ID check (global, no YAML rule needed)
                if (rules.isValidateNodeIds()) {
                    String node = row.get("Node");
                    if (node != null && !validNodes.isEmpty()
                            && !validNodesLower.contains(node.toLowerCase())) {
                        result.addError(new ValidationError(row.getRowNumber(), "Node", node,
                                "Node '" + node + "' not found in Node_ID sheet. Valid nodes: " + validNodes));
                    }
                }

                // Column rules — delegated to the validator chain; skip missing columns
                if (sheetRules != null) {
                    for (Map.Entry<String, ColumnRule> entry : sheetRules.getColumns().entrySet()) {
                        if (missingColumns.contains(entry.getKey())) continue;
                        validateCell(row, entry.getKey(), entry.getValue(), index, result);
                    }
                }
            }

            // Apply per-sheet row rules (require/forbid/compare/one_of/etc.)
            if (sheetRules != null && sheetRules.getRules() != null) {
                for (CiqRow row : sheet.getRows()) {
                    for (SheetRowRule rowRule : sheetRules.getRules()) {
                        List<ValidationError> errors = applyRowRule(row, rowRule);
                        for (ValidationError e : errors) result.addError(e);
                    }
                }
            }

            log.info("Sheet '{}': {} rows, {} error(s)", tableName,
                    result.getRowsChecked(), result.getErrors().size());
            report.getSheets().add(result);
        }

        // --- Special-sheet validation (Index, Node-ID, User-ID) ---
        // Column rules come from the generic sheets: map — no special config objects needed.
        if (store instanceof InMemoryCiqDataStore) {
            InMemoryCiqDataStore imStore = (InMemoryCiqDataStore) store;

            SheetRules indexRules = sheetRulesFor(rules, "Index");
            if (indexRules != null) {
                validateSpecialSheet(imStore.getRawIndexSheet(), "Index",
                        indexRules, index, report);
            }
            String nodeIdName  = rules.getNodeIdSheetName();
            SheetRules nodeIdRules = sheetRulesFor(rules, nodeIdName);
            if (nodeIdRules != null) {
                validateSpecialSheet(imStore.getRawNodeIdSheet(), nodeIdName,
                        nodeIdRules, index, report);
            }
            String userIdName  = rules.getUserIdSheetName();
            if (userIdName != null) {
                SheetRules userIdRules = sheetRulesFor(rules, userIdName);
                if (userIdRules != null) {
                    validateSpecialSheet(imStore.getRawUserIdSheet(), userIdName,
                            userIdRules, index, report);
                }
            }
        }

        // --- Workbook-level cross-sheet rules ---
        if (rules.getWorkbookRules() != null) {
            for (WorkbookRule wbRule : rules.getWorkbookRules()) {
                List<ValidationError> errors = workbookCrossRefValidator.validate(wbRule, store);
                for (ValidationError e : errors) {
                    report.getGlobalErrors().add("[workbook_rule] " + e.getMessage());
                }
            }
        }

        report.finalise();
        if ("PASSED".equals(report.getStatus())) {
            if (isCRGroupMode) {
                // CRGROUP mode: CR_LIST and CR_COUNT only — email is embedded per CR in the JSON
                log.info("CRGROUP_MAP={}", formatCRGroupMap(crGroupToGroupNodes));
                Map<String, String> params = new LinkedHashMap<>();
                List<String> crList = new ArrayList<>(crGroupToGroupNodes.keySet());
                params.put("CR_LIST",  String.join(",", crList));
                params.put("CR_COUNT", String.valueOf(crList.size()));
                report.setParameters(params);
            } else {
                // NODE / GROUP mode: populate from index entries
                report.populateGroupingSummary(index);
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

        // All-rows-blank check
        if (!sheet.getRows().isEmpty()
                && sheet.getRows().stream().allMatch(r ->
                        r.getData().values().stream().allMatch(v -> v == null))) {
            result.setStatus("FAILED");
            result.addError(new ValidationError(0, "-", null,
                    "Sheet '" + sheetLabel + "' contains only blank rows — no valid data found"));
            report.getSheets().add(result);
            log.warn("Sheet '{}': all rows are blank", sheetLabel);
            return;
        }

        Set<String> missingColumns = checkMissingColumns(sheet, sheetRules, result);

        for (CiqRow row : sheet.getRows()) {
            for (Map.Entry<String, ColumnRule> entry : sheetRules.getColumns().entrySet()) {
                if (missingColumns.contains(entry.getKey())) continue;
                validateCell(row, entry.getKey(), entry.getValue(), index, result);
            }
        }

        log.info("Sheet '{}' (special): {} rows, {} error(s)", sheetLabel,
                result.getRowsChecked(), result.getErrors().size());
        report.getSheets().add(result);
    }

    // -------------------------------------------------------------------------
    // Column-presence check
    // -------------------------------------------------------------------------

    /**
     * Compares the configured columns in {@code sheetRules} against the columns actually
     * present in {@code sheet} (as read from the Excel header). For each configured column
     * that is absent from the sheet, a single {@link ValidationError} at row 0 is added to
     * {@code result}.
     *
     * @return the set of column names that are missing from the sheet header (may be empty)
     */
    private static Set<String> checkMissingColumns(CiqSheet sheet, SheetRules sheetRules,
                                                   SheetValidationResult result) {
        Set<String> missing = new java.util.LinkedHashSet<>();
        if (sheetRules == null || sheetRules.getColumns() == null) return missing;

        List<String> presentColumns = sheet.getColumns() != null ? sheet.getColumns()
                                                                  : java.util.Collections.emptyList();
        // Normalize for case-insensitive + underscore-insensitive matching
        Set<String> presentNorm = new java.util.HashSet<>();
        for (String c : presentColumns) presentNorm.add(c.replace("_", "").toLowerCase());

        for (String configuredCol : sheetRules.getColumns().keySet()) {
            if (!presentNorm.contains(configuredCol.replace("_", "").toLowerCase())) {
                missing.add(configuredCol);
                result.setStatus("FAILED");
                result.addError(new ValidationError(0, configuredCol, null,
                        "Column '" + configuredCol + "' is not present in sheet '"
                        + sheet.getSheetName() + "'"));
                log.warn("Sheet '{}': configured column '{}' not found in header",
                        sheet.getSheetName(), configuredCol);
            }
        }
        return missing;
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
        // Run custom validator if specified in the column rule
        if (rule.getValidator() != null) {
            CellValidator custom = customValidators.get(rule.getValidator());
            if (custom != null) {
                List<ValidationError> errors = custom.validate(row, colName, value, rule, index);
                for (ValidationError e : errors) result.addError(e);
            } else {
                log.warn("Custom validator '{}' referenced in column '{}' but not registered",
                        rule.getValidator(), colName);
            }
        }
    }

    /**
     * Dispatches a {@link SheetRowRule} to the appropriate row-level validator.
     */
    private List<ValidationError> applyRowRule(CiqRow row, SheetRowRule rule) {
        // require / forbid (with optional when condition)
        if (rule.getRequire() != null || rule.getForbid() != null) {
            return conditionalRowRuleValidator.validate(row, rule);
        }
        // compare: ColA op ColB
        if (rule.getCompare() != null) {
            return compareColumnsValidator.validate(row, rule);
        }
        // one_of / only_one / all_or_none
        if (rule.getOne_of() != null || rule.getOnly_one() != null || rule.getAll_or_none() != null) {
            return groupPresenceValidator.validate(row, rule);
        }
        // sum: [cols] equals: col
        if (rule.getSum() != null && rule.getEquals() != null) {
            return sumEqualsValidator.validate(row, rule);
        }
        return java.util.Collections.emptyList();
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

    /** Returns column rules for a named sheet, or {@code null} when not configured. */
    private static SheetRules sheetRulesFor(ValidationRulesConfig rules, String sheetName) {
        if (rules.getSheets() == null || sheetName == null) return null;
        return rules.getSheets().get(sheetName);
    }

    /**
     * Returns the set of sheet names that are treated as special (Index, Node-ID, User-ID).
     * These are validated separately and must be excluded from the generic per-sheet loop.
     */
    private static Set<String> specialSheetNames(ValidationRulesConfig rules) {
        Set<String> names = new java.util.HashSet<>();
        names.add("Index");
        if (rules.getNodeIdSheetName() != null)  names.add(rules.getNodeIdSheetName());
        if (rules.getUserIdSheetName() != null)  names.add(rules.getUserIdSheetName());
        return names;
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

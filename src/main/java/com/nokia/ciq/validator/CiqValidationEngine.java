package com.nokia.ciq.validator;

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

        // --- Global checks ---
        if (rules.isValidateIndexSheets()) {
            checkIndexSheets(index, report);
        }

        // Build valid node set for node-ID validation
        Set<String> validNodes = index.getNiamMapping().keySet();

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

        report.finalise();
        if ("PASSED".equals(report.getStatus())) {
            report.populateGroupingSummary(index);
            log.info("NODE_NAMES={}", String.join(",", report.getNodeNames()));
            log.info("TOTAL_NODES_COUNT={}", report.getTotalNodesCount());
            log.info("CHILD_ORDERS={}", String.join(",", report.getChildOrders()));
            log.info("CHILD_ORDERS_COUNT={}", report.getChildOrdersCount());
        }
        log.info("Validation complete: {} — {} error(s)", report.getStatus(), report.getTotalErrors());
        return report;
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

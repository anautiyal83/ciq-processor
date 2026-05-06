package com.nokia.ciq.validator;

import com.nokia.ciq.processor.reader.InMemoryCiqDataStore;
import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.MinOnePerGroup;
import com.nokia.ciq.validator.config.OutputRule;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.config.SheetRules;
import com.nokia.ciq.validator.config.ValidatorDefinition;
import com.nokia.ciq.validator.config.ValidationRulesConfig;
import com.nokia.ciq.validator.config.WorkbookRule;
import com.nokia.ciq.validator.model.SheetValidationResult;
import com.nokia.ciq.validator.model.ValidationError;
import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.validator.CellValidator;
import com.nokia.ciq.validator.validator.CompareColumnsValidator;
import com.nokia.ciq.validator.validator.ConditionalPatternValidator;
import com.nokia.ciq.validator.validator.ConditionalRowRuleValidator;
import com.nokia.ciq.validator.validator.CrossRefValidator;
import com.nokia.ciq.validator.validator.DecimalValidator;
import com.nokia.ciq.validator.validator.EmailValidator;
import com.nokia.ciq.validator.validator.GroupPresenceValidator;
import com.nokia.ciq.validator.validator.HostnameValidator;
import com.nokia.ciq.validator.validator.IntegerValidator;
import com.nokia.ciq.validator.validator.IpAddressValidator;
import com.nokia.ciq.validator.validator.PatternValidator;
import com.nokia.ciq.validator.validator.RequiredValidator;
import com.nokia.ciq.validator.validator.SheetRefValidator;
import com.nokia.ciq.validator.validator.StringValidator;
import com.nokia.ciq.validator.validator.SumEqualsValidator;
import com.nokia.ciq.validator.validator.TemporalValidator;
import com.nokia.ciq.validator.validator.WorkbookCrossRefValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Validates CIQ JSON data (via {@link CiqDataStore}) against a {@link ValidationRulesConfig}.
 *
 * <p>Validation order per sheet:
 * <ol>
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
    private final ConditionalRowRuleValidator conditionalRowRuleValidator;
    private final CompareColumnsValidator compareColumnsValidator = new CompareColumnsValidator();
    private final GroupPresenceValidator groupPresenceValidator = new GroupPresenceValidator();
    private final SumEqualsValidator sumEqualsValidator = new SumEqualsValidator();
    private final WorkbookCrossRefValidator workbookCrossRefValidator = new WorkbookCrossRefValidator();

    public CiqValidationEngine(CiqDataStore store, ValidationRulesConfig rules) {
        this.store                       = store;
        this.rules                       = rules;
        this.validators                  = buildValidatorChain(store);
        this.customValidators            = loadCustomValidators(rules);
        this.conditionalRowRuleValidator = new ConditionalRowRuleValidator(store);
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
     *   <li>{@link RequiredValidator}      — gatekeeper; must remain first</li>
     *   <li>{@link StringValidator}        — minLength/maxLength, enum, allowedValues, boolean, urlScheme</li>
     *   <li>{@link IntegerValidator}       — integer (minValue, maxValue, allowedRanges)</li>
     *   <li>{@link DecimalValidator}       — decimal (minDecimal, maxDecimal, precision)</li>
     *   <li>{@link TemporalValidator}      — date / time / datetime</li>
     *   <li>{@link EmailValidator}         — email (multi-address support)</li>
     *   <li>{@link IpAddressValidator}     — ip / cidr</li>
     *   <li>{@link HostnameValidator}      — hostname / fqdn</li>
     *   <li>{@link PatternValidator}       — mac, phone (built-in), + custom pattern override</li>
     *   <li>{@link CrossRefValidator}           — crossRef (cross-sheet column lookup)</li>
     *   <li>{@link SheetRefValidator}           — sheetRef (cell value must match a workbook sheet name)</li>
     *   <li>{@link ConditionalPatternValidator} — conditionalPattern (pattern driven by a lookup sheet value)</li>
     * </ol>
     */
    private static List<CellValidator> buildValidatorChain(CiqDataStore store) {
        return Arrays.asList(
                new RequiredValidator(),               // 1. required / requiredWhen
                new StringValidator(),                 // 2. minLength/maxLength, enum, allowedValues, boolean, urlScheme
                new IntegerValidator(),                // 3. integer (minValue, maxValue, allowedRanges)
                new DecimalValidator(),                // 4. decimal (minDecimal, maxDecimal, precision)
                new TemporalValidator(),               // 5. date / time / datetime
                new EmailValidator(),                  // 6. email (multi-address support)
                new IpAddressValidator(),              // 7. ip / cidr
                new HostnameValidator(),               // 8. hostname / fqdn
                new PatternValidator(),                // 9. mac, phone (built-in), + custom pattern override
                new CrossRefValidator(store),          // 10. crossRef (cross-sheet column lookup)
                new SheetRefValidator(store),          // 11. sheetRef (cell value must match a sheet name)
                new ConditionalPatternValidator(store) // 12. conditionalPattern (lookup-driven pattern)
        );
    }

    public ValidationReport validate(String nodeType, String activity) throws IOException {
        ValidationReport report = new ValidationReport();
        report.setNodeType(nodeType);
        report.setActivity(activity);

        CiqIndex index = store.getIndex();

        // --- Determine which tables to validate ---
        // Start with sheets listed in the Index's Tables column, then add any additional
        // sheets defined in the YAML rules that are not special and not already included.
        // This ensures Node_Details, USER_ID, and any other configured sheets are always validated.
        Set<String> specialNames = specialSheetNames(rules);
        List<String> tableNames = new ArrayList<>(index.getAllTables());
        if (rules.getSheets() != null) {
            for (String name : rules.getSheets().keySet()) {
                if (!specialNames.contains(name) && !tableNames.contains(name)) {
                    tableNames.add(name);
                }
            }
        }
        if (!tableNames.isEmpty()) {
            log.info("Validating {} sheet(s): {}", tableNames.size(), tableNames);
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

            // Blank-sheet check: fail if the sheet has no rows OR every row has all-null values.
            // Note: isEmpty() must also be checked — when rows is empty the allMatch predicate
            // is vacuously true, so the original !isEmpty() guard would silently pass an empty sheet.
            if (sheet.getRows().isEmpty()
                    || sheet.getRows().stream().allMatch(r ->
                            r.getData().values().stream().allMatch(v -> v == null))) {
                result.setStatus("FAILED");
                result.addError(new ValidationError(0, "-", null,
                        sheet.getRows().isEmpty()
                            ? "Sheet '" + tableName + "' has no data rows"
                            : "Sheet '" + tableName + "' contains only blank rows — no valid data found"));
                report.getSheets().add(result);
                log.warn("Sheet '{}': {}", tableName,
                        sheet.getRows().isEmpty() ? "no data rows" : "all rows are blank");
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

            // Post-row aggregate checks: minOnePerGroup
            if (sheetRules != null) {
                checkMinOnePerGroup(sheet, sheetRules, result);
            }

            log.info("Sheet '{}': {} rows, {} error(s)", tableName,
                    result.getRowsChecked(), result.getErrors().size());
            report.getSheets().add(result);
        }

        // --- Special-sheet validation (Index, Node_ID) ---
        if (store instanceof InMemoryCiqDataStore) {
            InMemoryCiqDataStore imStore = (InMemoryCiqDataStore) store;

            SheetRules indexRules = sheetRulesFor(rules, "Index");
            if (indexRules != null) {
                validateSpecialSheet(imStore.getRawIndexSheet(), "Index",
                        indexRules, index, report);
            }
            SheetRules nodeIdRules = sheetRulesFor(rules, "Node_ID");
            if (nodeIdRules != null) {
                validateSpecialSheet(imStore.getRawNodeIdSheet(), "Node_ID",
                        nodeIdRules, index, report);
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
            if (rules.getOutputs() != null && !rules.getOutputs().isEmpty()) {
                computeOutputs(rules.getOutputs(), report);
                for (Map.Entry<String, String> e : report.getParameters().entrySet()) {
                    log.info("{}={}", e.getKey(), e.getValue());
                }
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

        // Blank-sheet check: same fix as the per-sheet loop — also catches zero-row sheets.
        if (sheet.getRows().isEmpty()
                || sheet.getRows().stream().allMatch(r ->
                        r.getData().values().stream().allMatch(v -> v == null))) {
            result.setStatus("FAILED");
            result.addError(new ValidationError(0, "-", null,
                    sheet.getRows().isEmpty()
                        ? "Sheet '" + sheetLabel + "' has no data rows"
                        : "Sheet '" + sheetLabel + "' contains only blank rows — no valid data found"));
            report.getSheets().add(result);
            log.warn("Sheet '{}' (special): {}", sheetLabel,
                    sheet.getRows().isEmpty() ? "no data rows" : "all rows are blank");
            return;
        }

        Set<String> missingColumns = checkMissingColumns(sheet, sheetRules, result);

        for (CiqRow row : sheet.getRows()) {
            for (Map.Entry<String, ColumnRule> entry : sheetRules.getColumns().entrySet()) {
                if (missingColumns.contains(entry.getKey())) continue;
                validateCell(row, entry.getKey(), entry.getValue(), index, result);
            }
        }

        // Apply per-sheet row rules (require/forbid/compare/one_of/etc.)
        if (sheetRules.getRules() != null) {
            for (CiqRow row : sheet.getRows()) {
                for (SheetRowRule rowRule : sheetRules.getRules()) {
                    List<ValidationError> errors = applyRowRule(row, rowRule);
                    for (ValidationError e : errors) result.addError(e);
                }
            }
        }

        // Post-row aggregate checks: minOnePerGroup
        checkMinOnePerGroup(sheet, sheetRules, result);

        log.info("Sheet '{}' (special): {} rows, {} error(s)", sheetLabel,
                result.getRowsChecked(), result.getErrors().size());
        report.getSheets().add(result);
    }

    // -------------------------------------------------------------------------
    // Post-row aggregate checks
    // -------------------------------------------------------------------------

    /**
     * For each column in {@code sheetRules} that has a {@code minOnePerGroup} rule,
     * verifies that at least one non-blank value exists in that column for every
     * unique value of the groupByColumn.
     */
    private void checkMinOnePerGroup(CiqSheet sheet, SheetRules sheetRules,
                                     SheetValidationResult result) {
        if (sheetRules.getColumns() == null) return;
        for (Map.Entry<String, ColumnRule> entry : sheetRules.getColumns().entrySet()) {
            String colName = entry.getKey();
            MinOnePerGroup rule = entry.getValue().getMinOnePerGroup();
            if (rule == null) continue;
            String groupByCol = rule.getGroupByColumn();
            if (groupByCol == null || groupByCol.trim().isEmpty()) continue;

            Map<String, Boolean> groupSatisfied = new LinkedHashMap<>();
            Map<String, Integer> groupFirstRow = new LinkedHashMap<>();
            for (CiqRow row : sheet.getRows()) {
                String groupVal = row.get(groupByCol);
                if (groupVal == null || groupVal.trim().isEmpty()) continue;
                groupFirstRow.putIfAbsent(groupVal, row.getRowNumber());
                String cellVal = row.get(colName);
                boolean hasValue = cellVal != null && !cellVal.trim().isEmpty();
                groupSatisfied.merge(groupVal, hasValue, Boolean::logicalOr);
            }
            for (Map.Entry<String, Boolean> g : groupSatisfied.entrySet()) {
                if (!g.getValue()) {
                    int firstRow = groupFirstRow.getOrDefault(g.getKey(), 0);
                    result.addError(new ValidationError(firstRow, colName, null,
                            "Column '" + colName + "' must have at least one non-blank value"
                            + " for " + groupByCol + "='" + g.getKey() + "'"));
                }
            }
        }
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
    // Post-validation output extraction
    // -------------------------------------------------------------------------

    /**
     * Computes each declared {@code outputs:} rule against the data store and adds the
     * result to {@code report.parameters}.  Called only when validation has PASSED.
     */
    private void computeOutputs(Map<String, OutputRule> outputs, ValidationReport report) throws IOException {
        for (Map.Entry<String, OutputRule> entry : outputs.entrySet()) {
            String yamlKey  = entry.getKey();
            OutputRule rule = entry.getValue();

            // Resolve the parameter name: explicit 'name' field takes precedence over the YAML key
            String paramName = (rule.getName() != null && !rule.getName().trim().isEmpty())
                    ? rule.getName().trim() : yamlKey;

            if (rule.getSheet() == null || rule.getAggregate() == null) {
                log.warn("outputs[{}]: 'sheet' and 'aggregate' are required — skipping", yamlKey);
                continue;
            }

            CiqSheet sheet = store.getSheet(rule.getSheet());
            if (sheet == null) {
                log.warn("outputs[{}]: sheet '{}' not found — skipping", yamlKey, rule.getSheet());
                continue;
            }

            // 'group' aggregate emits one parameter per distinct groupBy value
            if ("group".equalsIgnoreCase(rule.getAggregate().trim())) {
                Map<String, String> grouped = computeGroupAggregate(yamlKey, sheet, rule);
                for (Map.Entry<String, String> g : grouped.entrySet()) {
                    String key = paramName + "_" + g.getKey();
                    report.getParameters().put(key, g.getValue());
                    log.info("Output {}={}", key, g.getValue());
                }
            } else {
                String value = computeAggregate(yamlKey, sheet, rule);
                report.getParameters().put(paramName, value);
                log.info("Output {}={}", paramName, value);
            }
        }
    }

    /**
     * Evaluates one {@link OutputRule} against the rows of {@code sheet}.
     *
     * @param key   parameter name (used in log messages only)
     * @param sheet sheet to aggregate
     * @param rule  output rule describing the aggregate
     * @return string representation of the computed value (never {@code null})
     */
    private String computeAggregate(String key, CiqSheet sheet, OutputRule rule) {
        String agg = rule.getAggregate().trim().toLowerCase();

        switch (agg) {

            case "count": {
                String col = rule.getColumn();
                long count;
                if (col == null || col.trim().isEmpty()) {
                    count = sheet.getRows().size();
                } else {
                    count = sheet.getRows().stream()
                            .filter(r -> !isBlank(r.get(col)))
                            .count();
                }
                return String.valueOf(count);
            }

            case "sum": {
                if (isBlank(rule.getColumn())) {
                    log.warn("outputs[{}]: 'column' is required for aggregate 'sum' — returning 0", key);
                    return "0";
                }
                double total = 0.0;
                for (CiqRow row : sheet.getRows()) {
                    String v = row.get(rule.getColumn());
                    if (v != null && !v.trim().isEmpty()) {
                        try { total += Double.parseDouble(v.trim()); }
                        catch (NumberFormatException ignored) { /* skip non-numeric */ }
                    }
                }
                if (total == Math.floor(total) && !Double.isInfinite(total)) {
                    return String.valueOf((long) total);
                }
                return String.valueOf(total);
            }

            case "distinct": {
                if (isBlank(rule.getColumn())) {
                    log.warn("outputs[{}]: 'column' is required for aggregate 'distinct' — returning empty", key);
                    return "";
                }
                String sep = (rule.getSeparator() != null) ? rule.getSeparator() : ",";
                Set<String> seen = new LinkedHashSet<>();
                for (CiqRow row : sheet.getRows()) {
                    String v = row.get(rule.getColumn());
                    if (v != null && !v.trim().isEmpty()) seen.add(v.trim());
                }
                return String.join(sep, seen);
            }

            case "distinct_count": {
                if (isBlank(rule.getColumn())) {
                    log.warn("outputs[{}]: 'column' is required for aggregate 'distinct_count' — returning 0", key);
                    return "0";
                }
                Set<String> seen = new LinkedHashSet<>();
                for (CiqRow row : sheet.getRows()) {
                    String v = row.get(rule.getColumn());
                    if (v != null && !v.trim().isEmpty()) seen.add(v.trim());
                }
                return String.valueOf(seen.size());
            }

            default:
                log.warn("outputs[{}]: unknown aggregate '{}' — returning empty", key, rule.getAggregate());
                return "";
        }
    }

    /**
     * Groups distinct values of {@code rule.getColumn()} by each distinct value of
     * {@code rule.getGroupBy()}, returning a map of {@code groupValue → joinedColumnValues}.
     * Insertion order is preserved (first-seen group value comes first).
     */
    private Map<String, String> computeGroupAggregate(String key, CiqSheet sheet, OutputRule rule) {
        String groupByCol = rule.getGroupBy();
        String valueCol   = rule.getColumn();
        if (isBlank(groupByCol) || isBlank(valueCol)) {
            log.warn("outputs[{}]: 'groupBy' and 'column' are required for aggregate 'group' — returning empty", key);
            return new LinkedHashMap<>();
        }
        String sep = (rule.getSeparator() != null) ? rule.getSeparator() : ",";

        // Preserve insertion order; use LinkedHashSet per group to deduplicate while keeping order
        Map<String, LinkedHashSet<String>> groups = new LinkedHashMap<>();
        for (CiqRow row : sheet.getRows()) {
            String groupVal = row.get(groupByCol);
            String colVal   = row.get(valueCol);
            if (isBlank(groupVal)) continue;
            groupVal = groupVal.trim();
            groups.computeIfAbsent(groupVal, k -> new LinkedHashSet<>());
            if (!isBlank(colVal)) groups.get(groupVal).add(colVal.trim());
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : groups.entrySet()) {
            result.put(e.getKey(), String.join(sep, e.getValue()));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Global checks
    // -------------------------------------------------------------------------

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Returns column rules for a named sheet, or {@code null} when not configured. */
    private static SheetRules sheetRulesFor(ValidationRulesConfig rules, String sheetName) {
        if (rules.getSheets() == null || sheetName == null) return null;
        return rules.getSheets().get(sheetName);
    }

    /**
     * Returns the set of sheet names that are treated as special (Index, Node_ID).
     * These are validated separately and must be excluded from the generic per-sheet loop.
     */
    private static Set<String> specialSheetNames(ValidationRulesConfig rules) {
        Set<String> names = new java.util.HashSet<>();
        names.add("Index");
        names.add("Node_ID");
        return names;
    }
}

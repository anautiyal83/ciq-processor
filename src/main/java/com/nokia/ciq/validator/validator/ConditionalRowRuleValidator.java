package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.RowCondition;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles {@code require} and {@code forbid} row rules, optionally gated by a
 * {@code when} condition.
 *
 * <h3>Same-sheet directives</h3>
 * <ul>
 *   <li>{@code require: ColumnName} — the column must be non-blank.</li>
 *   <li>{@code forbid:  ColumnName} — the column must be blank.</li>
 * </ul>
 *
 * <h3>Cross-sheet directives ({@code SheetName.ColumnName} syntax)</h3>
 * <ul>
 *   <li>{@code require: Node_Details.Node_Name} — the current row's value for
 *       {@code Node_Name} must exist in {@code Node_Details.Node_Name}.</li>
 *   <li>{@code forbid:  Blacklist.ID} — the current row's {@code ID} value must
 *       NOT appear in {@code Blacklist.ID}.</li>
 * </ul>
 *
 * <h3>Cross-sheet {@code when} conditions</h3>
 * <pre>
 * when:
 *   sheet: Node_Details
 *   column: Node_Name
 *   operator: exists       # true when current row's Node_Name exists in Node_Details.Node_Name
 *
 * when:
 *   sheet: Blacklist
 *   column: ID
 *   operator: notExists
 * </pre>
 */
public class ConditionalRowRuleValidator implements RowValidator {

    private static final Logger log = LoggerFactory.getLogger(ConditionalRowRuleValidator.class);

    private final CiqDataStore store;
    /** Cache: "SheetName.ColumnName" → lower-cased value set for fast lookup. */
    private final Map<String, Set<String>> cache = new HashMap<>();

    public ConditionalRowRuleValidator(CiqDataStore store) {
        this.store = store;
    }

    @Override
    public List<ValidationError> validate(CiqRow row, SheetRowRule rule) {
        if (rule.getRequire() == null && rule.getForbid() == null) {
            return Collections.emptyList();
        }

        List<ValidationError> errors = new ArrayList<>();
        if (!evaluateCondition(rule.getWhen(), row)) return errors;

        // --- require ---
        if (rule.getRequire() != null) {
            String target = rule.getRequire();
            if (isCrossSheet(target)) {
                String[] parts = splitRef(target);
                String colVal = row.get(parts[1]);
                if (isBlank(colVal)) {
                    errors.add(new ValidationError(row.getRowNumber(), parts[1], null,
                            "Column '" + parts[1] + "' is required for cross-sheet lookup "
                            + "against '" + target + "' but is blank"));
                } else if (!existsInSheet(parts[0], parts[1], colVal)) {
                    errors.add(new ValidationError(row.getRowNumber(), parts[1], colVal,
                            "Value '" + colVal + "' not found in " + target));
                }
            } else {
                String val = row.get(target);
                if (isBlank(val)) {
                    errors.add(new ValidationError(row.getRowNumber(), target, null,
                            "Column '" + target + "' is required but is blank"));
                }
            }
        }

        // --- forbid ---
        if (rule.getForbid() != null) {
            String target = rule.getForbid();
            if (isCrossSheet(target)) {
                String[] parts = splitRef(target);
                String colVal = row.get(parts[1]);
                if (!isBlank(colVal) && existsInSheet(parts[0], parts[1], colVal)) {
                    errors.add(new ValidationError(row.getRowNumber(), parts[1], colVal,
                            "Value '" + colVal + "' is not allowed — found in " + target));
                }
            } else {
                String val = row.get(target);
                if (!isBlank(val)) {
                    errors.add(new ValidationError(row.getRowNumber(), target, val,
                            "Column '" + target + "' must be blank but contains: " + val));
                }
            }
        }

        return errors;
    }

    /**
     * Evaluates a {@link RowCondition} against the given row.
     * A {@code null} condition always returns {@code true} (no condition = always apply).
     */
    boolean evaluateCondition(RowCondition cond, CiqRow row) {
        if (cond == null) return true;

        // Compound conditions
        if (cond.getAll() != null) {
            for (RowCondition c : cond.getAll()) {
                if (!evaluateCondition(c, row)) return false;
            }
            return true;
        }
        if (cond.getAny() != null) {
            for (RowCondition c : cond.getAny()) {
                if (evaluateCondition(c, row)) return true;
            }
            return false;
        }

        String operator = Operator.normalize(cond.getOperator() != null ? cond.getOperator() : "");

        // Cross-sheet operators (sheet: field is set)
        if (cond.getSheet() != null) {
            String colVal = row.get(cond.getColumn());
            switch (operator) {
                case Operator.EXISTS:
                    return !isBlank(colVal) && existsInSheet(cond.getSheet(), cond.getColumn(), colVal);
                case Operator.NOT_EXISTS:
                    return isBlank(colVal) || !existsInSheet(cond.getSheet(), cond.getColumn(), colVal);
                default:
                    log.warn("Unknown cross-sheet operator '{}' — treated as false", operator);
                    return false;
            }
        }

        // Same-sheet operators
        String colVal = row.get(cond.getColumn());
        switch (operator) {
            case Operator.BLANK:                 return isBlank(colVal);
            case Operator.NOT_BLANK:             return !isBlank(colVal);
            case Operator.EQUALS:                return cond.getValue() != null && cond.getValue().equalsIgnoreCase(colVal);
            case Operator.NOT_EQUALS:            return cond.getValue() == null || !cond.getValue().equalsIgnoreCase(colVal);
            case Operator.CONTAINS:              return colVal != null && cond.getValue() != null
                                                     && colVal.toLowerCase().contains(cond.getValue().toLowerCase());
            case Operator.GREATER_THAN:
            case Operator.GREATER_THAN_OR_EQUALS:
            case Operator.LESS_THAN:
            case Operator.LESS_THAN_OR_EQUALS:   return Operator.evaluate(operator, compareNumericOrString(colVal, cond.getValue()));
            default:
                log.warn("Unknown row-condition operator '{}' — treated as false", operator);
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // Cross-sheet helpers
    // -------------------------------------------------------------------------

    /** Returns true if {@code value} exists (case-insensitive) in {@code sheet.column}. */
    private boolean existsInSheet(String sheetName, String columnName, String value) {
        return resolveSheet(sheetName, columnName).contains(value.trim().toLowerCase());
    }

    /** Resolves and caches all values for {@code sheetName.columnName} (lower-cased). */
    private Set<String> resolveSheet(String sheetName, String columnName) {
        String key = sheetName + "." + columnName;
        if (cache.containsKey(key)) return cache.get(key);

        Set<String> values = new HashSet<>();
        try {
            CiqSheet sheet = store.getSheet(sheetName);
            if (sheet != null) {
                for (CiqRow r : sheet.getRows()) {
                    String v = r.get(columnName);
                    if (v != null && !v.trim().isEmpty()) values.add(v.trim().toLowerCase());
                }
            } else {
                log.warn("Cross-sheet row rule: sheet '{}' not found", sheetName);
            }
        } catch (IOException e) {
            log.warn("Cross-sheet row rule: failed to read '{}': {}", key, e.getMessage());
        }

        cache.put(key, values);
        return values;
    }

    private static boolean isCrossSheet(String ref) {
        return ref != null && ref.contains(".");
    }

    private static String[] splitRef(String ref) {
        int dot = ref.indexOf('.');
        return new String[]{ ref.substring(0, dot), ref.substring(dot + 1) };
    }

    private static int compareNumericOrString(String a, String b) {
        if (a == null || b == null) return 0;
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

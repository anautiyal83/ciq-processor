package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.SubsetRule;
import com.nokia.ciq.validator.config.UniqueRule;
import com.nokia.ciq.validator.config.WorkbookRule;
import com.nokia.ciq.validator.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles workbook-level cross-sheet rules: {@code subset}, {@code superset},
 * {@code match}, and {@code unique}.
 *
 * <p>References use the {@code "SheetName.ColumnName"} format.
 */
public class WorkbookCrossRefValidator implements WorkbookRuleValidator {

    private static final Logger log = LoggerFactory.getLogger(WorkbookCrossRefValidator.class);

    @Override
    public List<ValidationError> validate(WorkbookRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();

        if (rule.getSubset() != null) {
            errors.addAll(checkSubset(rule.getSubset(), store, false));
        }

        if (rule.getSuperset() != null) {
            // Superset = reverse subset: every value in "to" must appear in "from"
            SubsetRule reversed = new SubsetRule();
            reversed.setFrom(rule.getSuperset().getTo());
            reversed.setTo(rule.getSuperset().getFrom());
            errors.addAll(checkSubset(reversed, store, false));
        }

        if (rule.getMatch() != null) {
            // Match = subset in both directions
            errors.addAll(checkSubset(rule.getMatch(), store, false));
            SubsetRule reversed = new SubsetRule();
            reversed.setFrom(rule.getMatch().getTo());
            reversed.setTo(rule.getMatch().getFrom());
            errors.addAll(checkSubset(reversed, store, false));
        }

        if (rule.getUnique() != null) {
            errors.addAll(checkUnique(rule.getUnique(), store));
        }

        return errors;
    }

    // -------------------------------------------------------------------------
    // Subset check
    // -------------------------------------------------------------------------

    private List<ValidationError> checkSubset(SubsetRule subsetRule, CiqDataStore store,
                                               boolean suppressLog) {
        List<ValidationError> errors = new ArrayList<>();
        if (subsetRule.getFrom() == null || subsetRule.getTo() == null) return errors;

        Set<String> fromVals = resolveColumn(subsetRule.getFrom(), store);
        Set<String> toVals   = resolveColumn(subsetRule.getTo(),   store);

        for (String v : fromVals) {
            if (!toVals.contains(v)) {
                errors.add(new ValidationError(0, subsetRule.getFrom(), v,
                        "Value '" + v + "' from [" + subsetRule.getFrom()
                        + "] not found in [" + subsetRule.getTo() + "]"));
            }
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // Unique check
    // -------------------------------------------------------------------------

    private List<ValidationError> checkUnique(UniqueRule uniqueRule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();
        if (uniqueRule.getColumns() == null || uniqueRule.getColumns().isEmpty()) return errors;

        // We need a sheet context — derive it from the first column reference
        // (assumed to be "SheetName.ColumnName"; if plain name, treat whole store)
        String firstRef = uniqueRule.getColumns().get(0);
        String sheetName = parseSheetName(firstRef);
        if (sheetName == null) return errors;

        CiqSheet sheet;
        try {
            sheet = store.getSheet(sheetName);
        } catch (IOException e) {
            log.warn("Cannot read sheet '{}' for unique check: {}", sheetName, e.getMessage());
            return errors;
        }
        if (sheet == null) return errors;

        List<String> colNames = new ArrayList<>();
        for (String ref : uniqueRule.getColumns()) {
            colNames.add(parseColumnName(ref));
        }

        Set<String> seen = new LinkedHashSet<>();
        Map<String, Integer> firstOccurrence = new LinkedHashMap<>();

        for (CiqRow row : sheet.getRows()) {
            StringBuilder key = new StringBuilder();
            for (String col : colNames) {
                String val = row.get(col);
                if (key.length() > 0) key.append("|");
                key.append(val != null ? val : "");
            }
            String k = key.toString();
            if (seen.contains(k)) {
                errors.add(new ValidationError(row.getRowNumber(), String.join("+", colNames), k,
                        "Duplicate composite key [" + k + "] in sheet '" + sheetName
                        + "' (first seen at row " + firstOccurrence.get(k) + ")"));
            } else {
                seen.add(k);
                firstOccurrence.put(k, row.getRowNumber());
            }
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a {@code "SheetName.ColumnName"} reference to the set of distinct
     * non-blank values in that column.
     */
    private Set<String> resolveColumn(String ref, CiqDataStore store) {
        Set<String> values = new LinkedHashSet<>();
        String sheetName = parseSheetName(ref);
        String colName   = parseColumnName(ref);
        if (sheetName == null || colName == null) return values;

        CiqSheet sheet;
        try {
            sheet = store.getSheet(sheetName);
        } catch (IOException e) {
            log.warn("Cannot read sheet '{}' for cross-ref: {}", sheetName, e.getMessage());
            return values;
        }
        if (sheet == null) return values;

        for (CiqRow row : sheet.getRows()) {
            String val = row.get(colName);
            if (val != null && !val.trim().isEmpty()) {
                values.add(val.trim());
            }
        }
        return values;
    }

    /** Returns the sheet part of a "Sheet.Column" reference, or {@code null} if absent. */
    private static String parseSheetName(String ref) {
        if (ref == null) return null;
        int dot = ref.indexOf('.');
        return dot > 0 ? ref.substring(0, dot) : null;
    }

    /** Returns the column part of a "Sheet.Column" reference, or the whole string if no dot. */
    private static String parseColumnName(String ref) {
        if (ref == null) return null;
        int dot = ref.indexOf('.');
        return dot >= 0 ? ref.substring(dot + 1) : ref;
    }
}

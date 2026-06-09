package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ConstantWithinRule;
import com.nokia.ciq.validator.config.CountPerRule;
import com.nokia.ciq.validator.config.SetMatchRule;
import com.nokia.ciq.validator.config.SubsetAnyRule;
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

        if (rule.getSubsetAny() != null) {
            errors.addAll(checkSubsetAny(rule.getSubsetAny(), store));
        }

        if (rule.getCountPer() != null) {
            errors.addAll(checkCountPer(rule.getCountPer(), store));
        }

        if (rule.getConstantWithin() != null) {
            errors.addAll(checkConstantWithin(rule.getConstantWithin(), store));
        }

        if (rule.getSetMatch() != null) {
            errors.addAll(checkSetMatch(rule.getSetMatch(), store));
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
    // SubsetAny check
    // -------------------------------------------------------------------------

    private List<ValidationError> checkSubsetAny(SubsetAnyRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();
        if (rule.getFrom() == null || rule.getTo() == null || rule.getTo().isEmpty()) return errors;

        Set<String> fromVals = resolveColumn(rule.getFrom(), store);

        // Union of all target columns — value must appear in at least one
        Set<String> unionVals = new LinkedHashSet<>();
        for (String to : rule.getTo()) {
            unionVals.addAll(resolveColumn(to, store));
        }

        for (String v : fromVals) {
            if (!unionVals.contains(v)) {
                errors.add(new ValidationError(0, rule.getFrom(), v,
                        "Value '" + v + "' from [" + rule.getFrom()
                        + "] not found in any of " + rule.getTo()));
            }
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // CountPer check
    // -------------------------------------------------------------------------

    private List<ValidationError> checkCountPer(CountPerRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();
        if (rule.getSheet() == null || rule.getGroup() == null || rule.getCount() <= 0) return errors;

        CiqSheet sheet = getSheet(store, rule.getSheet());
        if (sheet == null) {
            log.warn("count_per: sheet '{}' not found", rule.getSheet());
            return errors;
        }

        // Count occurrences of each distinct group value
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CiqRow row : sheet.getRows()) {
            String val = row.get(rule.getGroup());
            if (val != null && !val.trim().isEmpty()) {
                counts.merge(val.trim(), 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() != rule.getCount()) {
                errors.add(new ValidationError(0, rule.getGroup(), e.getKey(),
                        rule.getSheet() + "." + rule.getGroup() + " value '" + e.getKey()
                        + "' appears " + e.getValue() + " time(s) — expected exactly " + rule.getCount()));
            }
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // ConstantWithin check
    // -------------------------------------------------------------------------

    private List<ValidationError> checkConstantWithin(ConstantWithinRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();
        if (rule.getSheet() == null || rule.getGroup() == null
                || rule.getColumns() == null || rule.getColumns().isEmpty()) return errors;

        CiqSheet sheet = getSheet(store, rule.getSheet());
        if (sheet == null) {
            log.warn("constant_within: sheet '{}' not found", rule.getSheet());
            return errors;
        }

        // For each group value, collect distinct values of each target column
        Map<String, Map<String, Set<String>>> groupColValues = new LinkedHashMap<>();
        for (CiqRow row : sheet.getRows()) {
            String groupVal = row.get(rule.getGroup());
            if (groupVal == null || groupVal.trim().isEmpty()) continue;
            groupVal = groupVal.trim();

            groupColValues.putIfAbsent(groupVal, new LinkedHashMap<>());
            for (String col : rule.getColumns()) {
                String v = row.get(col);
                if (v != null && !v.trim().isEmpty()) {
                    groupColValues.get(groupVal)
                                  .computeIfAbsent(col, k -> new LinkedHashSet<>())
                                  .add(v.trim());
                }
            }
        }

        for (Map.Entry<String, Map<String, Set<String>>> groupEntry : groupColValues.entrySet()) {
            String groupVal = groupEntry.getKey();
            for (Map.Entry<String, Set<String>> colEntry : groupEntry.getValue().entrySet()) {
                if (colEntry.getValue().size() > 1) {
                    errors.add(new ValidationError(0, colEntry.getKey(), groupVal,
                            rule.getSheet() + "." + colEntry.getKey() + " must be constant within "
                            + rule.getGroup() + " '" + groupVal + "' — found: " + colEntry.getValue()));
                }
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

    // -------------------------------------------------------------------------
    // SetMatch check
    // -------------------------------------------------------------------------

    private List<ValidationError> checkSetMatch(SetMatchRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();
        SetMatchRule.Source src = rule.getSource();
        SetMatchRule.Target tgt = rule.getTarget();
        if (src == null || tgt == null) return errors;
        if (src.getSheet() == null || src.getGroup() == null || src.getColumn() == null) return errors;
        if (tgt.getSheet() == null || tgt.getColumns() == null || tgt.getColumns().isEmpty()) return errors;

        CiqSheet srcSheet = getSheet(store, src.getSheet());
        CiqSheet tgtSheet = getSheet(store, tgt.getSheet());
        if (srcSheet == null) { log.warn("set_match: source sheet '{}' not found", src.getSheet()); return errors; }
        if (tgtSheet == null) { log.warn("set_match: target sheet '{}' not found", tgt.getSheet()); return errors; }

        // Build source: group → Set<value>
        Map<String, Set<String>> sourceGroups = new LinkedHashMap<>();
        for (CiqRow row : srcSheet.getRows()) {
            String groupVal = row.get(src.getGroup());
            String colVal   = row.get(src.getColumn());
            if (groupVal == null || groupVal.trim().isEmpty()) continue;
            if (colVal   == null || colVal.trim().isEmpty())   continue;
            sourceGroups.computeIfAbsent(groupVal.trim(), k -> new LinkedHashSet<>()).add(colVal.trim());
        }

        // Build target: list of Set<value> (one set per row)
        List<Set<String>> targetSets = new ArrayList<>();
        for (CiqRow row : tgtSheet.getRows()) {
            Set<String> rowSet = new LinkedHashSet<>();
            for (String col : tgt.getColumns()) {
                String v = row.get(col);
                if (v != null && !v.trim().isEmpty()) rowSet.add(v.trim());
            }
            if (!rowSet.isEmpty()) targetSets.add(rowSet);
        }

        // Check every source group has a matching target row
        for (Map.Entry<String, Set<String>> e : sourceGroups.entrySet()) {
            if (targetSets.stream().noneMatch(t -> t.equals(e.getValue()))) {
                errors.add(new ValidationError(0, src.getGroup(), e.getKey(),
                        src.getGroup() + " '" + e.getKey() + "' has " + src.getSheet()
                        + "." + src.getColumn() + " set " + e.getValue()
                        + " — no matching row found in " + tgt.getSheet()
                        + " columns " + tgt.getColumns()));
            }
        }

        // Check every target row has a matching source group
        for (Set<String> tgtSet : targetSets) {
            if (sourceGroups.values().stream().noneMatch(s -> s.equals(tgtSet))) {
                errors.add(new ValidationError(0, String.join("+", tgt.getColumns()),
                        tgtSet.toString(),
                        tgt.getSheet() + " row with " + tgt.getColumns() + " = " + tgtSet
                        + " — no matching " + src.getGroup() + " group found in " + src.getSheet()));
            }
        }
        return errors;
    }

    /** Loads a sheet from the store by name, returning {@code null} on missing or error. */
    private CiqSheet getSheet(CiqDataStore store, String sheetName) {
        try {
            return store.getSheet(sheetName);
        } catch (IOException e) {
            log.warn("Cannot read sheet '{}': {}", sheetName, e.getMessage());
            return null;
        }
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

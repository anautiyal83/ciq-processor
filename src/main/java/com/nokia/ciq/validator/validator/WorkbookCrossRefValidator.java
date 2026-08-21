package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ConstantWithinRule;
import com.nokia.ciq.validator.config.ContiguousSequenceRule;
import com.nokia.ciq.validator.config.CountPerRule;
import com.nokia.ciq.validator.config.SetMatchRule;
import com.nokia.ciq.validator.config.SetRule;
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
            errors.addAll(checkSuperset(rule.getSuperset(), store));
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

        if (rule.getContiguousSequence() != null) {
            errors.addAll(checkContiguousSequence(rule.getContiguousSequence(), store));
        }

        if (rule.getSet() != null) {
            errors.addAll(checkSet(rule.getSet(), store));
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

        Set<String> fromVals = subsetRule.getWhere() != null
                ? resolveColumnWhere(subsetRule.getFrom(), subsetRule.getWhere(), store)
                : resolveColumn(subsetRule.getFrom(), store);
        Set<String> toVals   = resolveColumn(subsetRule.getTo(), store);

        String fromDesc = subsetRule.getWhere() != null
                ? subsetRule.getFrom() + " WHERE " + subsetRule.getWhere()
                : subsetRule.getFrom();

        for (String v : fromVals) {
            if (!toVals.contains(v)) {
                errors.add(new ValidationError(0, subsetRule.getFrom(), v,
                        "Value '" + v + "' from [" + fromDesc
                        + "] not found in [" + subsetRule.getTo() + "]"));
            }
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // Superset check — every value in `to` must appear in `from`.
    // An optional `where` filters the `from` side (same "column must live in the
    // `from` sheet" rule as subset), mirroring checkSubset.
    // -------------------------------------------------------------------------

    private List<ValidationError> checkSuperset(SubsetRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();
        if (rule.getFrom() == null || rule.getTo() == null) return errors;

        Set<String> allowedVals = rule.getWhere() != null
                ? resolveColumnWhere(rule.getFrom(), rule.getWhere(), store)
                : resolveColumn(rule.getFrom(), store);
        Set<String> requiredVals = resolveColumn(rule.getTo(), store);

        String fromDesc = rule.getWhere() != null
                ? rule.getFrom() + " WHERE " + rule.getWhere()
                : rule.getFrom();

        for (String v : requiredVals) {
            if (!allowedVals.contains(v)) {
                errors.add(new ValidationError(0, rule.getTo(), v,
                        "Value '" + v + "' from [" + rule.getTo()
                        + "] not found in [" + fromDesc + "]"));
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
    // ContiguousSequence check — within each partition, a column's values must
    // form contiguous blocks (a value may not reappear after a different value).
    // Evaluated in sheet row order.
    // -------------------------------------------------------------------------

    private List<ValidationError> checkContiguousSequence(ContiguousSequenceRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();
        if (rule.getSheet() == null || rule.getColumn() == null
                || rule.getPartitionBy() == null || rule.getPartitionBy().isEmpty()) {
            return errors;
        }

        CiqSheet sheet = getSheet(store, rule.getSheet());
        if (sheet == null) {
            log.warn("contiguous_sequence: sheet '{}' not found", rule.getSheet());
            return errors;
        }

        // Per partition: the last value seen (current run) and the set of values already closed.
        Map<String, String> lastByPartition = new LinkedHashMap<>();
        Map<String, Set<String>> seenByPartition = new LinkedHashMap<>();

        for (CiqRow row : sheet.getRows()) {
            StringBuilder pk = new StringBuilder();
            for (String col : rule.getPartitionBy()) {
                String pv = row.get(col);
                pk.append(pv != null ? pv.trim() : "").append('|');
            }
            String partition = pk.toString();

            String raw = row.get(rule.getColumn());
            String val = raw != null ? raw.trim() : "";
            if (val.isEmpty()) continue;   // blank sequence cell — ignore

            String last = lastByPartition.get(partition);
            if (val.equals(last)) continue; // still inside the same contiguous run

            Set<String> seen = seenByPartition.computeIfAbsent(partition, k -> new LinkedHashSet<>());
            if (seen.contains(val)) {
                errors.add(new ValidationError(row.getRowNumber(), rule.getColumn(), val,
                        rule.getColumn() + " value '" + val + "' reappears at row "
                        + row.getRowNumber() + " within " + partitionDesc(rule.getPartitionBy(), row)
                        + ". Once a different " + rule.getColumn() + " value appears, '"
                        + val + "' cannot appear again."));
            } else {
                seen.add(val);
            }
            lastByPartition.put(partition, val);
        }
        return errors;
    }

    /** Builds a readable partition description like {@code "NodeGroup=NG1, CRGROUP=CR1"}. */
    private String partitionDesc(List<String> cols, CiqRow row) {
        StringBuilder sb = new StringBuilder();
        for (String c : cols) {
            if (sb.length() > 0) sb.append(", ");
            String v = row.get(c);
            sb.append(c).append('=').append(v != null ? v.trim() : "");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Set (partition-scoped subset) check
    //
    // For each partition on the `from` side, every distinct value collected must
    // appear in the target partition derived by keeping only the target's
    // partitionBy columns. Reports the missing values per source partition.
    // -------------------------------------------------------------------------

    private List<ValidationError> checkSet(SetRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();
        SetRule.Source src = rule.getFrom();
        SetRule.Target tgt = rule.getTo();
        if (src == null || tgt == null) return errors;
        if (src.getSheet() == null || src.getColumn() == null
                || src.getPartitionBy() == null || src.getPartitionBy().isEmpty()) return errors;
        if (tgt.getSheet() == null || tgt.getColumn() == null
                || tgt.getPartitionBy() == null || tgt.getPartitionBy().isEmpty()) return errors;

        CiqSheet srcSheet = getSheet(store, src.getSheet());
        CiqSheet tgtSheet = getSheet(store, tgt.getSheet());
        if (srcSheet == null) { log.warn("set: source sheet '{}' not found", src.getSheet()); return errors; }
        if (tgtSheet == null) { log.warn("set: target sheet '{}' not found", tgt.getSheet()); return errors; }

        // Target partitionBy must be a subset (by name) of source partitionBy —
        // otherwise we can't derive the target key from the source key.
        List<Integer> tgtKeyIndices = new ArrayList<>();
        for (String tCol : tgt.getPartitionBy()) {
            int idx = src.getPartitionBy().indexOf(tCol);
            if (idx < 0) {
                log.warn("set: target partitionBy column '{}' is not present in source partitionBy {} — skipping rule",
                        tCol, src.getPartitionBy());
                return errors;
            }
            tgtKeyIndices.add(idx);
        }

        // Parse optional where clause
        String whereCol = null;
        String whereVal = null;
        if (src.getWhere() != null) {
            int eq = src.getWhere().indexOf('=');
            if (eq < 0) {
                log.warn("set: invalid where clause (no '='): {}", src.getWhere());
            } else {
                whereCol = src.getWhere().substring(0, eq).trim();
                whereVal = src.getWhere().substring(eq + 1).trim();
            }
        }

        // Build source: sourceKey (list of partition values, aligned with src.partitionBy) -> Set<value>
        // Preserve first-seen row key strings for readable errors.
        Map<List<String>, Set<String>> sourceGroups = new LinkedHashMap<>();
        for (CiqRow row : srcSheet.getRows()) {
            if (whereCol != null) {
                String rv = row.get(whereCol);
                if (!whereVal.equals(rv != null ? rv.trim() : null)) continue;
            }
            List<String> key = new ArrayList<>(src.getPartitionBy().size());
            for (String pc : src.getPartitionBy()) {
                String pv = row.get(pc);
                key.add(pv != null ? pv.trim() : "");
            }
            String val = row.get(src.getColumn());
            if (val == null || val.trim().isEmpty()) continue;
            sourceGroups.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(val.trim());
        }

        // Build target: targetKey (list aligned with tgt.partitionBy) -> Set<value>
        Map<List<String>, Set<String>> targetGroups = new LinkedHashMap<>();
        for (CiqRow row : tgtSheet.getRows()) {
            List<String> key = new ArrayList<>(tgt.getPartitionBy().size());
            for (String pc : tgt.getPartitionBy()) {
                String pv = row.get(pc);
                key.add(pv != null ? pv.trim() : "");
            }
            String val = row.get(tgt.getColumn());
            if (val == null || val.trim().isEmpty()) continue;
            targetGroups.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(val.trim());
        }

        // Compare each source partition against its projected target partition
        for (Map.Entry<List<String>, Set<String>> e : sourceGroups.entrySet()) {
            List<String> srcKey = e.getKey();
            List<String> tgtKey = new ArrayList<>(tgtKeyIndices.size());
            for (int idx : tgtKeyIndices) tgtKey.add(srcKey.get(idx));

            Set<String> tgtValues = targetGroups.getOrDefault(tgtKey, java.util.Collections.emptySet());

            List<String> missing = new ArrayList<>();
            for (String v : e.getValue()) {
                if (!tgtValues.contains(v)) missing.add(v);
            }
            if (missing.isEmpty()) continue;

            String srcKeyDesc = describeKey(src.getPartitionBy(), srcKey);
            String tgtKeyDesc = describeKey(tgt.getPartitionBy(), tgtKey);
            String whereDesc = src.getWhere() != null ? " WHERE " + src.getWhere() : "";
            errors.add(new ValidationError(0, src.getColumn(), missing.toString(),
                    src.getSheet() + "." + src.getColumn() + whereDesc
                    + " (" + srcKeyDesc + ") requires values " + e.getValue()
                    + " in " + tgt.getSheet() + "." + tgt.getColumn()
                    + " (" + tgtKeyDesc + "); missing: " + missing));
        }

        // Reverse direction (set equality): every target value must appear in the union of
        // source values that project onto that target partition. Flags extras on the `to` side.
        // Skipped entirely when the source is empty — i.e. the table is not declared in the
        // Index at all (TABLES filter matched no rows). Such a sheet is out of scope for this
        // rule, so its values are not reported as extras.
        if (rule.isBidirectional() && !sourceGroups.isEmpty()) {
            // Union of source values per projected target key.
            Map<List<String>, Set<String>> srcByTargetKey = new LinkedHashMap<>();
            for (Map.Entry<List<String>, Set<String>> e : sourceGroups.entrySet()) {
                List<String> srcKey = e.getKey();
                List<String> projKey = new ArrayList<>(tgtKeyIndices.size());
                for (int idx : tgtKeyIndices) projKey.add(srcKey.get(idx));
                srcByTargetKey.computeIfAbsent(projKey, k -> new LinkedHashSet<>()).addAll(e.getValue());
            }

            String whereDesc = src.getWhere() != null ? " WHERE " + src.getWhere() : "";
            for (Map.Entry<List<String>, Set<String>> e : targetGroups.entrySet()) {
                List<String> tgtKey = e.getKey();
                Set<String> allowed = srcByTargetKey.getOrDefault(tgtKey, java.util.Collections.emptySet());

                List<String> extra = new ArrayList<>();
                for (String v : e.getValue()) {
                    if (!allowed.contains(v)) extra.add(v);
                }
                if (extra.isEmpty()) continue;

                String tgtKeyDesc = describeKey(tgt.getPartitionBy(), tgtKey);
                errors.add(new ValidationError(0, tgt.getColumn(), extra.toString(),
                        tgt.getSheet() + "." + tgt.getColumn() + " (" + tgtKeyDesc + ") has values "
                        + e.getValue() + " not present in " + src.getSheet() + "." + src.getColumn()
                        + whereDesc + "; extra: " + extra));
            }
        }

        return errors;
    }

    /** Builds "col1=val1, col2=val2" for a partition key. */
    private String describeKey(List<String> cols, List<String> vals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(cols.get(i)).append('=').append(vals.get(i));
        }
        return sb.toString();
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

    /**
     * Resolves a {@code "SheetName.ColumnName"} reference to the set of distinct
     * non-blank values, but only for rows that match the {@code where} filter.
     *
     * <p>{@code where} format: {@code "ColumnName = value"} (single equality condition).
     */
    private Set<String> resolveColumnWhere(String ref, String where, CiqDataStore store) {
        Set<String> values = new LinkedHashSet<>();
        String sheetName = parseSheetName(ref);
        String colName   = parseColumnName(ref);
        if (sheetName == null || colName == null || where == null) return values;

        int eqIdx = where.indexOf('=');
        if (eqIdx < 0) {
            log.warn("Invalid where clause (no '='): {}", where);
            return values;
        }
        String filterCol = where.substring(0, eqIdx).trim();
        String filterVal = where.substring(eqIdx + 1).trim();

        CiqSheet sheet;
        try {
            sheet = store.getSheet(sheetName);
        } catch (IOException e) {
            log.warn("Cannot read sheet '{}' for cross-ref: {}", sheetName, e.getMessage());
            return values;
        }
        if (sheet == null) return values;

        for (CiqRow row : sheet.getRows()) {
            String rowFilterVal = row.get(filterCol);
            if (!filterVal.equals(rowFilterVal != null ? rowFilterVal.trim() : null)) continue;
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

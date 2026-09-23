package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ConstantWithinRule;
import com.nokia.ciq.validator.config.CrossSheetCompareRule;
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
 * {@code match}, {@code unique}, and {@code cross_sheet_compare}.
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

        if (rule.getCrossSheetCompare() != null) {
            errors.addAll(checkCrossSheetCompare(rule.getCrossSheetCompare(), store));
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
        String sheetName = parseSheetName(firstRef, store);
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
            colNames.add(parseColumnName(ref, store));
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

    // -------------------------------------------------------------------------
    // Cross-sheet paired-value comparison
    // -------------------------------------------------------------------------

    /**
     * Joins two sheets on a composite key and checks a relation between one column on
     * each side. Unlike {@link #checkSet} — which compares value <em>sets</em> per
     * partition — this compares the paired cell values themselves, so it can express
     * "these two services must never both be enabled for the same subscriber".
     *
     * <p>Key values are matched case-insensitively after trimming, consistent with the
     * rest of the framework. When a key maps to several rows on a side, every left
     * value is checked against every right value.
     */
    private List<ValidationError> checkCrossSheetCompare(CrossSheetCompareRule rule, CiqDataStore store) {
        List<ValidationError> errors = new ArrayList<>();

        CrossSheetCompareRule.Side left  = rule.getLeft();
        CrossSheetCompareRule.Side right = rule.getRight();
        if (left == null || right == null) {
            log.warn("cross_sheet_compare: both 'left' and 'right' are required - skipping rule");
            return errors;
        }
        if (left.getSheet() == null || left.getColumn() == null
                || right.getSheet() == null || right.getColumn() == null) {
            log.warn("cross_sheet_compare: each side needs 'sheet' and 'column' - skipping rule");
            return errors;
        }

        CrossSheetCompareRule.Driver driver = rule.getDriver();

        // Canonical key names — used for matching when a side declares none, and for
        // the {ColumnName} placeholders in messages.
        List<String> keyNames = (driver != null && driver.getKeys() != null && !driver.getKeys().isEmpty())
                ? driver.getKeys()
                : (left.getKeys() != null && !left.getKeys().isEmpty() ? left.getKeys() : null);
        if (keyNames == null) {
            log.warn("cross_sheet_compare: no key columns declared (driver.keys or left.keys) - skipping rule");
            return errors;
        }

        List<String> leftKeys  = (left.getKeys()  != null && !left.getKeys().isEmpty())  ? left.getKeys()  : keyNames;
        List<String> rightKeys = (right.getKeys() != null && !right.getKeys().isEmpty()) ? right.getKeys() : keyNames;
        if (leftKeys.size() != keyNames.size() || rightKeys.size() != keyNames.size()) {
            log.warn("cross_sheet_compare: key column lists must be the same length "
                    + "(keys={}, left={}, right={}) - skipping rule", keyNames, leftKeys, rightKeys);
            return errors;
        }

        CrossSheetCompareRule.Relation relation = rule.resolveRelation();
        List<List<String>> pairs = rule.getValuePairs();
        List<String> activeValues = rule.getActiveValues();
        if (relation == CrossSheetCompareRule.Relation.OPPOSITE && (pairs == null || pairs.isEmpty())) {
            log.warn("cross_sheet_compare: relation 'opposite' requires value_pairs - skipping rule");
            return errors;
        }
        if (relation == CrossSheetCompareRule.Relation.NOT_BOTH
                && (activeValues == null || activeValues.isEmpty())) {
            log.warn("cross_sheet_compare: relation 'not_both' requires active_values - skipping rule");
            return errors;
        }

        CiqSheet leftSheet  = getSheet(store, left.getSheet());
        CiqSheet rightSheet = getSheet(store, right.getSheet());
        if (leftSheet == null) {
            log.warn("cross_sheet_compare: sheet '{}' not found - skipping rule", left.getSheet());
            return errors;
        }
        if (rightSheet == null) {
            log.warn("cross_sheet_compare: sheet '{}' not found - skipping rule", right.getSheet());
            return errors;
        }

        Map<List<String>, KeyGroup> leftGroups  = collectKeyGroups(leftSheet,  leftKeys,  left.getColumn());
        Map<List<String>, KeyGroup> rightGroups = collectKeyGroups(rightSheet, rightKeys, right.getColumn());

        // Which keys to check: those enumerated by the driver sheet, or - with no driver -
        // every key seen on either side.
        Map<List<String>, List<String>> keysToCheck = new LinkedHashMap<>();
        if (driver != null && driver.getSheet() != null) {
            CiqSheet driverSheet = getSheet(store, driver.getSheet());
            if (driverSheet == null) {
                log.warn("cross_sheet_compare: driver sheet '{}' not found - skipping rule", driver.getSheet());
                return errors;
            }
            String whereCol = null;
            String whereVal = null;
            if (driver.getWhere() != null) {
                int eq = driver.getWhere().indexOf('=');
                if (eq < 0) {
                    log.warn("cross_sheet_compare: invalid where clause (no '='): {}", driver.getWhere());
                } else {
                    whereCol = driver.getWhere().substring(0, eq).trim();
                    whereVal = driver.getWhere().substring(eq + 1).trim();
                }
            }
            for (CiqRow row : driverSheet.getRows()) {
                if (whereCol != null) {
                    String rv = row.get(whereCol);
                    if (!whereVal.equalsIgnoreCase(rv != null ? rv.trim() : "")) continue;
                }
                List<String> display = keyValues(row, keyNames);
                if (display == null) continue;
                keysToCheck.put(normaliseKey(display), display);
            }
        } else {
            for (Map.Entry<List<String>, KeyGroup> e : leftGroups.entrySet()) {
                keysToCheck.put(e.getKey(), e.getValue().display);
            }
            for (Map.Entry<List<String>, KeyGroup> e : rightGroups.entrySet()) {
                if (!keysToCheck.containsKey(e.getKey())) keysToCheck.put(e.getKey(), e.getValue().display);
            }
        }

        String leftRef  = left.getSheet()  + "." + left.getColumn();
        String rightRef = right.getSheet() + "." + right.getColumn();

        for (Map.Entry<List<String>, List<String>> ke : keysToCheck.entrySet()) {
            List<String> key     = ke.getKey();
            List<String> display = ke.getValue();
            String keyDesc = describeKey(keyNames, display);

            KeyGroup lg = leftGroups.get(key);
            KeyGroup rg = rightGroups.get(key);

            if (lg == null || rg == null) {
                if (rule.resolveOnMissing() == CrossSheetCompareRule.OnMissing.ERROR) {
                    String missingRef = lg == null ? leftRef : rightRef;
                    if (lg == null && rg == null) missingRef = leftRef + " and " + rightRef;
                    int rowNum = lg != null ? lg.rowNumber : (rg != null ? rg.rowNumber : 0);
                    errors.add(new ValidationError(rowNum, keyNames.toString(), keyDesc,
                            "(" + keyDesc + ") has no row in " + missingRef
                            + "; both sides are required (on_missing: error)"));
                }
                continue;
            }

            for (String lv : lg.values) {
                for (String rv : rg.values) {
                    String problem = relationProblem(relation, pairs, activeValues, lv, rv);
                    if (problem == null) continue;
                    String msg = rule.getMessage() != null
                            ? renderMessage(rule.getMessage(), keyNames, display, lv, rv)
                            : "(" + keyDesc + ") " + leftRef + " = '" + lv + "' and "
                              + rightRef + " = '" + rv + "' " + problem;
                    errors.add(new ValidationError(lg.rowNumber, left.getColumn(), lv, msg));
                }
            }
        }

        return errors;
    }

    /**
     * Checks one value pair against the relation.
     *
     * @return {@code null} when the pair is acceptable, otherwise a phrase describing
     *         the violation, ready to append to a generated message
     */
    private String relationProblem(CrossSheetCompareRule.Relation relation,
                                   List<List<String>> pairs, List<String> activeValues,
                                   String lv, String rv) {
        switch (relation) {
            case EQUALS:
                return lv.equalsIgnoreCase(rv) ? null : "must be equal";
            case NOT_EQUALS:
                return lv.equalsIgnoreCase(rv) ? "must not be equal" : null;
            case NOT_BOTH:
                // Mutual exclusion: only both-active is a violation. Both inactive, and
                // one of each, are legitimate.
                return isActive(activeValues, lv) && isActive(activeValues, rv)
                        ? "must not both be active (active values: " + activeValues + ")"
                        : null;
            case OPPOSITE:
            default:
                int li = pairIndexOf(pairs, lv);
                int ri = pairIndexOf(pairs, rv);
                if (li < 0 || ri < 0) {
                    String unknown = li < 0 ? lv : rv;
                    return "must be opposite, but '" + unknown
                           + "' is not one of the declared value_pairs " + pairs;
                }
                if (li != ri) {
                    return "must be opposite, but they come from different value_pairs " + pairs;
                }
                return lv.equalsIgnoreCase(rv) ? "must be opposite (allowed: " + pairs.get(li) + ")" : null;
        }
    }

    /** Whether {@code value} counts as active for {@code relation: not_both}. */
    private boolean isActive(List<String> activeValues, String value) {
        for (String a : activeValues) {
            if (a != null && a.trim().equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    /** Index of the declared pair containing {@code value}, or -1 when no pair holds it. */
    private int pairIndexOf(List<List<String>> pairs, String value) {
        for (int i = 0; i < pairs.size(); i++) {
            List<String> pair = pairs.get(i);
            if (pair == null) continue;
            for (String v : pair) {
                if (v != null && v.trim().equalsIgnoreCase(value)) return i;
            }
        }
        return -1;
    }

    /**
     * Substitutes {@code {KeyColumn}}, {@code {left}} and {@code {right}} in a
     * user-supplied message template.
     */
    private String renderMessage(String template, List<String> keyNames,
                                 List<String> keyValues, String lv, String rv) {
        String out = template;
        for (int i = 0; i < keyNames.size(); i++) {
            out = out.replace("{" + keyNames.get(i) + "}", keyValues.get(i));
        }
        return out.replace("{left}", lv).replace("{right}", rv);
    }

    /**
     * Groups a sheet's rows by composite key, collecting the distinct non-blank values
     * of {@code valueColumn} for each. Rows whose key is entirely blank are ignored.
     */
    private Map<List<String>, KeyGroup> collectKeyGroups(CiqSheet sheet, List<String> keyCols,
                                                         String valueColumn) {
        Map<List<String>, KeyGroup> groups = new LinkedHashMap<>();
        for (CiqRow row : sheet.getRows()) {
            List<String> display = keyValues(row, keyCols);
            if (display == null) continue;
            String val = row.get(valueColumn);
            if (val == null || val.trim().isEmpty()) continue;

            List<String> key = normaliseKey(display);
            KeyGroup g = groups.get(key);
            if (g == null) {
                g = new KeyGroup(display, row.getRowNumber());
                groups.put(key, g);
            }
            g.values.add(val.trim());
        }
        return groups;
    }

    /** Trimmed key cell values for a row, or {@code null} when every component is blank. */
    private List<String> keyValues(CiqRow row, List<String> keyCols) {
        List<String> vals = new ArrayList<>(keyCols.size());
        boolean allBlank = true;
        for (String kc : keyCols) {
            String v = row.get(kc);
            v = v != null ? v.trim() : "";
            if (!v.isEmpty()) allBlank = false;
            vals.add(v);
        }
        return allBlank ? null : vals;
    }

    /** Lower-cased copy of a key, so joins match case-insensitively. */
    private List<String> normaliseKey(List<String> display) {
        List<String> key = new ArrayList<>(display.size());
        for (String v : display) key.add(v.toLowerCase(java.util.Locale.ROOT));
        return key;
    }

    /** Distinct values of the compared column for one key, plus a row number for reporting. */
    private static class KeyGroup {
        final List<String> display;
        final int rowNumber;
        final Set<String> values = new LinkedHashSet<>();

        KeyGroup(List<String> display, int rowNumber) {
            this.display = display;
            this.rowNumber = rowNumber;
        }
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
        String sheetName = parseSheetName(ref, store);
        String colName   = parseColumnName(ref, store);
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
        String sheetName = parseSheetName(ref, store);
        String colName   = parseColumnName(ref, store);
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

    /**
     * Resolves a {@code "Sheet.Column"} reference against sheets that actually exist in
     * {@code store}. Sheet names in this codebase may themselves contain dots (e.g.
     * {@code System.MGCFPoolTbl}), and so may column names (e.g.
     * {@code Record.GoldenCodecSet.GOLDEN_CODEC}), so the reference cannot be split on a
     * fixed dot position. Each dot is tried as a candidate split point, starting from the
     * rightmost (longest possible sheet name) and working left; the first candidate whose
     * left-hand side resolves to a real sheet via {@link CiqDataStore#getSheet} wins.
     * If no candidate resolves, falls back to splitting on the first dot, so a genuinely
     * unresolvable reference still fails the same way it did before this fix (a
     * "sheet/value not found" error downstream) instead of being silently misrouted.
     *
     * @return a 2-element array {@code {sheetName, columnName}}, or {@code null} if
     *         {@code ref} has no dot at all
     */
    private static String[] resolveRef(String ref, CiqDataStore store) {
        if (ref == null || !ref.contains(".")) return null;

        List<Integer> dots = new ArrayList<>();
        for (int i = 0; i < ref.length(); i++) {
            if (ref.charAt(i) == '.') dots.add(i);
        }
        for (int i = dots.size() - 1; i >= 0; i--) {
            int dot = dots.get(i);
            String candidateSheet = ref.substring(0, dot);
            if (candidateSheet.isEmpty()) continue;
            CiqSheet s;
            try {
                s = store.getSheet(candidateSheet);
            } catch (IOException e) {
                continue;
            }
            if (s != null) {
                return new String[]{candidateSheet, ref.substring(dot + 1)};
            }
        }
        // No candidate prefix matched an existing sheet — preserve the original
        // first-dot behavior so an unresolvable reference fails downstream as before.
        int firstDot = ref.indexOf('.');
        return new String[]{ref.substring(0, firstDot), ref.substring(firstDot + 1)};
    }

    /**
     * Resolves the sheet-name portion of a {@code "Sheet.Column"} reference. Exposed
     * (not just used internally) so {@link com.nokia.ciq.validator.CiqValidationEngine}
     * can resolve the same references consistently when deciding whether to skip a
     * {@code workbook_rule} for a missing dependent sheet.
     */
    public static String resolveSheetName(String ref, CiqDataStore store) {
        String[] r = resolveRef(ref, store);
        return r != null ? r[0] : null;
    }

    /** Returns the sheet part of a "Sheet.Column" reference, or {@code null} if absent. */
    private static String parseSheetName(String ref, CiqDataStore store) {
        return resolveSheetName(ref, store);
    }

    /** Returns the column part of a "Sheet.Column" reference, or the whole string if no dot. */
    private static String parseColumnName(String ref, CiqDataStore store) {
        String[] r = resolveRef(ref, store);
        return r != null ? r[1] : ref;
    }
}

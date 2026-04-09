package com.nokia.ciq.processor;

import com.nokia.ciq.processor.reader.InMemoryCiqDataStore;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a YAML-defined JSON template against CIQ data to produce a structured
 * output object.  The engine is fully generic — no column names or sheet names are
 * hardcoded in Java.  All data relationships are expressed in the YAML template.
 *
 * <h3>_each directives</h3>
 * <pre>
 * _each: SheetName
 *     Iterates every row of SheetName.  Inside the block, plain strings
 *     resolve as column names in the current row.
 *
 * _each: "SheetName WHERE [Sheet.]Col = value"
 *     Same as above but only rows where Col equals the resolved value.
 *     value can be a $variable, Sheet.Col reference, current-row column, or literal.
 *
 * _each: "DISTINCT Sheet.Column AS $varname"
 *     Iterates distinct (deduplicated) values of Column from Sheet.
 *     Sets $varname to each value in the child context.
 *     The source sheet's filtered rows are scoped to the current value so
 *     that Sheet.AnyColumn lookups automatically return per-value data.
 *     "AS $varname" is optional; defaults to $item when omitted.
 * </pre>
 *
 * <h3>Value resolution</h3>
 * <pre>
 * $varname              current value of a named variable (set by DISTINCT or withXxx)
 * Sheet.Column          first non-blank value from the (scoped) Sheet rows
 * "Sheet.Col WHERE ..."  relational lookup
 * plain string          column name (inside _each row block) or static literal
 * </pre>
 */
public class JsonTemplateEvaluator {

    public Object evaluate(Map<String, Object> template, TemplateContext ctx) {
        return buildObject(template, ctx);
    }

    // -------------------------------------------------------------------------
    // Core resolve / build
    // -------------------------------------------------------------------------

    private Object resolveValue(Object value, TemplateContext ctx) {
        if (value instanceof String) return resolveString((String) value, ctx);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return map.containsKey("_each") ? buildArray(map, ctx) : buildObject(map, ctx);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) result.add(resolveValue(item, ctx));
            return result;
        }
        return value;
    }

    private Object resolveString(String value, TemplateContext ctx) {
        if (value.startsWith("$")) return resolveVariable(value.substring(1), ctx);

        int whereIdx = value.toUpperCase().indexOf(" WHERE ");
        if (whereIdx >= 0) return resolveWhereExpr(value, whereIdx, ctx);

        if (value.contains(".")) {
            int dot = value.indexOf('.');
            return firstNonBlank(value.substring(0, dot), value.substring(dot + 1), ctx);
        }
        if (ctx.currentRow != null) return ctx.currentRow.get(value);
        return value;
    }

    private Object resolveWhereExpr(String expr, int whereIdx, TemplateContext ctx) {
        String targetExpr = expr.substring(0, whereIdx).trim();
        String condExpr   = expr.substring(whereIdx + 7).trim();

        int dot = targetExpr.indexOf('.');
        if (dot < 0) return null;
        String sheet     = targetExpr.substring(0, dot).trim();
        String targetCol = stripQuotes(targetExpr.substring(dot + 1).trim());

        int eqIdx = condExpr.indexOf('=');
        if (eqIdx < 0) return null;
        String condLeft  = condExpr.substring(0, eqIdx).trim();
        String condRight = condExpr.substring(eqIdx + 1).trim();

        String filterCol = condLeft.contains(".")
                ? stripQuotes(condLeft.substring(condLeft.indexOf('.') + 1).trim())
                : stripQuotes(condLeft);

        Object filterVal = resolveString(condRight, ctx);
        if (filterVal == null) return null;
        String filterValStr = filterVal.toString();

        for (CiqRow row : resolveRows(sheet, ctx)) {
            if (filterValStr.equals(row.get(filterCol))) {
                String v = row.get(targetCol);
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        }
        return null;
    }

    private Object resolveVariable(String name, TemplateContext ctx) {
        // Generic variable map first — set by DISTINCT or withVar
        String v = ctx.vars.get(name);
        if (v != null) return v;
        // Legacy specific fields for backward compatibility
        switch (name) {
            case "node":        return ctx.currentNode;
            case "niamID":      return ctx.currentNiamId;
            case "group":       return ctx.currentGroup;
            case "nodes":       return ctx.currentGroupNodes;
            case "niamMapping": return ctx.currentGroupNiam;
            case "cr":          return ctx.currentCr;
            default:            return null;
        }
    }

    private Map<String, Object> buildObject(Map<String, Object> template, TemplateContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : template.entrySet()) {
            result.put(e.getKey(), resolveValue(e.getValue(), ctx));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Array builders
    // -------------------------------------------------------------------------

    private List<Object> buildArray(Map<String, Object> template, TemplateContext ctx) {
        String each = String.valueOf(template.get("_each"));
        Map<String, Object> elemTemplate = new LinkedHashMap<>(template);
        elemTemplate.remove("_each");

        // ── Legacy: _each: nodes / groups ───────────────────────────────────
        if ("nodes".equals(each))  return buildNodeArray(elemTemplate, ctx);
        if ("groups".equals(each)) return buildGroupArray(elemTemplate, ctx);

        // ── DISTINCT Sheet.Column [AS $varname] ──────────────────────────────
        // Iterates distinct values of Column, scopes the source sheet per value.
        // "AS $varname" is optional; $item is used when absent.
        if (each.toUpperCase().startsWith("DISTINCT ")) {
            String rest = each.substring(9).trim();
            String ref, varName;
            int asIdx = rest.toUpperCase().indexOf(" AS ");
            if (asIdx >= 0) {
                ref     = rest.substring(0, asIdx).trim();
                varName = rest.substring(asIdx + 4).trim();
                if (varName.startsWith("$")) varName = varName.substring(1);
            } else {
                ref     = rest;
                varName = "item";
            }
            int dot = ref.indexOf('.');
            String srcSheet = dot >= 0 ? ref.substring(0, dot).trim() : ref;
            String srcCol   = dot >= 0 ? ref.substring(dot + 1).trim() : ref;
            return buildDistinctArray(elemTemplate, ctx, srcSheet, srcCol, varName);
        }

        // ── SheetName WHERE FilterCol = value ────────────────────────────────
        int whereIdx = each.toUpperCase().indexOf(" WHERE ");
        if (whereIdx >= 0) {
            String sheetName = each.substring(0, whereIdx).trim();
            String condition = each.substring(whereIdx + 7).trim();
            return buildFilteredSheetArray(sheetName, condition, elemTemplate, ctx);
        }

        // ── Plain sheet name ─────────────────────────────────────────────────
        return buildSheetArray(each, elemTemplate, ctx);
    }

    /**
     * Generic distinct-value iterator.  Collects unique values of {@code srcCol}
     * from {@code srcSheet}, then for each value:
     * <ul>
     *   <li>Scopes {@code filteredRows[srcSheet]} to only matching rows so that
     *       cross-sheet lookups like {@code srcSheet.AnyColumn} return per-value data.</li>
     *   <li>Sets {@code currentRow} to the first matching row so bare column-name
     *       strings also resolve correctly inside the element template.</li>
     *   <li>Sets {@code $varName} in the context for use as {@code $varName} in values.</li>
     * </ul>
     */
    private List<Object> buildDistinctArray(Map<String, Object> elemTemplate,
                                             TemplateContext ctx,
                                             String srcSheet, String srcCol, String varName) {
        List<String> values = new ArrayList<>();
        for (CiqRow row : resolveRows(srcSheet, ctx)) {
            String v = row.get(srcCol);
            if (v != null && !v.trim().isEmpty() && !values.contains(v.trim())) {
                values.add(v.trim());
            }
        }

        List<Object> result = new ArrayList<>();
        for (String value : values) {
            Map<String, List<CiqRow>> scoped =
                    scopeSheetByColumn(ctx.filteredRows, srcSheet, srcCol, value);

            CiqRow firstRow = null;
            for (CiqRow row : resolveRows(srcSheet, ctx)) {
                if (value.equals(row.get(srcCol))) { firstRow = row; break; }
            }

            TemplateContext child = ctx.withVar(varName, value, scoped);
            if (firstRow != null) child = child.withRow(firstRow);
            result.add(buildObject(elemTemplate, child));
        }
        return result;
    }

    private List<Object> buildFilteredSheetArray(String sheetName, String condition,
                                                   Map<String, Object> elemTemplate,
                                                   TemplateContext ctx) {
        int eqIdx = condition.indexOf('=');
        if (eqIdx < 0) return buildSheetArray(sheetName, elemTemplate, ctx);

        String filterColExpr = condition.substring(0, eqIdx).trim();
        String filterValExpr = condition.substring(eqIdx + 1).trim();

        String filterCol = filterColExpr.contains(".")
                ? filterColExpr.substring(filterColExpr.indexOf('.') + 1).trim()
                : filterColExpr;

        Object filterVal = resolveString(filterValExpr, ctx);
        if (filterVal == null) return Collections.emptyList();
        String filterValStr = filterVal.toString();

        List<Object> result = new ArrayList<>();
        for (CiqRow row : resolveRows(sheetName, ctx)) {
            if (filterValStr.equals(row.get(filterCol))) {
                result.add(buildObject(elemTemplate, ctx.withRow(row)));
            }
        }
        return result;
    }

    /** Legacy: one element per node in the NIAM mapping. */
    private List<Object> buildNodeArray(Map<String, Object> elemTemplate, TemplateContext ctx) {
        List<Object> result = new ArrayList<>();
        for (Map.Entry<String, String> e : ctx.niamMapping.entrySet()) {
            String node   = e.getKey();
            String niamId = e.getValue();
            String group  = ctx.nodeToGroup != null ? ctx.nodeToGroup.get(node) : null;
            TemplateContext child = ctx.withNode(node, niamId, group,
                    scopeFilteredRows(ctx.filteredRows, group, null));
            result.add(buildObject(elemTemplate, child));
        }
        return result;
    }

    /** Legacy: one element per group derived from nodeToGroup. */
    private List<Object> buildGroupArray(Map<String, Object> elemTemplate, TemplateContext ctx) {
        Map<String, List<String>> groupToNodes = new LinkedHashMap<>();
        if (ctx.nodeToGroup != null) {
            for (Map.Entry<String, String> e : ctx.nodeToGroup.entrySet()) {
                groupToNodes.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            }
        }
        List<Object> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> ge : groupToNodes.entrySet()) {
            String group       = ge.getKey();
            List<String> nodes = ge.getValue();
            Map<String, String> groupNiam = new LinkedHashMap<>();
            for (String n : nodes) {
                String id = ctx.niamMapping.get(n);
                if (id != null) groupNiam.put(n, id);
            }
            TemplateContext child = ctx.withGroup(group, nodes, groupNiam,
                    scopeFilteredRows(ctx.filteredRows, group, null));
            result.add(buildObject(elemTemplate, child));
        }
        return result;
    }

    private List<Object> buildSheetArray(String sheetName, Map<String, Object> elemTemplate,
                                          TemplateContext ctx) {
        List<Object> result = new ArrayList<>();
        for (CiqRow row : resolveRows(sheetName, ctx)) {
            result.add(buildObject(elemTemplate, ctx.withRow(row)));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String firstNonBlank(String sheetName, String col, TemplateContext ctx) {
        String normalizedCol = stripQuotes(col);
        for (CiqRow row : resolveRows(sheetName, ctx)) {
            String v = row.get(normalizedCol);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    private List<CiqRow> resolveRows(String sheetName, TemplateContext ctx) {
        for (Map.Entry<String, List<CiqRow>> e : ctx.filteredRows.entrySet()) {
            if (e.getKey().equalsIgnoreCase(sheetName)) return e.getValue();
        }
        CiqSheet sheet = ctx.store.getSheet(sheetName);
        return sheet != null ? sheet.getRows() : Collections.emptyList();
    }

    /**
     * Scopes {@code filteredRows[srcSheet]} to rows where {@code srcCol} equals
     * {@code value}.  All other sheets are left unchanged so they remain fully
     * accessible for WHERE-based lookups in the element template.
     */
    private Map<String, List<CiqRow>> scopeSheetByColumn(
            Map<String, List<CiqRow>> filteredRows,
            String srcSheet, String srcCol, String value) {
        Map<String, List<CiqRow>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<CiqRow>> e : filteredRows.entrySet()) {
            if (e.getKey().equalsIgnoreCase(srcSheet)) {
                List<CiqRow> scoped = new ArrayList<>();
                for (CiqRow row : e.getValue()) {
                    if (value.equals(row.get(srcCol))) scoped.add(row);
                }
                result.put(e.getKey(), scoped);
            } else {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    /** Legacy: scope rows by group or node name. */
    private Map<String, List<CiqRow>> scopeFilteredRows(Map<String, List<CiqRow>> filteredRows,
                                                          String group, String node) {
        if (group == null && node == null) return filteredRows;
        Map<String, List<CiqRow>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<CiqRow>> e : filteredRows.entrySet()) {
            List<CiqRow> rows = new ArrayList<>();
            for (CiqRow row : e.getValue()) {
                if (group != null && group.equals(row.get("Group"))) { rows.add(row); continue; }
                if (node  != null && node.equals(row.get("Node")))   { rows.add(row); }
            }
            result.put(e.getKey(), rows);
        }
        return result;
    }

    private static String stripQuotes(String s) {
        if (s != null && s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    // =========================================================================
    // Evaluation context
    // =========================================================================

    public static class TemplateContext {

        public final InMemoryCiqDataStore store;
        public final Map<String, List<CiqRow>> filteredRows;
        public final Map<String, String> niamMapping;
        public final Map<String, String> nodeToGroup;

        /** Generic named variables set by {@code DISTINCT … AS $varname} or withVar(). */
        public final Map<String, String> vars;

        // Legacy iteration state — kept for backward compatibility with _each: nodes/groups
        public final String              currentNode;
        public final String              currentNiamId;
        public final String              currentGroup;
        public final List<String>        currentGroupNodes;
        public final Map<String, String> currentGroupNiam;
        public final String              currentCr;
        public final CiqRow              currentRow;

        public TemplateContext(InMemoryCiqDataStore store,
                               Map<String, List<CiqRow>> filteredRows,
                               Map<String, String> niamMapping,
                               Map<String, String> nodeToGroup) {
            this.store             = store;
            this.filteredRows      = filteredRows;
            this.niamMapping       = niamMapping  != null ? niamMapping  : Collections.emptyMap();
            this.nodeToGroup       = nodeToGroup;
            this.vars              = Collections.emptyMap();
            this.currentNode       = null;
            this.currentNiamId     = null;
            this.currentGroup      = null;
            this.currentGroupNodes = null;
            this.currentGroupNiam  = null;
            this.currentCr         = null;
            this.currentRow        = null;
        }

        private TemplateContext(TemplateContext base,
                                String node, String niamId,
                                String group, List<String> groupNodes, Map<String, String> groupNiam,
                                String cr,
                                CiqRow row,
                                Map<String, List<CiqRow>> filteredRows,
                                Map<String, String> vars) {
            this.store             = base.store;
            this.filteredRows      = filteredRows != null ? filteredRows : base.filteredRows;
            this.niamMapping       = base.niamMapping;
            this.nodeToGroup       = base.nodeToGroup;
            this.vars              = vars != null ? vars : base.vars;
            this.currentNode       = node;
            this.currentNiamId     = niamId;
            this.currentGroup      = group;
            this.currentGroupNodes = groupNodes;
            this.currentGroupNiam  = groupNiam;
            this.currentCr         = cr;
            this.currentRow        = row;
        }

        /** Sets a named variable {@code $name = value} and optionally scopes filteredRows. */
        public TemplateContext withVar(String name, String value,
                                       Map<String, List<CiqRow>> scoped) {
            Map<String, String> newVars = new LinkedHashMap<>(this.vars);
            newVars.put(name, value);
            return new TemplateContext(this,
                    currentNode, currentNiamId, currentGroup,
                    currentGroupNodes, currentGroupNiam, currentCr,
                    currentRow, scoped, newVars);
        }

        public TemplateContext withNode(String node, String niamId, String group,
                                        Map<String, List<CiqRow>> scoped) {
            Map<String, String> newVars = new LinkedHashMap<>(this.vars);
            newVars.put("node",   node);
            if (niamId != null) newVars.put("niamID", niamId);
            if (group  != null) newVars.put("group",  group);
            return new TemplateContext(this, node, niamId, group, null, null,
                    this.currentCr, null, scoped, newVars);
        }

        public TemplateContext withGroup(String group, List<String> nodes,
                                         Map<String, String> niam,
                                         Map<String, List<CiqRow>> scoped) {
            Map<String, String> newVars = new LinkedHashMap<>(this.vars);
            newVars.put("group", group);
            return new TemplateContext(this, null, null, group, nodes, niam,
                    this.currentCr, null, scoped, newVars);
        }

        public TemplateContext withCr(String cr, Map<String, List<CiqRow>> crScoped) {
            Map<String, String> newVars = new LinkedHashMap<>(this.vars);
            newVars.put("cr", cr);
            return new TemplateContext(this, null, null, null, null, null,
                    cr, null, crScoped, newVars);
        }

        public TemplateContext withRow(CiqRow row) {
            return new TemplateContext(this,
                    currentNode, currentNiamId, currentGroup,
                    currentGroupNodes, currentGroupNiam,
                    currentCr, row, null, this.vars);
        }
    }
}

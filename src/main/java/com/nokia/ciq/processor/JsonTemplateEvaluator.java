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
 * Evaluates a YAML-defined JSON template against CIQ workbook data to produce
 * a structured JSON-serialisable object.
 *
 * <p>The engine is fully generic — no sheet names, column names, or variable
 * names are hardcoded.  All data relationships are expressed entirely in the
 * YAML template using the directives below.
 *
 * <h3>_each directives (array generation)</h3>
 * <pre>
 * _each: SheetName
 *     One element per row of SheetName.
 *     Inside the block, plain strings resolve as column names in the current row.
 *
 * _each: "[Sheet.Column |] Sheet WHERE [Sheet.]Col = value"
 *     One element per row of Sheet where Col equals the resolved value.
 *     An optional "Sheet.Column" prefix (column hint) is stripped — only the
 *     sheet name is used.
 *     value may be a $variable, Sheet.Col reference, current-row column, or literal.
 *
 * _each: "DISTINCT [FROM] Sheet.Column [AS $varname]"
 *     One element per distinct value of Column in Sheet.
 *     Sets $varname (default: $item) in the child context.
 *     Scopes Sheet's rows to the current value so Sheet.AnyCol lookups
 *     automatically return per-value data.
 *     Sets currentRow to the first matching row so bare column names also resolve.
 * </pre>
 *
 * <h3>Value resolution (in order)</h3>
 * <pre>
 * $varname              — named variable from the current context (set by DISTINCT)
 * "Sheet.Col WHERE …"   — relational lookup returning first matching non-blank value
 * Sheet.Column          — first non-blank value from (scoped) Sheet rows
 * plain string          — column name when inside a row block; static literal otherwise
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
        if (value.startsWith("$")) return ctx.vars.get(value.substring(1));

        int whereIdx = value.toUpperCase().indexOf(" WHERE ");
        if (whereIdx >= 0) return resolveWhereExpr(value, whereIdx, ctx);

        if (value.contains(".")) {
            int dot = value.indexOf('.');
            return firstNonBlank(value.substring(0, dot).trim(),
                                 value.substring(dot + 1).trim(), ctx);
        }
        if (ctx.currentRow != null) return ctx.currentRow.get(value);
        return value;
    }

    /**
     * Resolves: {@code Sheet.Column WHERE [Sheet.]FilterCol = value}
     * Returns the first non-blank Column value in Sheet where FilterCol matches.
     */
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

        // ── DISTINCT [FROM] Sheet.Column [AS $varname] ──────────────────────
        if (each.toUpperCase().startsWith("DISTINCT ")) {
            String rest = each.substring(9).trim();
            if (rest.toUpperCase().startsWith("FROM ")) rest = rest.substring(5).trim();
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

        // ── [Sheet.Column |] Sheet WHERE FilterCol = value ───────────────────
        int whereIdx = each.toUpperCase().indexOf(" WHERE ");
        if (whereIdx >= 0) {
            String sheetPart = each.substring(0, whereIdx).trim();
            if (sheetPart.contains("."))
                sheetPart = sheetPart.substring(0, sheetPart.indexOf('.')).trim();
            String condition = each.substring(whereIdx + 7).trim();
            return buildFilteredSheetArray(sheetPart, condition, elemTemplate, ctx);
        }

        // ── Plain sheet name ─────────────────────────────────────────────────
        return buildSheetArray(each, elemTemplate, ctx);
    }

    /**
     * One element per distinct value of {@code srcCol} in {@code srcSheet}.
     * Scopes the source sheet's rows per value and sets {@code $varName}.
     */
    private List<Object> buildDistinctArray(Map<String, Object> elemTemplate,
                                             TemplateContext ctx,
                                             String srcSheet, String srcCol, String varName) {
        List<String> values = new ArrayList<>();
        for (CiqRow row : resolveRows(srcSheet, ctx)) {
            String v = row.get(srcCol);
            if (v != null && !v.trim().isEmpty() && !values.contains(v.trim()))
                values.add(v.trim());
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

    /** One element per row of {@code sheetName} matching the filter condition. */
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
            if (filterValStr.equals(row.get(filterCol)))
                result.add(buildObject(elemTemplate, ctx.withRow(row)));
        }
        return result;
    }

    /** One element per row of {@code sheetName}. */
    private List<Object> buildSheetArray(String sheetName, Map<String, Object> elemTemplate,
                                          TemplateContext ctx) {
        List<Object> result = new ArrayList<>();
        for (CiqRow row : resolveRows(sheetName, ctx))
            result.add(buildObject(elemTemplate, ctx.withRow(row)));
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
     * Returns a copy of {@code filteredRows} where the named sheet's list is
     * restricted to rows where {@code col} equals {@code value}.
     * All other sheets are left unchanged.
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

    private static String stripQuotes(String s) {
        if (s != null && s.length() >= 2
                && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'')
            return s.substring(1, s.length() - 1);
        return s;
    }

    // =========================================================================
    // Evaluation context
    // =========================================================================

    /**
     * Immutable context passed through each recursive evaluation step.
     *
     * <p>Contains only generic, domain-agnostic state:
     * <ul>
     *   <li>{@code store} — read-only access to all workbook sheets</li>
     *   <li>{@code filteredRows} — current in-scope rows per sheet</li>
     *   <li>{@code vars} — named template variables ({@code $varname → value})</li>
     *   <li>{@code currentRow} — active row during sheet iteration</li>
     * </ul>
     */
    public static class TemplateContext {

        public final InMemoryCiqDataStore      store;
        public final Map<String, List<CiqRow>> filteredRows;
        /** Named template variables: {@code $varname → value} (String, List, or Map). */
        public final Map<String, Object>       vars;
        /** Active row during {@code _each: Sheet} iteration; enables bare column-name resolution. */
        public final CiqRow                    currentRow;

        public TemplateContext(InMemoryCiqDataStore store,
                               Map<String, List<CiqRow>> filteredRows) {
            this.store        = store;
            this.filteredRows = filteredRows != null ? filteredRows : Collections.emptyMap();
            this.vars         = Collections.emptyMap();
            this.currentRow   = null;
        }

        private TemplateContext(TemplateContext base,
                                Map<String, Object> vars,
                                Map<String, List<CiqRow>> filteredRows,
                                CiqRow row) {
            this.store        = base.store;
            this.filteredRows = filteredRows != null ? filteredRows : base.filteredRows;
            this.vars         = vars         != null ? vars         : base.vars;
            this.currentRow   = row;
        }

        /** Returns a new context with {@code $name} set to {@code value}. */
        public TemplateContext withVar(String name, Object value,
                                       Map<String, List<CiqRow>> scoped) {
            Map<String, Object> newVars = new LinkedHashMap<>(this.vars);
            newVars.put(name, value);
            return new TemplateContext(this, newVars, scoped, this.currentRow);
        }

        /** Returns a new context with a replaced vars map. */
        public TemplateContext withVars(Map<String, Object> newVars,
                                        Map<String, List<CiqRow>> scoped) {
            return new TemplateContext(this, newVars, scoped, this.currentRow);
        }

        /** Returns a new context with {@code currentRow} set for column-name resolution. */
        public TemplateContext withRow(CiqRow row) {
            return new TemplateContext(this, this.vars, null, row);
        }
    }
}

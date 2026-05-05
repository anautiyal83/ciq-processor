package com.nokia.ciq.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nokia.ciq.processor.model.GroupIndex;
import com.nokia.ciq.processor.reader.InMemoryCiqDataStore;
import com.nokia.ciq.processor.reader.InMemoryExcelReader;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.validator.CiqValidationEngine;
import com.nokia.ciq.validator.config.JsonOutputLoader;
import com.nokia.ciq.validator.config.ValidationRulesConfig;
import com.nokia.ciq.validator.config.ValidationRulesLoader;
import com.nokia.ciq.validator.model.ValidationReport;
import com.nokia.ciq.validator.config.ReportOutputConfig;
import com.nokia.ciq.validator.report.ExcelReportWriter;
import com.nokia.ciq.validator.report.HtmlReportWriter;
import com.nokia.ciq.validator.report.HtmlTemplateReportWriter;
import com.nokia.ciq.validator.report.ReportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link CiqProcessor}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link ValidationRulesLoader} loads YAML validation rules.</li>
 *   <li>{@link InMemoryExcelReader} reads the CIQ Excel workbook into an
 *       {@link InMemoryCiqDataStore}.</li>
 *   <li>{@link CiqValidationEngine} validates the in-memory data.</li>
 *   <li>Reports are written in the requested formats (JSON, HTML, Excel).</li>
 *   <li>When validation passes and {@code jsonOutputDir} is set, JSON output
 *       files are produced according to the {@code json_output} block in the rules YAML.</li>
 * </ol>
 *
 * <h2>JSON output modes</h2>
 * <h3>output_mode: single</h3>
 * <p>One JSON file for the entire workbook.  The {@code data} template is evaluated
 * with all rows from every sheet available in the context.
 *
 * <h3>output_mode: individual</h3>
 * <p>One JSON file per distinct value of the configured {@code segregate_by} column.
 * The YAML must include a {@code segregate_by} block specifying the sheet and column
 * that drives file splitting:
 * <pre>
 * json_output:
 *   output_mode: individual
 *   segregate_by:
 *     sheet:  Index          # any sheet in the workbook
 *     column: CRGroup        # any column in that sheet
 *     as:     $cr            # variable injected into the template (default: $unit)
 *   data:
 *     crGroup: $cr
 *     ...
 * </pre>
 * <p>The template is evaluated once per distinct value with the segregation sheet
 * pre-scoped to matching rows.  All other sheets remain fully accessible so the
 * template can cross-reference freely via WHERE conditions.
 */
public class CiqProcessorImpl implements CiqProcessor {

    private static final Logger log = LoggerFactory.getLogger(CiqProcessorImpl.class);

    private final ObjectMapper mapper;

    public CiqProcessorImpl() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public ValidationReport process(String ciqFilePath,
                                    String nodeType,
                                    String activity,
                                    String rulesFilePath,
                                    String outputDir,
                                    String formatCsv,
                                    String jsonOutputDir,
                                    String jsonOutputConfigPath,
                                    String reportTemplateName,
                                    String reportTemplatePath) throws IOException {

        List<ReportFormat> formats = ReportFormat.parseList(
                formatCsv != null ? formatCsv : "JSON,HTML,MSEXCEL");

        log.info("=== CIQ Processor ===");
        log.info("CIQ file:  {}", ciqFilePath);
        log.info("Node type: {}, Activity: {}", nodeType, activity);
        log.info("Rules:     {}", rulesFilePath);
        log.info("Formats:   {}", formats);

        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        // Step 1: Load validation rules
        ValidationRulesConfig rules = new ValidationRulesLoader().load(rulesFilePath);

        // Step 2: Read Excel into memory
        InMemoryCiqDataStore store = new InMemoryExcelReader()
                .read(ciqFilePath, nodeType, activity, rules);

        // Step 3: Validate
        ValidationReport report =
                new CiqValidationEngine(store, rules).validate(nodeType, activity);

        // Step 4: Write validation reports
        ReportOutputConfig reportOutputConfig = rules.getReportOutput();
        String baseName = resolveReportBaseName(reportOutputConfig, nodeType, activity);
        String htmlTemplatePath = buildTemplatePath(reportTemplateName, reportTemplatePath);

        for (ReportFormat fmt : formats) {
            String fileName = baseName + "." + fmt.extension();
            String path     = new File(outDir, fileName).getAbsolutePath();
            report.getParameters().put("REPORT_FILENAME_" + fmt.name(), fileName);
            switch (fmt) {
                case JSON:
                    mapper.writeValue(new File(path), report);
                    log.info("JSON report:  {}", path);
                    break;
                case HTML:
                    if (htmlTemplatePath != null && !htmlTemplatePath.trim().isEmpty()) {
                        new HtmlTemplateReportWriter().write(report, path, htmlTemplatePath);
                        log.info("HTML report (template):  {}", path);
                    } else {
                        new HtmlReportWriter().write(report, path);
                        log.info("HTML report:  {}", path);
                    }
                    break;
                case MSEXCEL:
                    new ExcelReportWriter().write(report, path);
                    log.info("Excel report: {}", path);
                    break;
            }
        }

        // Step 5: JSON output — only when validation passed and both dirs are provided
        if ("PASSED".equals(report.getStatus())
                && jsonOutputDir != null && jsonOutputConfigPath != null) {
            File mopOutDir = new File(jsonOutputDir);
            if (!mopOutDir.exists()) mopOutDir.mkdirs();

            log.info("JSON output config: {}", jsonOutputConfigPath);
            Map<String, Object> jsonOutputConfig = new JsonOutputLoader().load(jsonOutputConfigPath);

            OutputMode outputMode = resolveOutputMode(jsonOutputConfig);
            Map<String, Object> template = resolveTemplate(jsonOutputConfig);

            if (outputMode == OutputMode.SINGLE) {
                log.info("output_mode=single → {}", jsonOutputDir);
                String jsonFileName = segregateSingle(store, nodeType, activity, jsonOutputDir, template);
                if (jsonFileName != null) {
                    report.getParameters().put("JSON_FILE_NAME", jsonFileName);
                }
            } else {
                // INDIVIDUAL — driven entirely by 'segregate_by' in the json-output config
                SegregateByConfig seg = resolveSegregateBy(jsonOutputConfig);
                if (seg == null) {
                    log.warn("output_mode=individual requires a 'segregate_by' block in the json-output config — skipping JSON output");
                } else {
                    log.info("output_mode=individual — segregating by {}.{} (${}) → {}",
                            seg.sheet, seg.column, seg.varName, jsonOutputDir);
                    String jsonFileName = segregateIndividual(store, nodeType, activity, jsonOutputDir, template, seg);
                    if (jsonFileName != null) {
                        report.getParameters().put("JSON_FILE_NAME", jsonFileName);
                    }
                }
            }
        }

        return report;
    }

    // =========================================================================
    // Report output resolution
    // =========================================================================

    /**
     * Returns the base name (without extension) for validation report files.
     * Uses the {@code filename} field from {@code report_output} if configured.
     * Falls back to {@code {nodeType}_{activity}_VALIDATION_REPORT}.
     * Both {@code {nodeType}} and {@code {activity}} placeholders are resolved.
     */
    private static String resolveReportBaseName(ReportOutputConfig cfg,
                                                 String nodeType,
                                                 String activity) {
        String tmpl = (cfg != null && cfg.getFilename() != null && !cfg.getFilename().trim().isEmpty())
                ? cfg.getFilename().trim()
                : "{nodeType}_{activity}_VALIDATION_REPORT";
        return resolvePlaceholders(tmpl, nodeType, activity);
    }

    /**
     * Combines {@code reportTemplatePath} and {@code reportTemplateName} into a full path.
     * Returns {@code null} when name is absent, so the built-in HTML writer is used.
     * When path is absent, name is treated as the complete file path.
     */
    private static String buildTemplatePath(String reportTemplateName, String reportTemplatePath) {
        if (reportTemplateName == null || reportTemplateName.trim().isEmpty()) return null;
        if (reportTemplatePath == null || reportTemplatePath.trim().isEmpty())
            return reportTemplateName.trim();
        return reportTemplatePath.trim().replaceAll("[/\\\\]+$", "") + "/" + reportTemplateName.trim();
    }

    /**
     * Replaces {@code {nodeType}} and {@code {activity}} in {@code template}
     * with the upper-cased node type and activity strings.
     */
    private static String resolvePlaceholders(String template, String nodeType, String activity) {
        return template
                .replace("{nodeType}", nodeType.toUpperCase())
                .replace("{activity}", activity.toUpperCase());
    }

    // =========================================================================
    // SINGLE output — one file for the entire workbook
    // =========================================================================

    private String segregateSingle(InMemoryCiqDataStore store,
                                   String nodeType,
                                   String activity,
                                   String outputDir,
                                   Map<String, Object> template) throws IOException {
        if (template == null || template.isEmpty()) {
            log.warn("output_mode=single: no 'data' template in json_output — skipping");
            return null;
        }
        JsonTemplateEvaluator.TemplateContext ctx =
                new JsonTemplateEvaluator.TemplateContext(store, buildAllRows(store));
        Object json = new JsonTemplateEvaluator().evaluate(template, ctx);
        fillNullEmailsByGroup(json);
        String fileName = nodeType.toUpperCase() + "_" + activity.toUpperCase() + ".json";
        mapper.writeValue(new File(outputDir, fileName), json);
        log.info("Single JSON → {}", fileName);
        return fileName;
    }

    // =========================================================================
    // INDIVIDUAL output — one file per distinct value of segregate_by column
    // =========================================================================

    private String segregateIndividual(InMemoryCiqDataStore store,
                                       String nodeType,
                                       String activity,
                                       String outputDir,
                                       Map<String, Object> template,
                                       SegregateByConfig seg) throws IOException {
        List<String> values = distinctValues(store, seg.sheet, seg.column);
        if (values.isEmpty()) {
            log.warn("segregate_by: no distinct values found in {}.{} — skipping", seg.sheet, seg.column);
            return null;
        }

        List<String> fileNames = new ArrayList<>();
        for (String value : values) {
            // Scope the segregation sheet to rows matching this value.
            // All other sheets are NOT in scopedRows — the template engine falls back
            // to store.getSheet() for unfiltered access when a sheet isn't in filteredRows.
            Map<String, List<CiqRow>> scopedRows = filterSheetByColumn(store, seg.sheet, seg.column, value);

            Object json;
            if (template != null && !template.isEmpty()) {
                JsonTemplateEvaluator.TemplateContext ctx =
                        new JsonTemplateEvaluator.TemplateContext(store, scopedRows)
                                .withVar(seg.varName, value, scopedRows);
                json = new JsonTemplateEvaluator().evaluate(template, ctx);
                fillNullEmailsByGroup(json);
            } else {
                // No template: produce a minimal generic JSON of all sheets with full row data
                json = buildDefaultJson(store, nodeType, activity, seg.column, value);
            }

            String fileName = nodeType.toUpperCase() + "_" + activity.toUpperCase()
                    + "_" + sanitize(value) + ".json";
            mapper.writeValue(new File(outputDir, fileName), json);
            log.info("  [{}.{}={}] → {}", seg.sheet, seg.column, value, fileName);
            fileNames.add(fileName);
        }
        return String.join(",", fileNames);
    }

    // =========================================================================
    // YAML config resolution
    // =========================================================================

    private OutputMode resolveOutputMode(Map<String, Object> jsonOutputConfig) {
        if (jsonOutputConfig == null) return OutputMode.INDIVIDUAL;
        Object mode = jsonOutputConfig.get("output_mode");
        return OutputMode.fromString(mode != null ? mode.toString() : null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveTemplate(Map<String, Object> jsonOutputConfig) {
        if (jsonOutputConfig == null) return null;
        Object data = jsonOutputConfig.get("data");
        return (data instanceof Map) ? (Map<String, Object>) data : null;
    }

    /**
     * Reads the {@code segregate_by} block from the json-output config map.
     * Returns {@code null} when absent or incomplete.
     *
     * <pre>
     * segregate_by:
     *   sheet:  Index      # required — sheet containing the segregation key column
     *   column: CRGroup    # required — one output file per distinct value of this column
     *   as:     $cr        # optional — variable name injected into the template (default: $unit)
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private SegregateByConfig resolveSegregateBy(Map<String, Object> jsonOutputConfig) {
        if (jsonOutputConfig == null) return null;
        Object seg = jsonOutputConfig.get("segregate_by");
        if (!(seg instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) seg;

        String sheet  = (String) m.get("sheet");
        String column = (String) m.get("column");
        if (sheet == null || column == null) {
            log.warn("segregate_by: 'sheet' and 'column' are required — skipping");
            return null;
        }
        String as      = m.containsKey("as") ? String.valueOf(m.get("as")) : "$unit";
        String varName = as.startsWith("$") ? as.substring(1) : as;
        return new SegregateByConfig(sheet, column, varName);
    }

    // =========================================================================
    // Row helpers
    // =========================================================================

    /**
     * Builds an unfiltered row map containing every sheet in the store.
     * Used by SINGLE mode so the template engine sees the full workbook.
     */
    private Map<String, List<CiqRow>> buildAllRows(InMemoryCiqDataStore store) {
        Map<String, List<CiqRow>> out = new LinkedHashMap<>();
        for (String name : store.getAvailableSheets()) {
            CiqSheet s = store.getSheet(name);
            if (s != null) out.put(name, s.getRows());
        }
        if (store.getRawIndexSheet() != null)
            out.put("Index", store.getRawIndexSheet().getRows());
        return out;
    }

    /**
     * Returns a row map where only {@code sheetName} is present and filtered to rows
     * whose {@code column} value equals {@code value}.
     * Column matching is case-insensitive and underscore-insensitive.
     */
    private Map<String, List<CiqRow>> filterSheetByColumn(InMemoryCiqDataStore store,
                                                           String sheetName,
                                                           String column,
                                                           String value) {
        Map<String, List<CiqRow>> out = new LinkedHashMap<>();
        CiqSheet sheet = resolveSheet(store, sheetName);
        if (sheet == null) return out;
        String actualCol = findActualColumnName(sheet.getColumns(), column);
        List<CiqRow> rows = new ArrayList<>();
        for (CiqRow row : sheet.getRows()) {
            if (value.equals(row.get(actualCol))) rows.add(row);
        }
        out.put(sheetName, rows);
        return out;
    }

    /**
     * Returns distinct non-blank values of {@code column} in {@code sheetName},
     * preserving encounter order.
     */
    private List<String> distinctValues(InMemoryCiqDataStore store,
                                        String sheetName, String column) {
        CiqSheet sheet = resolveSheet(store, sheetName);
        if (sheet == null) {
            log.warn("segregate_by: sheet '{}' not found", sheetName);
            return Collections.emptyList();
        }
        String actualCol = findActualColumnName(sheet.getColumns(), column);
        List<String> values = new ArrayList<>();
        for (CiqRow row : sheet.getRows()) {
            String v = row.get(actualCol);
            if (v != null && !v.trim().isEmpty() && !values.contains(v.trim()))
                values.add(v.trim());
        }
        return values;
    }

    /**
     * Resolves a sheet from the store by name.
     * {@code "Index"} (case-insensitive) maps to the raw Index sheet when present;
     * all other names use {@link InMemoryCiqDataStore#getSheet(String)}.
     */
    private CiqSheet resolveSheet(InMemoryCiqDataStore store, String sheetName) {
        if ("Index".equalsIgnoreCase(sheetName) && store.getRawIndexSheet() != null)
            return store.getRawIndexSheet();
        return store.getSheet(sheetName);
    }

    /**
     * Produces a minimal generic JSON for the no-template case.
     * Contains all workbook sheets with their full row data, plus top-level metadata.
     */
    private Map<String, Object> buildDefaultJson(InMemoryCiqDataStore store,
                                                  String nodeType, String activity,
                                                  String segColumn, String segValue) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodeType", nodeType);
        out.put("activity", activity);
        out.put(segColumn, segValue);
        Map<String, Object> sheets = new LinkedHashMap<>();
        for (String name : store.getAvailableSheets()) {
            CiqSheet s = store.getSheet(name);
            if (s == null) continue;
            List<Map<String, String>> rows = new ArrayList<>();
            for (CiqRow row : s.getRows()) rows.add(row.getData());
            sheets.put(name, rows);
        }
        out.put("sheets", sheets);
        return out;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /**
     * Finds the actual column name in {@code columns} that matches {@code canonical}
     * using case-insensitive, underscore/space-insensitive comparison.
     * Falls back to {@code canonical} when no match is found.
     */
    private static String findActualColumnName(List<String> columns, String canonical) {
        if (columns == null) return canonical;
        String target = canonical.replaceAll("[_\\s]", "").toLowerCase();
        for (String col : columns) {
            if (col.replaceAll("[_\\s]", "").toLowerCase().equals(target)) return col;
        }
        return canonical;
    }

    /** Replaces characters that are unsafe in file names with underscores. */
    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._\\-]", "_");
    }

    // =========================================================================
    // Post-processing — fill null emails within the same crGroup
    // =========================================================================

    /**
     * For any node whose email is null, borrows the email from another node in
     * the same crGroup that does have a value.  Operates in-place on the JSON
     * object produced by the template evaluator before it is written to disk.
     */
    @SuppressWarnings("unchecked")
    private void fillNullEmailsByGroup(Object json) {
        if (!(json instanceof Map)) return;
        Map<String, Object> root = (Map<String, Object>) json;
        Object nodesObj = root.get("nodes");
        if (!(nodesObj instanceof List)) return;
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;

        // First pass: collect the first non-null email per crGroup
        Map<String, String> groupEmail = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            Object crGroup = node.get("crGroup");
            Object email   = node.get("email");
            if (crGroup != null && email != null && !email.toString().trim().isEmpty()) {
                groupEmail.putIfAbsent(crGroup.toString(), email.toString());
            }
        }

        // Second pass: fill null emails from the collected map
        for (Map<String, Object> node : nodes) {
            Object crGroup = node.get("crGroup");
            Object email   = node.get("email");
            if (email == null && crGroup != null) {
                String resolved = groupEmail.get(crGroup.toString());
                if (resolved != null) {
                    node.put("email", resolved);
                    log.debug("Filled null email for node '{}' (crGroup='{}') → {}",
                            node.get("node"), crGroup, resolved);
                }
            }
        }
    }

    // =========================================================================
    // SegregateByConfig — parsed 'segregate_by' YAML block
    // =========================================================================

    private static final class SegregateByConfig {
        final String sheet;
        final String column;
        final String varName;   // the variable name without the leading '$'

        SegregateByConfig(String sheet, String column, String varName) {
            this.sheet   = sheet;
            this.column  = column;
            this.varName = varName;
        }

        @Override
        public String toString() {
            return sheet + "." + column + " as $" + varName;
        }
    }
}
